// Real JNI surface for ticket 025, replacing ticket 016's stub. Bridges MoshBridge.kt to upstream
// mosh's Network::Transport<UserStream, Terminal::Complete> - the same client-side instantiation
// stmclient.cc drives (ticket 003's asset) - built against rjyo/mosh-android's prebuilt NDK libs,
// fetched by CMakeLists.txt at configure time rather than vendored (see that file's header
// comment for why).
//
// Design, mirroring stmclient.cc's shape but as a reusable buffer-in/buffer-out session instead of
// a CLI program that owns a real tty:
//   - MoshClientSession owns one Network::Transport<UserStream, Complete> for the session's
//     lifetime. UserStream is our sent state (keystrokes/resizes); Complete is the remote state
//     mosh reconstructs locally from the server's diffs.
//   - Complete::diff_from (compiled into libmoshterminal.a) already produces real terminal escape
//     sequences, not a custom wire format - Transport::get_remote_diff() hands us exactly the
//     bytes TerminalSession.feedIncoming() wants, identical in shape to what
//     HerdrDataChannelSession's ServerMessage.Terminal branch does for the SSH path.
//   - Roaming is NOT implemented here: it lives entirely inside Network::Connection::recv_one
//     (already compiled into libmoshnetwork.a), which re-learns the peer's source address/port on
//     any validly-authenticated packet. This wrapper's only roaming-relevant job is to keep the
//     background loop thread alive across an Android connectivity change instead of tearing the
//     session down - which the Kotlin side already does by simply not calling close() on a network
//     callback (see MoshSession.kt's doc comment).
//
// REINS_MOSH_REAL_LIBS is 0 when CMakeLists.txt couldn't fetch the prebuilt libs in this build
// environment (offline, GitHub unreachable) - see that file's header comment. In that mode this
// file compiles a loopback stand-in behind the exact same JNI surface, so :mosh and everything
// that calls into it (MoshBridge.kt, MoshSession.kt, :data's MoshDataChannelSession) still build
// and can be exercised in-process, but no real mosh session, no real UDP socket, and no real
// roaming ever happens - the loopback path with the exact string is grepped for below. Ticket
// 025's resolution documents which mode a given build actually used.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#define LOG_TAG "reins-mosh"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if REINS_MOSH_REAL_LIBS

#include <sys/select.h>
#include <sys/time.h>

// The -impl.h headers (not just networktransport.h/transportsender.h) are needed here: rjyo's
// prebuilt libmoshnetwork.a only explicitly instantiates Transport<Complete, UserStream> (mosh's
// own server direction) inside its networktransport.cc, not the Transport<UserStream, Complete>
// client direction this file needs (confirmed by an undefined-symbol link failure without this -
// mosh-client.cc must carry its own explicit instantiation, and rjyo's `--disable-client` build
// never compiles that file). Including the method-body headers here makes our own use of
// MoshTransport trigger implicit template instantiation instead, compiled straight into
// mosh_jni.cpp.o, linking against the already-compiled non-template helpers (Connection,
// TransportSender's shared internals) that libmoshnetwork.a does carry either way.
#include "networktransport.h"
#include "networktransport-impl.h"
#include "completeterminal.h"
#include "user.h"

using MoshTransport = Network::Transport<Network::UserStream, Terminal::Complete>;

// A real Network::Transport<UserStream, Complete> session, driven from a background thread.
// Not a CLI driver like STMClient - no tty, no ncurses; input/output are plain byte buffers so the
// Kotlin side can wire them into TerminalSession exactly like HerdrDataChannelSession does.
class MoshClientSession {
public:
    MoshClientSession(std::string key, std::string host, std::string port, int cols, int rows)
        : terminal_(static_cast<size_t>(cols), static_cast<size_t>(rows)),
          userStream_(),
          key_(std::move(key)),
          host_(std::move(host)),
          port_(std::move(port)) {
        transport_ = std::make_unique<MoshTransport>(userStream_, terminal_, key_.c_str(), host_.c_str(), port_.c_str());
    }

    void sendInput(const uint8_t *data, size_t len) {
        std::lock_guard<std::mutex> lock(mutex_);
        for (size_t i = 0; i < len; i++) {
            transport_->get_current_state().push_back(Parser::UserByte(static_cast<int>(data[i])));
        }
    }

    void resize(int cols, int rows) {
        std::lock_guard<std::mutex> lock(mutex_);
        transport_->get_current_state().push_back(Parser::Resize(static_cast<size_t>(cols), static_cast<size_t>(rows)));
    }

    // Blocking loop: waits for network activity or the sender's own retransmit/heartbeat timer,
    // ticks the sender, and hands any new remote-diff bytes to onOutput. Runs until stop() is
    // called or the transport reports the far end shut down.
    void runLoop(const std::function<void(const std::string &)> &onOutput,
                 const std::function<void()> &onShutdown,
                 const std::function<void(const std::string &)> &onError) {
        try {
            while (running_.load()) {
                {
                    std::lock_guard<std::mutex> lock(mutex_);
                    transport_->tick();
                }

                int waitTimeMs = transport_->wait_time();
                if (waitTimeMs < 0) waitTimeMs = 0;
                if (waitTimeMs > 1000) waitTimeMs = 1000; // re-check running_ periodically regardless

                fd_set readable;
                FD_ZERO(&readable);
                int maxFd = -1;
                for (int fd: transport_->fds()) {
                    FD_SET(fd, &readable);
                    if (fd > maxFd) maxFd = fd;
                }

                struct timeval tv{};
                tv.tv_sec = waitTimeMs / 1000;
                tv.tv_usec = (waitTimeMs % 1000) * 1000;

                int selected = maxFd >= 0 ? select(maxFd + 1, &readable, nullptr, nullptr, &tv) : 0;
                if (selected > 0) {
                    std::lock_guard<std::mutex> lock(mutex_);
                    transport_->recv();
                }

                std::lock_guard<std::mutex> lock(mutex_);
                std::string diff = transport_->get_remote_diff();
                if (!diff.empty()) {
                    onOutput(diff);
                }
                if (transport_->shutdown_acknowledged() || transport_->counterparty_shutdown_ack_sent()) {
                    onShutdown();
                    return;
                }
            }
        } catch (const std::exception &e) {
            onError(e.what());
        } catch (...) {
            onError("unknown native Mosh transport error");
        }
    }

    void requestShutdown() {
        std::lock_guard<std::mutex> lock(mutex_);
        transport_->start_shutdown();
    }

    void stop() { running_.store(false); }

private:
    Terminal::Complete terminal_;
    Network::UserStream userStream_;
    std::string key_;
    std::string host_;
    std::string port_;
    std::unique_ptr<MoshTransport> transport_;
    std::mutex mutex_;
    std::atomic<bool> running_{true};
};

#else // !REINS_MOSH_REAL_LIBS - simulated loopback, see this file's header comment.

#include <chrono>
#include <condition_variable>
#include <deque>

class MoshClientSession {
public:
    MoshClientSession(std::string key, std::string host, std::string port, int cols, int rows) {
        (void) key;
        (void) host;
        (void) port;
        (void) cols;
        (void) rows;
        LOGI("REINS_MOSH_SIMULATED: no real mosh libs at build time - this session is a loopback stand-in, not a real Mosh connection");
    }

    void sendInput(const uint8_t *data, size_t len) {
        std::lock_guard<std::mutex> lock(mutex_);
        pending_.append(reinterpret_cast<const char *>(data), len);
        cv_.notify_all();
    }

    void resize(int, int) { /* no-op in loopback mode */ }

    void runLoop(const std::function<void(const std::string &)> &onOutput,
                 const std::function<void()> &onShutdown,
                 const std::function<void(const std::string &)> &) {
        while (running_.load()) {
            std::unique_lock<std::mutex> lock(mutex_);
            cv_.wait_for(lock, std::chrono::milliseconds(200), [this] { return !pending_.empty() || !running_.load(); });
            if (!running_.load()) break;
            if (!pending_.empty()) {
                std::string echoed = pending_;
                pending_.clear();
                lock.unlock();
                onOutput(echoed); // simulated "server": echo keystrokes straight back
            }
        }
        onShutdown();
    }

    void requestShutdown() { running_.store(false); }
    void stop() { running_.store(false); }

private:
    std::mutex mutex_;
    std::condition_variable cv_;
    std::string pending_;
    std::atomic<bool> running_{true};
};

#endif

namespace {

JavaVM *g_jvm = nullptr;

struct SessionHandle {
    std::unique_ptr<MoshClientSession> session;
    std::unique_ptr<std::thread> loopThread;
    jobject callbacks = nullptr; // global ref
};

jclass g_callbacksClass = nullptr;
jmethodID g_onOutputMethod = nullptr;
jmethodID g_onShutdownMethod = nullptr;
jmethodID g_onErrorMethod = nullptr;

JNIEnv *attachCurrentThread(bool *didAttach) {
    JNIEnv *env = nullptr;
    if (g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
        *didAttach = false;
        return env;
    }
    *didAttach = (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK);
    return env;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jstring JNICALL
Java_co_maxasif_reins_mosh_MoshBridge_nativeVersion(JNIEnv *env, jobject /* this */) {
#if REINS_MOSH_REAL_LIBS
    return env->NewStringUTF("reins-mosh-0.1.0 (real: rjyo/mosh-android prebuilt libs, ticket 025)");
#else
    return env->NewStringUTF("reins-mosh-0.1.0 (REINS_MOSH_SIMULATED: prebuilt libs unavailable at build time)");
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_co_maxasif_reins_mosh_MoshBridge_nativeIsSimulated(JNIEnv *, jobject) {
#if REINS_MOSH_REAL_LIBS
    return JNI_FALSE;
#else
    return JNI_TRUE;
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_co_maxasif_reins_mosh_MoshBridge_nativeCreate(
        JNIEnv *env, jobject /* this */, jstring jKey, jstring jHost, jstring jPort, jint cols, jint rows) {
    const char *keyChars = env->GetStringUTFChars(jKey, nullptr);
    const char *hostChars = env->GetStringUTFChars(jHost, nullptr);
    const char *portChars = env->GetStringUTFChars(jPort, nullptr);

    auto *handle = new SessionHandle();
    try {
        handle->session = std::make_unique<MoshClientSession>(
                std::string(keyChars), std::string(hostChars), std::string(portChars), cols, rows);
    } catch (const std::exception &e) {
        LOGE("nativeCreate failed: %s", e.what());
        delete handle;
        handle = nullptr;
    }

    env->ReleaseStringUTFChars(jKey, keyChars);
    env->ReleaseStringUTFChars(jHost, hostChars);
    env->ReleaseStringUTFChars(jPort, portChars);

    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_co_maxasif_reins_mosh_MoshBridge_nativeStartLoop(JNIEnv *env, jobject /* this */, jlong handlePtr, jobject callbacks) {
    auto *handle = reinterpret_cast<SessionHandle *>(handlePtr);
    if (handle == nullptr) return;

    if (g_callbacksClass == nullptr) {
        jclass local = env->GetObjectClass(callbacks);
        g_callbacksClass = static_cast<jclass>(env->NewGlobalRef(local));
        g_onOutputMethod = env->GetMethodID(g_callbacksClass, "onOutput", "([B)V");
        g_onShutdownMethod = env->GetMethodID(g_callbacksClass, "onShutdown", "()V");
        g_onErrorMethod = env->GetMethodID(g_callbacksClass, "onError", "(Ljava/lang/String;)V");
    }

    handle->callbacks = env->NewGlobalRef(callbacks);
    MoshClientSession *session = handle->session.get();

    handle->loopThread = std::make_unique<std::thread>([session, handle] {
        bool didAttach = false;
        JNIEnv *threadEnv = attachCurrentThread(&didAttach);

        auto onOutput = [threadEnv, handle](const std::string &bytes) {
            jbyteArray array = threadEnv->NewByteArray(static_cast<jsize>(bytes.size()));
            threadEnv->SetByteArrayRegion(array, 0, static_cast<jsize>(bytes.size()),
                                           reinterpret_cast<const jbyte *>(bytes.data()));
            threadEnv->CallVoidMethod(handle->callbacks, g_onOutputMethod, array);
            threadEnv->DeleteLocalRef(array);
        };
        auto onShutdown = [threadEnv, handle] {
            threadEnv->CallVoidMethod(handle->callbacks, g_onShutdownMethod);
        };
        auto onError = [threadEnv, handle](const std::string &message) {
            jstring jMessage = threadEnv->NewStringUTF(message.c_str());
            threadEnv->CallVoidMethod(handle->callbacks, g_onErrorMethod, jMessage);
            threadEnv->DeleteLocalRef(jMessage);
        };

        session->runLoop(onOutput, onShutdown, onError);

        if (didAttach) g_jvm->DetachCurrentThread();
    });
}

extern "C" JNIEXPORT void JNICALL
Java_co_maxasif_reins_mosh_MoshBridge_nativeSendInput(JNIEnv *env, jobject /* this */, jlong handlePtr, jbyteArray data) {
    auto *handle = reinterpret_cast<SessionHandle *>(handlePtr);
    if (handle == nullptr || handle->session == nullptr) return;

    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    handle->session->sendInput(reinterpret_cast<const uint8_t *>(bytes), static_cast<size_t>(len));
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_co_maxasif_reins_mosh_MoshBridge_nativeResize(JNIEnv *, jobject /* this */, jlong handlePtr, jint cols, jint rows) {
    auto *handle = reinterpret_cast<SessionHandle *>(handlePtr);
    if (handle == nullptr || handle->session == nullptr) return;
    handle->session->resize(cols, rows);
}

extern "C" JNIEXPORT void JNICALL
Java_co_maxasif_reins_mosh_MoshBridge_nativeShutdown(JNIEnv *, jobject /* this */, jlong handlePtr) {
    auto *handle = reinterpret_cast<SessionHandle *>(handlePtr);
    if (handle == nullptr || handle->session == nullptr) return;
    handle->session->requestShutdown();
}

extern "C" JNIEXPORT void JNICALL
Java_co_maxasif_reins_mosh_MoshBridge_nativeDestroy(JNIEnv *env, jobject /* this */, jlong handlePtr) {
    auto *handle = reinterpret_cast<SessionHandle *>(handlePtr);
    if (handle == nullptr) return;

    if (handle->session) handle->session->stop();
    if (handle->loopThread && handle->loopThread->joinable()) handle->loopThread->join();
    if (handle->callbacks != nullptr) env->DeleteGlobalRef(handle->callbacks);
    delete handle;
}

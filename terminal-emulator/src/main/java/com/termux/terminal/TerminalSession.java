package com.termux.terminal;

import androidx.annotation.NonNull;

import android.os.Handler;
import android.os.Message;

import java.util.UUID;

/**
 * VENDORED AND MODIFIED from termux/termux-app (Apache-2.0). See VENDORED.md at the repo root
 * for the upstream commit and a summary of changes.
 * <p>
 * Original {@code TerminalSession} couples the terminal emulator to a local subprocess over a
 * JNI-backed pty (see upstream's {@code initializeEmulator}/{@code JNI.createSubprocess}). Reins
 * never spawns a local process — its Data Channel is a remote herdr Agent Session's PTY, reached
 * over SSH (ticket 017) — so this version drops the JNI/subprocess machinery entirely and instead
 * exposes {@link #feedIncoming(byte[], int)} (remote bytes in) and a {@link RemoteWriteCallback}
 * (keystrokes out), both wired up by {@code co.maxasif.reins.data} to the herdr wire-protocol
 * channel. The public surface {@link com.termux.view.TerminalView} depends on (write,
 * writeCodePoint, updateSize, getEmulator, getTitle, reset, finishIfRunning, isRunning,
 * getExitStatus, the TerminalOutput callbacks) is preserved so TerminalView needs no changes.
 * The class is no longer {@code final} for the same reason ByteQueue-based threading was dropped:
 * there is now nothing process-lifecycle-specific left to protect against subclassing, and it
 * keeps the door open for a future fake/test session.
 */
public class TerminalSession extends TerminalOutput {

    private static final int MSG_NEW_INPUT = 1;

    /** Invoked with keystrokes/data the user (or Command Dial) sends, to forward over the Data Channel. */
    public interface RemoteWriteCallback {
        void onWrite(byte[] data, int offset, int count);
    }

    public final String mHandle = UUID.randomUUID().toString();

    TerminalEmulator mEmulator;

    /** Callback which gets notified when a session finishes or changes title. */
    TerminalSessionClient mClient;

    private final Integer mTranscriptRows;

    private volatile RemoteWriteCallback mRemoteWriteCallback;
    private volatile boolean mFinished = false;

    /** Buffer to translate code points into UTF-8 before writing to the remote write callback. */
    private final byte[] mUtf8InputBuffer = new byte[5];

    /** Byte queue + Handler used only to marshal remote-received bytes onto the main thread. */
    private final ByteQueue mIncomingQueue = new ByteQueue(64 * 1024);
    private final Handler mMainThreadHandler = new IncomingHandler();

    public String mSessionName;

    public TerminalSession(TerminalSessionClient client, Integer transcriptRows) {
        this.mTranscriptRows = transcriptRows;
        this.mClient = client;
    }

    public void setRemoteWriteCallback(RemoteWriteCallback callback) {
        mRemoteWriteCallback = callback;
    }

    public void updateTerminalSessionClient(TerminalSessionClient client) {
        mClient = client;
        if (mEmulator != null) mEmulator.updateTerminalSessionClient(client);
    }

    /** Create the emulator on first size report, or reflow it on subsequent resizes. No pty involved. */
    public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mEmulator == null) {
            mEmulator = new TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient);
            mClient.setTerminalShellPid(this, 0);
        } else {
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
        }
    }

    public String getTitle() {
        return (mEmulator == null) ? null : mEmulator.getTitle();
    }

    /** Feed bytes received from the remote Data Channel (a herdr {@code Terminal} frame) into the emulator. */
    public void feedIncoming(byte[] data, int len) {
        if (mEmulator == null || len <= 0) return;
        if (!mIncomingQueue.write(data, 0, len)) return;
        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
    }

    /** Forward bytes to the remote Data Channel instead of a local pty. */
    @Override
    public void write(byte[] data, int offset, int count) {
        RemoteWriteCallback callback = mRemoteWriteCallback;
        if (callback != null) callback.onWrite(data, offset, count);
    }

    public void writeCodePoint(boolean prependEscape, int codePoint) {
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            throw new IllegalArgumentException("Invalid code point: " + codePoint);
        }

        int bufferPosition = 0;
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27;

        if (codePoint <= 0b1111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= 0b11111111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= 0b1111111111111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        }
        write(mUtf8InputBuffer, 0, bufferPosition);
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    protected void notifyScreenUpdate() {
        mClient.onTextChanged(this);
    }

    public void reset() {
        mEmulator.reset();
        notifyScreenUpdate();
    }

    /** Tear down this session. The owner of the Data Channel connection closes the actual transport. */
    public void finishIfRunning() {
        if (mFinished) return;
        mFinished = true;
        mIncomingQueue.close();
        mClient.onSessionFinished(this);
    }

    public boolean isRunning() {
        return !mFinished;
    }

    /** Always 0 - there is no local process; a non-zero value here would mislead the exit UI. */
    public int getExitStatus() {
        return 0;
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {
        mClient.onTitleChanged(this);
    }

    @Override
    public void onCopyTextToClipboard(String text) {
        mClient.onCopyTextToClipboard(this, text);
    }

    @Override
    public void onPasteTextFromClipboard() {
        mClient.onPasteTextFromClipboard(this);
    }

    @Override
    public void onBell() {
        mClient.onBell(this);
    }

    @Override
    public void onColorsChanged() {
        mClient.onColorsChanged(this);
    }

    private class IncomingHandler extends Handler {
        private final byte[] mReceiveBuffer = new byte[64 * 1024];

        @Override
        public void handleMessage(@NonNull Message msg) {
            int bytesRead = mIncomingQueue.read(mReceiveBuffer, false);
            if (bytesRead > 0) {
                mEmulator.append(mReceiveBuffer, bytesRead);
                notifyScreenUpdate();
            }
        }
    }
}

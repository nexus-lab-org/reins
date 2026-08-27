cmake_minimum_required(VERSION 3.22.1)
project("reins-mosh")

# Real transport bridging to upstream mosh's Network::Transport classes (ticket 025), built on
# rjyo/mosh-android's prebuilt NDK static libs (ticket 003's recommendation). Those libs are a
# ~150 MB-per-ABI tree (mosh's own libs plus OpenSSL/protobuf/abseil, which a current protobuf-lite
# build pulls in transitively) - too large to vendor into git, so this file fetches the pinned
# GitHub release archive at CMake-configure time into a repo-local, gitignored cache
# (mosh/.moshlibs-cache/), verifies it by SHA-256, and normalizes its headers (see below) rather
# than committing the binary. Re-run is a no-op once cached; delete the cache dir to force a
# re-fetch. Set REINS_MOSH_LIBS_DIR to an already-prepared `android-libs/` directory (matching this
# same layout) to skip the network fetch entirely, e.g. for offline/CI builds.
#
# If the fetch fails (offline environment, GitHub unreachable), the build falls back to
# REINS_MOSH_SIMULATED mode: mosh_jni.cpp compiles a loopback stand-in behind the same JNI surface
# so :mosh and its Kotlin callers still build, but no real mosh session is ever created. This is
# the "simulated vs real" fallback the module's owning ticket (025) calls for when real vendoring
# is impractical in a given build environment - see mosh_jni.cpp's own header comment and
# CONTEXT.md/the ticket 025 resolution for what that implies.

set(MOSH_RELEASE_URL "https://github.com/rjyo/mosh-android/releases/download/v1.0.0/mosh-android-libs-v1.0.0.tar.gz")
set(MOSH_RELEASE_SHA256 "8a6c88d9d7646d796db0a7f58571564d59b8dcdc7836b0dbf679318a23141005")
set(MOSH_CACHE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.moshlibs-cache/v1.0.0")

set(REINS_MOSH_REAL_LIBS OFF)

if(DEFINED ENV{REINS_MOSH_LIBS_DIR} AND NOT "$ENV{REINS_MOSH_LIBS_DIR}" STREQUAL "")
    set(MOSH_LIBS_DIR "$ENV{REINS_MOSH_LIBS_DIR}")
    if(EXISTS "${MOSH_LIBS_DIR}/include/networktransport.h")
        set(REINS_MOSH_REAL_LIBS ON)
        message(STATUS "reins-mosh: using REINS_MOSH_LIBS_DIR override at ${MOSH_LIBS_DIR}")
    else()
        message(WARNING "reins-mosh: REINS_MOSH_LIBS_DIR=${MOSH_LIBS_DIR} doesn't look like a prepared android-libs/ dir - ignoring it")
    endif()
endif()

if(NOT REINS_MOSH_REAL_LIBS)
    set(MOSH_LIBS_DIR "${MOSH_CACHE_ROOT}/extracted/android-libs")
    set(MOSH_STAMP "${MOSH_LIBS_DIR}/.prepared")

    if(EXISTS "${MOSH_STAMP}")
        set(REINS_MOSH_REAL_LIBS ON)
    else()
        set(_archive "${MOSH_CACHE_ROOT}/mosh-android-libs-v1.0.0.tar.gz")
        message(STATUS "reins-mosh: fetching prebuilt mosh libs (ticket 003) to ${_archive}")
        file(DOWNLOAD "${MOSH_RELEASE_URL}" "${_archive}"
                EXPECTED_HASH SHA256=${MOSH_RELEASE_SHA256}
                STATUS _dl_status
                TLS_VERIFY ON)
        list(GET _dl_status 0 _dl_code)

        if(_dl_code EQUAL 0)
            file(ARCHIVE_EXTRACT INPUT "${_archive}" DESTINATION "${MOSH_CACHE_ROOT}/extracted")

            # The release tarball was packed on macOS and carries AppleDouble resource-fork
            # sidecar files (`._libfoo.a` next to `libfoo.a`, PAX xattr headers) - not real
            # archives, and fatal to the linker if globbed in alongside the real ones below.
            file(GLOB_RECURSE _apple_double_files "${MOSH_CACHE_ROOT}/extracted/*/._*")
            if(_apple_double_files)
                file(REMOVE ${_apple_double_files})
            endif()

            # rjyo/mosh-android's release ships every header flattened into one include/ dir, but
            # the headers themselves #include each other via mosh's own upstream source layout
            # ("src/network/network.h", "src/crypto/crypto.h", etc) - neither candidate repo ships
            # a usable wrapper (ticket 003's finding), and this flattening is exactly why: taken
            # as-is, none of these headers compile. Mirror the flat files into the paths they
            # actually #include, closing the exact set of quoted includes this pinned release's
            # header tree uses (verified by grepping every `#include "src/...` across the release).
            set(_inc "${MOSH_LIBS_DIR}/include")
            set(_mirror_pairs
                    "crypto/ae.h=ae.h"
                    "crypto/crypto.h=crypto.h"
                    "crypto/prng.h=prng.h"
                    "include/config.h=config.h"
                    "network/network.h=network.h"
                    "network/networktransport.h=networktransport.h"
                    "network/transportfragment.h=transportfragment.h"
                    "network/transportsender.h=transportsender.h"
                    "protobufs/transportinstruction.pb.h=transportinstruction.pb.h"
                    "terminal/parseraction.h=parseraction.h"
                    "terminal/parser.h=parser.h"
                    "terminal/terminal.h=terminal.h"
                    "terminal/terminalframebuffer.h=terminalframebuffer.h"
                    "util/fatal_assert.h=fatal_assert.h"
                    "util/timestamp.h=timestamp.h"
            )
            foreach(_pair IN LISTS _mirror_pairs)
                string(REPLACE "=" ";" _pair_list "${_pair}")
                list(GET _pair_list 0 _dest_rel)
                list(GET _pair_list 1 _src_name)
                get_filename_component(_dest_dir "${_inc}/src/${_dest_rel}" DIRECTORY)
                file(MAKE_DIRECTORY "${_dest_dir}")
                file(COPY_FILE "${_inc}/${_src_name}" "${_inc}/src/${_dest_rel}" ONLY_IF_DIFFERENT)
            endforeach()

            file(WRITE "${MOSH_STAMP}" "prepared from ${MOSH_RELEASE_URL}\n")
            set(REINS_MOSH_REAL_LIBS ON)
        else()
            list(GET _dl_status 1 _dl_message)
            message(WARNING "reins-mosh: could not fetch prebuilt mosh libs (${_dl_message}) - building in REINS_MOSH_SIMULATED mode")
        endif()
    endif()
endif()

add_library(reins-mosh SHARED mosh_jni.cpp)
find_library(log-lib log)
find_library(z-lib z)

if(REINS_MOSH_REAL_LIBS)
    message(STATUS "reins-mosh: linking real mosh libs from ${MOSH_LIBS_DIR}")
    target_compile_definitions(reins-mosh PRIVATE REINS_MOSH_REAL_LIBS=1)
    target_include_directories(reins-mosh PRIVATE "${MOSH_LIBS_DIR}/include")

    set(MOSH_ABI_LIBS_DIR "${MOSH_LIBS_DIR}/static/${ANDROID_ABI}")
    file(GLOB MOSH_STATIC_LIBS "${MOSH_ABI_LIBS_DIR}/*.a")
    # form/menu/panel are ncurses' higher-level widget libs (STMClient's CLI-only "connecting..."
    # overlay, ticket 003's asset) - unused by this JNI wrapper. The plain (non-wide) libncurses.a
    # is excluded too: libmoshterminal.a's Terminal::Display queries terminfo capabilities
    # (setupterm/tigetflag/tigetstr) unconditionally at construction against the wide-char
    # (ncursesw) ABI mosh's own configure prefers - linking both variants together pulls in
    # internal ncurses symbols (_nc_fallback2) that only exist in one or the other, not both.
    list(FILTER MOSH_STATIC_LIBS EXCLUDE REGEX "lib(formw|menuw|panelw|ncurses)\\.a$")
    if(NOT MOSH_STATIC_LIBS)
        message(FATAL_ERROR "reins-mosh: no prebuilt .a libs found under ${MOSH_ABI_LIBS_DIR}")
    endif()

    # Static libs, linked as one --start-group so the mosh/protobuf/abseil circular references
    # resolve regardless of listing order (their exact inter-dependency graph isn't documented by
    # rjyo/mosh-android and isn't worth hand-curating - see this file's header comment).
    #
    # -z notext: rjyo/mosh-android's own mosh/crypto/terminal .a objects (unlike its absl/protobuf/
    # OpenSSL ones) weren't compiled with -fPIC, which ld.lld otherwise refuses outright when
    # linking a static lib's non-PIC code into a SHARED object (every Android .so must be
    # position-independent, API 16+). This is the standard, documented NDK escape hatch for
    # exactly that situation - it permits the resulting text relocations instead of erroring - at
    # the cost of slightly weaker ASLR for the affected code pages and a small one-time relocation
    # cost at load. A real limitation of this specific prebuilt release, not a Reins shortcut: the
    # only real fix is rjyo/mosh-android rebuilding with -fPIC, or Reins cross-compiling mosh from
    # source itself (which ticket 003 deliberately chose not to do - see that ticket's asset).
    target_link_libraries(reins-mosh
            "-Wl,--start-group"
            ${MOSH_STATIC_LIBS}
            "-Wl,--end-group"
            "-Wl,-z,notext"
            # -Bsymbolic: -z notext alone isn't enough for AArch64's PC-relative ADRP-page
            # relocations (the class covering vtables/typeinfo of mosh's exception/dispatcher
            # classes) - lld refuses those outright against a *preemptible* symbol, since no
            # dynamic relocation record can represent a page-relative offset computed against an
            # address only known at load time. -Bsymbolic resolves every symbol from this .so
            # against its own definition at link time (non-preemptible), which is exactly what a
            # single self-contained JNI library wants anyway - nothing else in the process is
            # meant to override reins-mosh's mosh/protobuf/abseil symbols.
            "-Wl,-Bsymbolic"
            ${log-lib}
            ${z-lib}
    )
else()
    target_compile_definitions(reins-mosh PRIVATE REINS_MOSH_REAL_LIBS=0)
    target_link_libraries(reins-mosh ${log-lib})
endif()

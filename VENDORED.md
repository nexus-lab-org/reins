# Vendored third-party source

## `terminal-emulator/`, `terminal-view/`

Source: [`termux/termux-app`](https://github.com/termux/termux-app), commit
`3b66f8799635a4dba4a206563048ff0e6792c487` (2026-08-24), directories
`terminal-emulator/` and `terminal-view/`.

License: Apache License 2.0 (see `.context/assets/002-termux-terminal-view-integration.md`
for the license research this vendoring decision was based on — the root `termux-app`
repo is GPLv3, but these two modules are an explicit Apache-2.0 exception, and have no
compile dependency on the GPLv3 `app` module).

Reproduced here (Apache-2.0 §4 attribution requirement):

```
Copyright (C) The Termux team and contributors, and jackpal/Android-Terminal-Emulator
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

### Changes made (Apache-2.0 §4(b))

- `terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java` was
  substantially rewritten. Upstream couples the terminal emulator to a locally
  spawned subprocess over a JNI pty (`JNI.createSubprocess`/`JNI.waitFor`/`JNI.close`,
  three background threads, two `ByteQueue`s). Reins never spawns a local process —
  every PTY it renders belongs to a remote herdr Agent Session, reached over SSH
  (ticket 017) — so the vendored copy drops the JNI/subprocess machinery entirely and
  instead exposes `feedIncoming(byte[], int)` (remote bytes in, called by
  `co.maxasif.reins.data`'s herdr wire-protocol reader) and a `RemoteWriteCallback`
  (keystrokes out, forwarded as `ClientMessage::Input` frames). The class is no longer
  `final`. All other public methods `TerminalView` depends on (`write`,
  `writeCodePoint`, `updateSize`, `getEmulator`, `getTitle`, `reset`,
  `finishIfRunning`, `isRunning`, `getExitStatus`, the `TerminalOutput` callbacks)
  keep their original signatures, so `terminal-view/` needed no changes at all.
- `JNI.java` and `terminal-emulator/src/main/jni/` (the native pty-spawning code) were
  **not** vendored — nothing in the remote-only path calls into them, and dropping them
  removes the module's NDK/CMake dependency entirely.
- Everything else (`TerminalBuffer`, `TerminalRow`, `TerminalEmulator`, `TextStyle`,
  `TerminalColors`, `TerminalColorScheme`, `KeyHandler`, `WcWidth`, `Logger`,
  `ByteQueue`, `TerminalOutput`, `TerminalSessionClient`, and all of `terminal-view/`)
  is unmodified apart from being placed under this repo's Gradle module layout
  (`build.gradle.kts` instead of upstream's `build.gradle`, no `maven-publish`/JitPack
  plumbing, no native `externalNativeBuild` block).

No `NOTICE` file exists upstream to propagate (verified directly — see the ticket 002
asset). No other Termux/Android-Terminal-Emulator files are vendored.

## `whisper/src/main/cpp/whisper-cpp/`

Source: [`ggml-org/whisper.cpp`](https://github.com/ggml-org/whisper.cpp), commit
`978113305b2ead22249b881deafa131dc8884911` (2026-08-25), directories `ggml/`, `src/`
(`whisper.cpp`, `whisper-arch.h`, `coreml/`, `openvino/`, `vitisai/`), and `include/`
(`whisper.h`). License: MIT (see `whisper/src/main/cpp/whisper-cpp/LICENSE`, copied
verbatim from the upstream repo root).

Trimmed from the upstream checkout before vendoring: `ggml/.git`, `ggml/tests`,
`ggml/examples`, `ggml/ci`, `ggml/.github`, and `ggml/kompute` (a git submodule
directory with no in-tree source and no reference from `ggml/CMakeLists.txt` once
absent - Reins never enables `GGML_KOMPUTE`). Every other backend under `ggml/src`
(CUDA, Metal, Vulkan, SYCL, etc.) is vendored as-is; `ggml/CMakeLists.txt`'s own
`GGML_*` options default them off, so they add source bulk but not build cost. No
`whisper.cpp` files outside these directories are vendored - notably not
`examples/`, `bindings/`, or the model download scripts.

### Adapted, not modified (ticket 024)

Unlike the terminal-emulator/terminal-view vendoring above, none of the vendored
whisper.cpp/ggml source itself was changed. What Reins wrote from scratch, adapted
from whisper.cpp's official `examples/whisper.android` reference (not vendored, only
read for structure - see the ticket 005 and 024 assets):

- `whisper/src/main/cpp/CMakeLists.txt` - adapted from
  `examples/whisper.android/lib/src/main/jni/whisper/CMakeLists.txt`. Same
  `FetchContent`-onto-vendored-`ggml`, same per-ABI extra target
  (`reins_whisper_v8fp16_va` / `reins_whisper_vfpv4`, renamed from upstream's
  `whisper_v8fp16_va` / `whisper_vfpv4` to avoid colliding with `:mosh`'s own native
  libs in the merged APK), same release-build LTO/section-GC flags. Hardcodes
  `WHISPER_VERSION` instead of regexing it out of a root `CMakeLists.txt` Reins
  doesn't vendor.
- `whisper/src/main/cpp/whisper_jni.cpp` - adapted from
  `examples/whisper.android/lib/src/main/jni/whisper/jni.c` (C, ported to C++ to
  match this module's existing stub file), trimmed to what `WhisperBridge.kt` needs:
  asset-backed model loading, one-shot transcribe, segment readback. Upstream's
  `InputStream`-backed loader and the two `bench*` JNI hooks were dropped - Reins
  only ever loads the one bundled asset model and has no in-app benchmarking UI.
- `whisper/src/main/kotlin/co/maxasif/reins/whisper/WhisperBridge.kt` - adapted from
  `examples/whisper.android`'s `LibWhisper.kt` (the `WhisperContext`/single-thread-
  dispatcher pattern, the CPU-feature-sniffing `System.loadLibrary` dance) and
  `WhisperCpuConfig.kt` (thread-count heuristic, simplified to
  `availableProcessors() - 2` since Reins doesn't need the frequency-binning logic a
  dictation-length transcription would justify - see the ticket 024 asset for why
  short Ad Hoc Commands don't need it).

## `mosh/` prebuilt native libraries (fetched at build time, not vendored into git)

Source: [`rjyo/mosh-android`](https://github.com/rjyo/mosh-android), release `v1.0.0`
(`mosh-android-libs-v1.0.0.tar.gz`), which itself packages upstream
[`mobile-shell/mosh`](https://github.com/mobile-shell/mosh)'s C++ sources compiled as
static libraries for Android, plus the OpenSSL/protobuf/abseil libraries mosh's
protobuf-lite build depends on.

Unlike the two sections above, none of this is committed to the Reins repository.
The archive is roughly 150 MB per ABI, too large to vendor as source or binary, so
`mosh/src/main/cpp/CMakeLists.txt` downloads the pinned release at CMake-configure
time into a gitignored cache (`.moshlibs-cache/`), verifies it against a pinned
SHA-256, and links against it directly.
`REINS_MOSH_LIBS_DIR` can point at an already-prepared directory of the same layout
to skip the network fetch (offline/CI use).
See that file's own header comment for the full mechanics (header mirroring,
AppleDouble stripping, the `-z notext`/`-Bsymbolic` linker flags this specific
prebuilt release requires) and the [ticket 025 resolution](.context/tickets/025-mosh-transport.md)
for what was built on top of it.

License: mosh itself is GPL-3.0 (`mobile-shell/mosh`'s `COPYING`); OpenSSL is
Apache-2.0, protobuf and abseil are BSD-3-Clause. Reins links these as static
libraries into `:mosh`'s own JNI shared object rather than distributing mosh's
source - see [ticket 003's asset](.context/assets/003-mosh-android-integration.md)
for the licensing analysis this approach was based on (Reins is a personal,
sideload-only app, not distributed through a store or to third parties).

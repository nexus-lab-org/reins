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

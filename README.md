# Reins

An Android (later iOS) app for steering remote coding agents from a phone — connecting to
[herdr](https://github.com/) sessions over SSH or Mosh without disturbing the agent's own input
stream. Personal-use, sideload-only, no Play Store distribution.

See [CONTEXT.md](CONTEXT.md) for the domain glossary (Agent Session, Data Channel, Control
Channel, Command Dial, Ad Hoc Command, Host, Identity) — read it before touching any of the
terms below.

## Module structure

```
:app          — Application class, DI wiring, the foreground connection Service, manifest entry points
:presentation — Compose screens (Host List, Connect, Terminal+Dial, Settings), ViewModels
:domain       — Host, Identity, UseCases, repository interfaces — pure Kotlin, zero Android/JNI/sshj deps
:data         — Repository impls: Room, the custom sshj direct-streamlocal channel, Keystore adapter,
                wraps :mosh
:mosh         — JNI wrapper on rjyo/mosh-android's prebuilt NDK libs, own CMake/NDK toolchain
```

Dependency direction: `app → presentation, data` / `presentation → domain` / `data → domain,
mosh` / `domain → nothing`. `domain` never sees Room entities, JNI handles, or sshj types —
only its own repository interfaces, implemented in `data`.

## Requirements

- JDK 17 (a full JDK with `javac`, not a JRE-only install)
- Android SDK: platform 34, build-tools 34.0.0, NDK 26.1.10909125, CMake 3.22.1
- Android Studio is the easiest way to get all of the above — open the project root and let it
  provision the SDK/NDK/CMake components and generate `local.properties` on first sync

If you'd rather not use Android Studio, install the [Android command-line
tools](https://developer.android.com/studio#command-tools), then:

```sh
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

The NDK and CMake are pulled in automatically on first build of `:mosh` (Gradle
auto-accepts and installs them via the AGP `externalNativeBuild` config), as long as
`sdkmanager`'s license for them has been accepted once (`sdkmanager --licenses`).

`:mosh`'s first build also fetches `rjyo/mosh-android`'s prebuilt release archive over the
network into a gitignored cache (`.moshlibs-cache/`) — see [VENDORED.md](VENDORED.md) and
`mosh/src/main/cpp/CMakeLists.txt` for the mechanics. Without network access at that point, the
build falls back to a simulated Mosh transport (compiles and runs, but never opens a real
session) — set `REINS_MOSH_LIBS_DIR` to a pre-fetched copy to build the real thing offline. Only
`arm64-v8a` is supported for Mosh: the vendored release's `armeabi-v7a`/`x86_64` static libs are
missing symbols (see the [ticket 025 resolution](.context/tickets/025-mosh-transport.md)), so
the `x86_64` emulator and `armeabi-v7a` devices can select a Mosh-transport Host but won't get a
real connection.

## Building

```sh
./gradlew assembleDebug        # build the full app, including :mosh's native build
./gradlew installDebug         # build and install on a connected device/emulator
```

## Testing locally

No CI — this is local-build-only (ticket 014). Run tests per layer directly:

```sh
./gradlew :domain:test                          # plain JUnit, pure Kotlin, fast
./gradlew :data:test                             # Robolectric + fakes for Room/sshj/Keystore
./gradlew :presentation:connectedAndroidTest      # Compose UI tests — needs a connected device/emulator
./gradlew test                                    # every JVM-level test module at once
```

`:mosh` is a thin JNI wrapper — not unit-tested at the native boundary; verify it
with a manual smoke check on a real device instead (see its ticket for what to check).

To run everything that doesn't need a device:

```sh
./gradlew test
```

## Project status

Early skeleton (ticket 016) — module structure and build wiring only, no feature code yet. See
`.context/map.md` (local, gitignored — Claude Code wayfinder planning artifacts, not shipped with
the repo) for the full spec this was built against, and `.context/tickets/` for the implementation
backlog and its dependency order.

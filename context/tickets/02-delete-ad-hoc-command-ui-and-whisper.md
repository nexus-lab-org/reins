# 02 - Delete Ad Hoc Command UI and the whisper module

**What to build:** The terminal screen no longer has a Keyboard-mode floating text field or a Voice-mode mic FAB - both input modes and their mode-toggle FABs are gone. The terminal is just the terminal. Since Voice mode is gone, the on-device whisper.cpp transcription module has no remaining caller anywhere in the app and is deleted outright rather than left as dead weight.

**Blocked by:** None - can start immediately

- [ ] Ad Hoc Command Keyboard field, Ad Hoc Command Voice field, and the mode-toggle FABs are removed from the terminal screen
- [ ] The terminal screen renders as just the terminal view, full-screen, with no overlay UI
- [ ] The `:whisper` module (native build, bundled model asset, JNI bridge) is deleted, including its `settings.gradle.kts` module inclusion and its `VENDORED.md` entry
- [ ] No remaining reference anywhere in the app to the deleted Voice/Keyboard-mode classes or the whisper module
- [ ] App builds and runs; connecting to a Host still lands in a working plain terminal (typing still works via whatever default input path remains at this point - full keyboard-at-cursor polish is ticket 03)

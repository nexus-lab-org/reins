# 05 - Settings: Extra Keys picker + swipe/autocorrect toggle

**What to build:** Two additions to the existing Settings screen, following its established in-memory/process-lifetime settings-state pattern (no settings-persistence layer exists yet - this ticket doesn't introduce one).

1. An Extra Keys picker: checkboxes for a fixed catalog (Ctrl, Esc, Tab, the 4 arrows, and `| / - _`), reorderable, defaulting to Ctrl/Esc/Tab/arrows on and the symbols off. This drives which keys ticket 04's row actually shows.
2. An "Enable swipe/autocorrect (experimental)" toggle, off by default. Turning it on switches the terminal's keyboard input type from the current suggestions-suppressing configuration to a normal text input type that supports swipe-typing and autocorrect. This is known to risk composing-region bugs against a terminal (no real text buffer for the IME to query) - it's opt-in and off by default specifically because of that risk, not because it's expected to be broken.

**Blocked by:** Ticket 04 (needs the fixed key catalog and the row's key-set consumption point to exist before Settings can drive it)

- [ ] Settings screen shows a checkbox list for Ctrl, Esc, Tab, the 4 arrows, and `| / - _`, defaulting to Ctrl/Esc/Tab/arrows checked and the four symbols unchecked
- [ ] The list supports reordering, and the extra-keys row (ticket 04) reflects both which keys are checked and their order
- [ ] Settings screen shows an "Enable swipe/autocorrect (experimental)" toggle, off by default
- [ ] Turning the toggle on changes the terminal's keyboard input type to support swipe/autocorrect; turning it off restores the current suggestions-suppressing behavior
- [ ] Verified on-device with the toggle on: swipe-typing and autocorrect actually work; note any composing-region glitches observed for follow-up, but don't block shipping the toggle on finding some (it's explicitly experimental)

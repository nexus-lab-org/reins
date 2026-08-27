# 04 - Extra-keys row (Ctrl/Esc/Tab/arrows)

**What to build:** With no Command Dial and no ad-hoc field, there's currently no way to send Ctrl+C, Esc, Tab, or arrow keys - keys most mobile OS keyboards don't expose - which matters a lot for steering a remote coding agent. A slim row of keys sits docked directly above the OS keyboard, visible only while the keyboard is showing (disappears when it's dismissed, leaving an unobstructed terminal). Ctrl behaves as a sticky/armed modifier: tapping it arms it, and the next keypress is sent as Ctrl+that key (so Ctrl+C is two taps: Ctrl, then C).

**Blocked by:** Ticket 03 (needs IME-visibility tracking already wired up to dock the row against)

- [ ] A row showing Ctrl, Esc, Tab, and 4 arrow keys appears docked directly above the OS keyboard whenever it's visible
- [ ] The row disappears when the OS keyboard is dismissed
- [ ] Tapping Ctrl arms it (visibly, e.g. a highlighted/pressed state); the next keystroke (from the OS keyboard or from another extra key) is sent as Ctrl+that key, then Ctrl disarms
- [ ] Tapping Esc, Tab, or an arrow key sends that key to the remote shell immediately (no arming needed)
- [ ] Verified on-device: Ctrl+C actually interrupts a running remote command

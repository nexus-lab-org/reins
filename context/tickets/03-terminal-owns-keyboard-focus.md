# 03 - Terminal owns keyboard focus naturally

**What to build:** Landing on the terminal after connecting brings the OS keyboard up immediately, right at the terminal's cursor, with no extra tap or mode toggle needed - the same feel as opening any other terminal app. While the keyboard is showing, swiping back (or pressing system back) dismisses the keyboard first instead of leaving the terminal screen, consistent with CONTEXT.md's existing "leaving Terminal is a UI-stack pop only" principle - dismissing the keyboard isn't leaving.

**Blocked by:** Ticket 02 (needs the ad-hoc overlay UI gone so the terminal is the only thing that can own focus)

- [ ] The terminal view is focusable and takes focus in touch mode
- [ ] The OS keyboard appears automatically as soon as the terminal screen is shown, without requiring a tap first
- [ ] Typing at the OS keyboard sends keystrokes to the remote shell exactly as before (this is a focus/IME-visibility change, not a change to how keystrokes reach the Data Channel)
- [ ] With the keyboard showing, a back gesture/press dismisses the keyboard and keeps the terminal screen open
- [ ] With the keyboard already dismissed, a back gesture/press behaves as it did before this ticket (leaves the terminal screen)

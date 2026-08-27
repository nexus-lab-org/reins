# 01 - Rewrite CONTEXT.md glossary

A prefactor: bring the domain glossary in line with the plain-SSH/Mosh terminal redesign already done in code, before later tickets touch the exact terms it defines.

**What to build:** CONTEXT.md accurately describes the current app - a plain SSH/Mosh terminal client with no herdr-specific protocol integration. Command Dial, Control Channel, and Agent Session are removed from the glossary (all three no longer exist in the codebase). The "Ad Hoc Command" entry, currently defined only in contrast to the Command Dial ("for anything the Command Dial doesn't cover"), is retired or redefined so it no longer references a UI element that's about to be deleted.

**Blocked by:** None - can start immediately

- [ ] CONTEXT.md's overview paragraph describes Reins as a plain SSH/Mosh terminal client, not a herdr-protocol client
- [ ] Command Dial, Control Channel, and Agent Session glossary entries are removed
- [ ] Data Channel entry no longer references Command Dial keypresses as part of it
- [ ] Ad Hoc Command entry is retired or redefined without depending on Command Dial
- [ ] No remaining reference in CONTEXT.md to concepts absent from the current codebase

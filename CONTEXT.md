# Reins

An Android (later iOS) app for steering remote coding agents from a phone — a plain SSH/Mosh terminal client that lands you in a normal interactive login shell, exactly as if you'd typed `ssh user@host` (or `mosh user@host`) yourself. Reins carries no protocol of its own beyond that: whatever the remote shell can run — `herdr attach`, `tmux attach`, anything else — is up to the person typing at it.

## Language

**Data Channel**:
The channel carrying raw keystrokes into the remote shell's PTY, exactly as if typed at a local keyboard — the terminal's own typing, sent straight over the connection with no framing of any kind.
_Avoid_: stdin, terminal stream

## Data model

**Host**:
A saved remote target: display name, address, port, an explicit transport (SSH or Mosh — no auto-detect), one Identity reference, and a TOFU-pinned host-key fingerprint. Holds no live connection state itself — a Host being "connected" is runtime state owned above the navigation graph, not a Host field.
_Avoid_: server, connection, saved connection

**Identity**:
An SSH key, referenced by one or more Hosts (many-to-one — the same key commonly authenticates multiple Hosts). A sealed type with two variants: `ImportedKeyIdentity` (encrypted key blob + optional passphrase) or `KeystoreIdentity` (Android Keystore alias, EC P-256, no key material stored). A Host has exactly one Identity, never a fallback list.
_Avoid_: key, credential, keypair (too generic — always name the variant when it matters)

package co.maxasif.reins.data.ssh

import java.security.PublicKey
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier

/**
 * TOFU (trust-on-first-use) host-key verification for `Host.hostKeyFingerprint` (ticket 009, 018).
 * `pinnedFingerprint == null` means no key has been pinned yet (first connect to this Host): any
 * key is accepted and reported via [onUnpinned] so the caller can persist it - matching the
 * "set on first successful connect" half of ticket 009's spec. Once a fingerprint is pinned, any
 * mismatch hard-blocks with no silent bypass - [PromiscuousVerifier][net.schmizz.sshj.transport.verification.PromiscuousVerifier]
 * is gone from the real connect path (still fine for tests).
 *
 * [HostKeyVerifier.verify] must return a plain `Boolean` - sshj itself is what throws once we
 * return `false`, wrapping it as a generic transport exception whose message doesn't distinguish
 * "key changed" from any other handshake failure. [onMismatch] fires first so the caller can
 * capture the distinct reason (ticket 019) before that generic exception surfaces.
 */
class TofuHostKeyVerifier(
    private val pinnedFingerprint: String?,
    private val onUnpinned: (String) -> Unit,
    private val onMismatch: (presented: String, pinned: String) -> Unit = { _, _ -> },
) : HostKeyVerifier {
    override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
        val fingerprint = SecurityUtils.getFingerprint(key)
        if (pinnedFingerprint == null) {
            onUnpinned(fingerprint)
            return true
        }
        if (fingerprint != pinnedFingerprint) {
            onMismatch(fingerprint, pinnedFingerprint)
            return false
        }
        return true
    }

    override fun findExistingAlgorithms(hostname: String?, port: Int): MutableList<String> = mutableListOf()
}

/**
 * Thrown by the connect flow (not by [TofuHostKeyVerifier] itself - see its [TofuHostKeyVerifier.onMismatch]
 * doc) when a Host's presented key fingerprint doesn't match its pinned one, so the Connect screen
 * can show a specific "host key changed" warning instead of a generic transport-failure message.
 */
class HostKeyMismatchException(
    val presentedFingerprint: String,
    val pinnedFingerprint: String,
) : Exception(
    "This host's key has changed since it was last pinned (expected $pinnedFingerprint, got " +
        "$presentedFingerprint). Refusing to connect - this could mean the host was re-keyed, or " +
        "someone is impersonating it.",
)

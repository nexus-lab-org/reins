package co.maxasif.reins.data.ssh

import net.schmizz.sshj.common.SecurityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Android ships a stub security provider registered under the name "BC" that lacks algorithms
 * sshj's SecurityUtils assumes any "BC" provider has (X25519 key agreement, MD5 digests for
 * fingerprinting). sshj auto-detects a provider named "BC" and trusts it blindly, so we must
 * replace Android's stub with the real BouncyCastle implementation before any sshj/Keystore
 * identity code runs.
 *
 * [SecurityUtils.setRegisterBouncyCastle] is the important call here, more than the provider
 * registration itself: sshj's own `SecurityUtils.register()` (triggered lazily on its first
 * `getSignature`/`getKeyFactory`/etc call) does its own capability probe against a "BC" provider
 * and, once that succeeds, calls `setSecurityProvider("BC")` - which makes every later
 * `SecurityUtils.getSignature(algorithm)` call resolve via the explicit-provider overload,
 * `Signature.getInstance(algorithm, "BC")`, not the provider-priority-based
 * `Signature.getInstance(algorithm)`. That completely bypasses JCA's normal provider ordering (so
 * reordering/re-registering the "BC" provider here has no effect on it), and unconditionally routes
 * every EC signature - including ones over a Keystore-backed identity's opaque, non-exportable
 * private key - to BouncyCastle's software implementation, which can't use that key
 * (`InvalidKeyException: no encoding for EC private key`). That made every Key-auth attempt against
 * a `KeystoreIdentity` fail with sshj reporting "Exhausted available authentication methods" -
 * confirmed via a device repro. Disabling sshj's self-registration keeps `getSignature` on the
 * provider-priority path, where `AndroidKeyStore`'s own EC engine (which does support this key)
 * wins; real BC stays registered (at low priority, so it doesn't shadow Android's own providers
 * either) purely so it's still present by name for algorithms Android's providers lack (X25519,
 * MD5 fingerprinting).
 */
object BouncyCastleSetup {
    fun install() {
        SecurityUtils.setRegisterBouncyCastle(false)
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) is BouncyCastleProvider) return
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
    }
}

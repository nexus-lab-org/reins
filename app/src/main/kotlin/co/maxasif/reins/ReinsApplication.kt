package co.maxasif.reins

import android.app.Application
import androidx.room.Room
import co.maxasif.reins.data.db.ReinsDatabase
import co.maxasif.reins.data.identity.IdentityKeyCipher
import co.maxasif.reins.data.repository.HostRepositoryImpl
import co.maxasif.reins.data.repository.IdentityRepositoryImpl
import co.maxasif.reins.data.ssh.BouncyCastleSetup

/**
 * Manual DI wiring - no DI framework in this project, so the Room database and repositories are
 * built once here and read off `(application as ReinsApplication)` by [MainActivity]. The
 * foreground connection Service (ticket 013, 026) is registered from this module too, not
 * :data or :presentation.
 */
class ReinsApplication : Application() {
    lateinit var hostRepository: HostRepositoryImpl
        private set
    lateinit var identityRepository: IdentityRepositoryImpl
        private set

    override fun onCreate() {
        super.onCreate()
        // Must run before any sshj/Keystore identity code (SSH connects, on-device key
        // generation) - both rely on a "BC" provider with real X25519/MD5 support, which
        // Android's bundled stub provider of the same name does not have.
        BouncyCastleSetup.install()
        // No installed base predates this app's own development, so a destructive migration (drop
        // and recreate) is the right tradeoff for schema churn over hand-written Room migrations.
        val database = Room.databaseBuilder(this, ReinsDatabase::class.java, ReinsDatabase.FILE_NAME)
            .fallbackToDestructiveMigration()
            .build()
        hostRepository = HostRepositoryImpl(database.hostDao())
        identityRepository = IdentityRepositoryImpl(database.identityDao(), IdentityKeyCipher(), this)
    }
}

package co.maxasif.reins.domain.repository

import co.maxasif.reins.domain.model.Host
import co.maxasif.reins.domain.model.HostAuthMethod
import co.maxasif.reins.domain.model.Transport
import kotlinx.coroutines.flow.Flow

/** Room-backed persistence for [Host] (ticket 018), implemented in `:data`. */
interface HostRepository {
    fun observeHosts(): Flow<List<Host>>

    suspend fun getHost(id: String): Host?

    suspend fun createHost(
        displayName: String,
        username: String,
        hostname: String,
        port: Int,
        transport: Transport,
        authMethod: HostAuthMethod,
    ): Host

    suspend fun updateHost(host: Host)

    suspend fun deleteHost(id: String)

    /** Persists the TOFU host-key fingerprint pinned on first successful connect (ticket 009). */
    suspend fun pinHostKeyFingerprint(hostId: String, fingerprint: String)
}

package co.maxasif.reins.data.repository

import co.maxasif.reins.data.db.dao.HostDao
import co.maxasif.reins.data.db.mapper.toDomain
import co.maxasif.reins.data.db.mapper.toEntity
import co.maxasif.reins.domain.model.Host
import co.maxasif.reins.domain.model.HostAuthMethod
import co.maxasif.reins.domain.model.Transport
import co.maxasif.reins.domain.repository.HostRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HostRepositoryImpl(private val dao: HostDao) : HostRepository {
    override fun observeHosts(): Flow<List<Host>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getHost(id: String): Host? = dao.getById(id)?.toDomain()

    override suspend fun createHost(
        displayName: String,
        username: String,
        hostname: String,
        port: Int,
        transport: Transport,
        authMethod: HostAuthMethod,
    ): Host {
        val host = Host(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            username = username,
            hostname = hostname,
            port = port,
            transport = transport,
            authMethod = authMethod,
            hostKeyFingerprint = null,
        )
        dao.insert(host.toEntity())
        return host
    }

    override suspend fun updateHost(host: Host) {
        dao.update(host.toEntity())
    }

    override suspend fun deleteHost(id: String) {
        dao.deleteById(id)
    }

    override suspend fun pinHostKeyFingerprint(hostId: String, fingerprint: String) {
        dao.updateHostKeyFingerprint(hostId, fingerprint)
    }
}

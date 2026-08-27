package co.maxasif.reins.data.db.mapper

import co.maxasif.reins.data.db.entity.HostEntity
import co.maxasif.reins.domain.model.Host
import co.maxasif.reins.domain.model.HostAuthMethod
import co.maxasif.reins.domain.model.Transport

fun HostEntity.toDomain(): Host = Host(
    id = id,
    displayName = displayName,
    username = username,
    hostname = hostname,
    port = port,
    transport = transport.toDomainTransport(),
    authMethod = toDomainAuthMethod(authMethod, identityId),
    hostKeyFingerprint = hostKeyFingerprint,
)

fun Host.toEntity(): HostEntity = HostEntity(
    id = id,
    displayName = displayName,
    username = username,
    hostname = hostname,
    port = port,
    transport = transport.toEntityTransport(),
    authMethod = authMethod.toEntityAuthMethod(),
    identityId = (authMethod as? HostAuthMethod.Key)?.identityId,
    hostKeyFingerprint = hostKeyFingerprint,
)

private fun String.toDomainTransport(): Transport = when (this) {
    HostEntity.TRANSPORT_SSH -> Transport.Ssh
    HostEntity.TRANSPORT_MOSH -> Transport.Mosh
    else -> error("Unknown Host.transport in Room row: $this")
}

private fun Transport.toEntityTransport(): String = when (this) {
    Transport.Ssh -> HostEntity.TRANSPORT_SSH
    Transport.Mosh -> HostEntity.TRANSPORT_MOSH
}

private fun toDomainAuthMethod(authMethod: String, identityId: String?): HostAuthMethod = when (authMethod) {
    HostEntity.AUTH_KEY -> HostAuthMethod.Key(requireNotNull(identityId) { "Key-auth Host row missing identityId" })
    HostEntity.AUTH_PASSWORD -> HostAuthMethod.Password
    else -> error("Unknown Host.authMethod in Room row: $authMethod")
}

private fun HostAuthMethod.toEntityAuthMethod(): String = when (this) {
    is HostAuthMethod.Key -> HostEntity.AUTH_KEY
    is HostAuthMethod.Password -> HostEntity.AUTH_PASSWORD
}

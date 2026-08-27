package co.maxasif.reins.domain.model

/** A Host's transport, explicit per-host with no auto-detect (ticket 009). */
enum class Transport {
    Ssh,
    Mosh,
}

package co.maxasif.reins.presentation.nav

/**
 * Reins' screen flow (ticket 008): Host List -> Host Form (add/edit) / Import Identity ->
 * Connect (stepper, lands on the terminal), plus Settings reachable from Host List's top bar
 * (ticket 029 - the Settings screen itself existed since ticket 021 but was never actually wired
 * into navigation until now).
 *
 * Deliberately a plain sealed class + a manual back-stack in `:app` rather than Jetpack Navigation
 * - the app has no navigation-compose dependency yet and doesn't need one for this few screens.
 */
sealed class ReinsDestination {
    object HostList : ReinsDestination()

    /** [hostId] `null` means "new Host". */
    data class HostForm(val hostId: String?) : ReinsDestination()

    object ImportIdentity : ReinsDestination()

    object GenerateKeystoreIdentity : ReinsDestination()

    data class Connect(val hostId: String) : ReinsDestination()

    object Settings : ReinsDestination()
}

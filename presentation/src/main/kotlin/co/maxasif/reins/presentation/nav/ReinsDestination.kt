package co.maxasif.reins.presentation.nav

/**
 * Reins' four-screen flow (ticket 008): Host List -> Host Form (add/edit) / Import Identity ->
 * Connect (stepper, lands on the terminal). Settings is reachable separately (ticket 021-024's
 * real navigation graph); this ticket only wires the Host List/Connect half.
 *
 * Deliberately a plain sealed class + a manual back-stack in `:app` rather than Jetpack Navigation
 * - the app has no navigation-compose dependency yet and doesn't need one for four screens.
 */
sealed class ReinsDestination {
    object HostList : ReinsDestination()

    /** [hostId] `null` means "new Host". */
    data class HostForm(val hostId: String?) : ReinsDestination()

    object ImportIdentity : ReinsDestination()

    object GenerateKeystoreIdentity : ReinsDestination()

    data class Connect(val hostId: String) : ReinsDestination()
}

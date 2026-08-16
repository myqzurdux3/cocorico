package com.cocorico.ring

enum class VolumeState { PLEIN, BAISSE }

/**
 * Décide du niveau sonore pendant l'alarme. Le contrat produit :
 * prendre le téléphone ou interagir avec le défi baisse le volume,
 * l'inactivité le fait remonter. Ne notifie que sur changement effectif,
 * pour ne pas repousser le volume système à chaque événement capteur.
 */
class VolumeStateMachine(private val onChange: (VolumeState) -> Unit) {

    var state: VolumeState = VolumeState.PLEIN
        private set

    fun onPhonePrisEnMain() = transition(VolumeState.BAISSE)

    fun onInteraction() = transition(VolumeState.BAISSE)

    fun onInactiviteExpiree() = transition(VolumeState.PLEIN)

    private fun transition(next: VolumeState) {
        if (next == state) return
        state = next
        onChange(next)
    }
}

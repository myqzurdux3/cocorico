package com.cocorico.challenge.pompes

/**
 * Un instant de mesure, tel que la coquille capteur le fournit.
 *
 * [procheDuCapteur] est un booléen et non une distance : beaucoup de capteurs de
 * proximité ne rapportent que près/loin. L'algorithme ne s'appuie donc que sur
 * le franchissement, jamais sur une valeur absolue, et reste indépendant du
 * modèle de téléphone.
 */
data class EchantillonPompe(
    val procheDuCapteur: Boolean,
    val inclinaisonDegres: Float,
    val ecartGravite: Float,
    val tMillis: Long,
)

enum class EtatPompes { ATTENTE_POSITION, PRET, BAS }

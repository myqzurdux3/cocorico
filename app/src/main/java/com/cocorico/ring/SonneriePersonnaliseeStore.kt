package com.cocorico.ring

import android.content.Context

/**
 * Mémoire disque de la sonnerie personnalisée importée par l'utilisateur :
 * l'URI du fichier choisi et son nom affichable.
 *
 * Un magasin dédié, sur le modèle de `VolumeOrigine` dans [RingtonePlayer],
 * plutôt qu'un champ ajouté à `AlarmConfig` : `AlarmConfig` et son dépôt DataStore
 * sont modifiés en parallèle par ailleurs, et cette persistance n'a de toute
 * façon rien à faire dans la transaction de configuration — elle ne change
 * que depuis cet écran, jamais depuis la logique d'alarme.
 */
object SonneriePersonnaliseeStore {

    private const val FICHIER = "cocorico_sonnerie_personnalisee"
    private const val CLE_URI = "uri"
    private const val CLE_NOM = "nom"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /** `null` si l'utilisateur n'a jamais importé de fichier. */
    fun lireUri(context: Context): String? = prefs(context).getString(CLE_URI, null)

    /** `null` si l'utilisateur n'a jamais importé de fichier. */
    fun lireNom(context: Context): String? = prefs(context).getString(CLE_NOM, null)

    fun ecrire(context: Context, uri: String, nom: String) {
        prefs(context).edit().putString(CLE_URI, uri).putString(CLE_NOM, nom).apply()
    }
}

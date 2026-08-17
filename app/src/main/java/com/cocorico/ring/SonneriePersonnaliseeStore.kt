package com.cocorico.ring

import android.content.Context
import android.content.Intent
import android.net.Uri

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

    /**
     * Remplace la sonnerie personnalisée, et relâche la permission persistée sur
     * l'ancienne URI. Sans ce relâchement, chaque import en accumulait une de
     * plus : la réserve de permissions persistées d'une application est plafonnée
     * par le système, qui purge les plus anciennes une fois le quota atteint —
     * y compris celle en cours d'usage. La sonnerie devient alors illisible au
     * réveil, des semaines après le dernier passage sur cet écran.
     *
     * L'ordre compte : la nouvelle permission est déjà prise par l'appelant
     * quand on arrive ici, et l'ancienne n'est relâchée que si elle diffère —
     * réimporter le même fichier ne doit pas révoquer la permission qu'on vient
     * d'obtenir. Le `runCatching` couvre le cas d'une permission déjà révoquée
     * par le système : l'échec ne doit pas empêcher l'enregistrement.
     */
    fun ecrire(context: Context, uri: String, nom: String) {
        val ancienne = lireUri(context)
        if (ancienne != null && ancienne != uri) {
            runCatching {
                context.applicationContext.contentResolver.releasePersistableUriPermission(
                    Uri.parse(ancienne),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        prefs(context).edit().putString(CLE_URI, uri).putString(CLE_NOM, nom).apply()
    }
}

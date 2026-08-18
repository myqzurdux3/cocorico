package com.cocorico.ring

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Sonde la lisibilité d'un fichier importé au moment où l'utilisateur le
 * choisit, pas au réveil : c'est le seul moment où apprendre qu'un fichier
 * ne convient pas est encore utile à quelqu'un. Le câblage Android (préparer
 * un `MediaPlayer` jetable, interroger le fournisseur de contenu) vit ici ;
 * la décision — jouable ou pas, quel nom afficher — revient à
 * [SonneriePersonnaliseeLogique], seule partie testée.
 */
object SondeSonnerie {

    /**
     * Prépare un lecteur jetable sur l'URI et regarde sa durée. Toute
     * défaillance — format exotique, fichier corrompu, permission déjà
     * perdue — est absorbée par `runCatching` : aucune exception ne doit
     * remonter jusqu'à l'écran de sélection, seul le verdict compte.
     *
     * **À appeler hors du thread principal.** `MediaPlayer.create` prépare le
     * média de façon synchrone : sur une URI servie par un fournisseur distant
     * (stockage réseau, application tierce endormie), la préparation peut durer
     * plusieurs secondes et bloque alors l'interface jusqu'à l'ANR. L'appelant
     * doit l'exécuter dans une coroutine sur un dispatcher d'entrées-sorties.
     *
     * Le `release()` est dans un `finally` : la lecture de `duration` peut
     * lever, et le lecteur restait alors ouvert — un descripteur de fichier et
     * une session audio fuités à chaque fichier refusé.
     */
    fun estLisible(context: Context, uri: Uri): Boolean {
        val duree = runCatching {
            MediaPlayer.create(context, uri)?.let { lecteur ->
                try {
                    lecteur.duration
                } finally {
                    runCatching { lecteur.release() }
                }
            }
        }.getOrNull()
        return SonneriePersonnaliseeLogique.estJouable(duree)
    }

    /**
     * Nom déclaré par le fournisseur de contenu (colonne `DISPLAY_NAME`),
     * quand il existe. `null` en cas d'échec — fournisseur récalcitrant,
     * colonne absente — laissant [SonneriePersonnaliseeLogique.nomAffichable]
     * retomber sur le dernier segment de l'URI.
     */
    fun nomInterroge(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { curseur ->
                val colonne = curseur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (colonne >= 0 && curseur.moveToFirst()) curseur.getString(colonne) else null
            }
    }.getOrNull()
}

package com.cocorico.challenge.photo

import android.graphics.Bitmap

/**
 * Rend un verdict sur une photo : montre-t-elle l'objet demandé ?
 *
 * Une seule implémentation existe, [JugeGemini], qui interroge un modèle de
 * vision distant. La reconnaissance embarquée qui la précédait a été retirée.
 * L'interface reste : c'est elle qui permet de décrire le défi sans caméra ni
 * réseau, et de remplacer le juge par un double dans un test.
 *
 * **Contrat impératif : aucune implémentation ne lève d'exception.** Clé
 * absente, réseau coupé, délai dépassé, réponse illisible — tout se traduit
 * par un [DiagnosticJuge]. Ces classes s'exécutent pendant qu'une alarme hurle
 * à plein volume et qu'un utilisateur debout attend un verdict. Une exception
 * qui remonterait ferait planter l'application devant la sirène, et il ne
 * resterait plus qu'à forcer l'arrêt du téléphone pour la faire taire.
 *
 * Un refus n'est jamais définitif : l'utilisateur reprend une photo. Encore
 * faut-il que ce soit un refus — d'où [juger] plutôt qu'un simple booléen.
 */
interface JugePhoto {

    /**
     * Le verdict **et sa cause**. Le défi a besoin des deux : un booléen seul
     * ne distingue pas « le modèle ne reconnaît pas l'objet », qu'une autre
     * photo peut lever, de « le juge n'a pas répondu », qu'aucune photo ne
     * lèvera. Présenter le second comme le premier laisse quelqu'un
     * rephotographier sans fin devant une sirène ; voir [IssueJuge].
     *
     * L'image reste en mémoire : aucune implémentation ne l'écrit sur le
     * disque, dans aucun mode.
     */
    suspend fun juger(image: Bitmap, objet: ObjetPhoto): DiagnosticJuge

    /** Le seul verdict, pour les appelants que la cause n'intéresse pas. */
    suspend fun accepte(image: Bitmap, objet: ObjetPhoto): Boolean =
        juger(image, objet).accepte

    /**
     * Libère ce que l'implémentation détient. Vide par défaut : un juge sans
     * ressource n'a rien à fermer.
     */
    fun fermer() = Unit
}

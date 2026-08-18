package com.cocorico.ring

/**
 * Décisions pures autour de la sonnerie personnalisée importée par
 * l'utilisateur : aucune dépendance Android, donc testable sans appareil.
 * Le câblage — sonder un fichier avec `MediaPlayer`, interroger le
 * fournisseur de contenu, lire et écrire l'URI sur disque — vit ailleurs :
 * [SondeSonnerie], [SonneriePersonnaliseeStore] et [RingtonePlayer].
 */
object SonneriePersonnaliseeLogique {

    /** Source à tenter pour la lecture réelle de l'alarme. */
    sealed class SourceAJouer {
        /** L'URI importée est enregistrée : on tente de la lire en premier. */
        data class Personnalisee(val uri: String) : SourceAJouer()

        /** Pas de sonnerie personnalisée sélectionnée, ou pas d'URI enregistrée. */
        object Embarquee : SourceAJouer()
    }

    /**
     * Quelle source tenter en premier. Une sonnerie personnalisée choisie
     * mais sans URI enregistrée — ne devrait pas arriver puisqu'on ne
     * sélectionne qu'après avoir persisté, mais un magasin vidé entre-temps
     * y suffit — se replie tout de suite sur l'embarquée : inutile de
     * tenter une lecture qu'on sait vouée à l'échec faute d'URI.
     */
    fun sourceAJouer(personnalisee: Boolean, uriPersistee: String?): SourceAJouer =
        if (personnalisee && !uriPersistee.isNullOrBlank()) {
            SourceAJouer.Personnalisee(uriPersistee)
        } else {
            SourceAJouer.Embarquee
        }

    /**
     * Un fichier importé est jouable comme sonnerie s'il a pu être préparé
     * et que sa durée sondée est strictement positive. `null` (sonde en
     * échec), zéro ou négative (métadonnée absente ou fichier vide) ne sont
     * jamais assez pour risquer une alarme silencieuse : mieux vaut refuser
     * le fichier au moment du choix que le découvrir six heures plus tard.
     */
    fun estJouable(dureeMs: Int?): Boolean = dureeMs != null && dureeMs > 0

    /**
     * Nom affiché pour le fichier importé. Le nom interrogé auprès du
     * fournisseur de contenu (colonne `DISPLAY_NAME`) est préféré quand il
     * existe ; à défaut — fournisseur récalcitrant, colonne absente — on
     * retombe sur le dernier segment de l'URI plutôt que sur l'URI brut,
     * illisible pour un humain.
     */
    fun nomAffichable(uri: String, nomInterroge: String?): String {
        val propre = nomInterroge?.trim()
        if (!propre.isNullOrBlank()) return propre
        val segment = uri.substringAfterLast('/').trim()
        return segment.ifBlank { "Sonnerie personnalisée" }
    }
}

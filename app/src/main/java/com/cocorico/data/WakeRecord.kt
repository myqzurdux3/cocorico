package com.cocorico.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Un réveil mené jusqu'à la résolution du défi. */
@Entity(tableName = "wake_records")
data class WakeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmeAt: Long,
    val resoluAt: Long,
    val erreurs: Int,
    /**
     * **Colonne morte : jamais alimentée, jamais lue.** Tous les appelants
     * écrivent 0 en dur, aucun écran ni calcul ne la consulte. Ne pas la prendre
     * pour une donnée : un `triches = 0` en base ne signifie pas « pas de
     * triche », il signifie « rien n'a jamais été mesuré ».
     *
     * Elle reste là parce que l'enlever n'est pas un nettoyage mais un
     * changement à part entière : SQLite ne supprime une colonne qu'en
     * recréant la table, il faudrait donc une migration Room v3 et le schéma
     * versionné correspondant, avec le risque de plantage au démarrage que
     * comporte toute désynchronisation entre schéma généré et migration.
     *
     * Pour l'enlever un jour : retirer le champ ici, écrire `MIGRATION_2_3` qui
     * recrée `wake_records` sans la colonne et y recopie les lignes, l'ajouter à
     * [CocoricoDatabase], porter la version à 3 et regénérer le schéma versionné.
     */
    val triches: Int,
    /**
     * Identifiant du défi accompli, au format [ChallengeId].
     *
     * `defaultValue` est répété ici volontairement : Room n'infère jamais la valeur
     * par défaut SQL à partir de la valeur par défaut Kotlin du constructeur — sans
     * cette annotation, le schéma généré pour la table n'aurait pas de défaut et ne
     * correspondrait plus, au caractère près, à celui produit par [CocoricoDatabase.MIGRATION_1_2],
     * ce qui ferait planter Room au démarrage (IllegalStateException de validation).
     */
    @ColumnInfo(defaultValue = "'MATHS'")
    val defi: String = ChallengeId.MATHS.name,
    /** Vrai si l'utilisateur a renoncé au défi initial pour se rabattre sur les calculs. */
    @ColumnInfo(defaultValue = "0")
    val abandon: Boolean = false,
)

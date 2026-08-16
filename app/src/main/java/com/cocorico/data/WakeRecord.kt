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

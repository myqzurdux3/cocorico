package com.cocorico.alarm

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Traduit l'heure murale voulue en instant absolu — la seule chose que
 * `setAlarmClock` sait mémoriser.
 *
 * Deux jours par an, cette traduction n'a pas de réponse évidente, et
 * `LocalDateTime.atZone()` tranchait pour nous sans le dire. La règle retenue
 * ici est celle qui sert un réveil : **jamais plus tard que l'heure demandée**.
 * Sonner un peu trop tôt réveille ; sonner une heure trop tard fait rater
 * l'avion.
 *
 * Trou du printemps (2 h 30 le jour où 2 h 00 devient 3 h 00) : l'heure
 * demandée n'existe pas. `atZone` décale d'une heure pleine et sonne à 3 h 30 ;
 * on sonne à la bascule elle-même, 3 h 00, c'est-à-dire au premier instant où
 * l'heure demandée est dépassée.
 *
 * Recouvrement d'automne (2 h 30 le jour où 3 h 00 redevient 2 h 00) : l'heure
 * demandée arrive deux fois. On prend la première, la seule qui garantisse de
 * ne pas réveiller une heure trop tard. C'est aussi ce que faisait `atZone`,
 * mais par accident : le rendre explicite empêche qu'une réécriture innocente
 * l'inverse.
 *
 * Type pur, sans import `android.*` : la décision se teste avec un fuseau fixe
 * et une date de bascule connue, sans téléphone.
 */
object InstantSonnerie {

    fun resoudre(heureLocale: LocalDateTime, zone: ZoneId): Instant {
        val regles = zone.rules
        val offsets = regles.getValidOffsets(heureLocale)
        return when {
            // Cas courant : une seule lecture possible.
            offsets.size == 1 -> heureLocale.toInstant(offsets[0])
            // Recouvrement : deux offsets, le premier de la liste est celui
            // d'avant la bascule, donc l'instant le plus tôt.
            offsets.size > 1 -> heureLocale.toInstant(offsets[0])
            // Trou : aucun offset valide. La transition porte l'instant exact
            // où l'horloge saute, et c'est le premier instant postérieur à
            // l'heure demandée.
            else -> regles.getTransition(heureLocale).instant
        }
    }
}

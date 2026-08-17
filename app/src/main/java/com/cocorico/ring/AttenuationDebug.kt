package com.cocorico.ring

import android.content.Context
import android.util.Log
import com.cocorico.BuildConfig
import java.io.File
import kotlin.math.roundToInt

/**
 * Atténuation réservée aux essais : permet de faire sonner l'alarme pour de
 * vrai, chez soi, en pleine journée, sans une sirène à plein volume dans les
 * oreilles.
 *
 * **Trois garde-fous, parce qu'un réveil silencieux est le seul échec que ce
 * produit n'a pas le droit de commettre :**
 *
 * 1. **Rien de tout ceci n'existe en version publiée.** [consigne] renvoie
 *    toujours `null` hors d'une version de débogage — la vérification porte sur
 *    `BuildConfig.DEBUG`, donc R8 supprime le corps de la fonction.
 * 2. **Il faut le demander explicitement.** Même en débogage, l'atténuation
 *    n'existe que si un fichier a été déposé à la main. Une version de débogage
 *    qui sonnerait bas d'office ferait prendre une panne pour un réglage.
 * 3. **Jamais le silence.** Le niveau rendu est toujours d'au moins 1 : un essai
 *    muet ne permet pas de distinguer « ça marche mais c'est bas » de « ça n'a
 *    jamais démarré ».
 *
 * Volontairement en dehors de [NiveauxVolume] : la logique produit — le plafond
 * choisi par l'utilisateur et son plancher à 50 % — n'est pas modifiée. Le
 * niveau est calculé exactement comme en production, puis atténué en dernier.
 * Ce qui est mis à l'épreuve pendant l'essai reste donc le vrai code.
 *
 * Pour l'activer, appareil branché :
 * ```
 * adb shell "mkdir -p /sdcard/Android/data/com.cocorico/files"
 * adb shell "echo 10 > /sdcard/Android/data/com.cocorico/files/attenuation_essai"
 * ```
 * Pour revenir à la normale :
 * ```
 * adb shell "rm /sdcard/Android/data/com.cocorico/files/attenuation_essai"
 * ```
 */
object AttenuationDebug {

    const val FICHIER = "attenuation_essai"

    /**
     * Applique l'atténuation à un niveau déjà calculé par [NiveauxVolume].
     * Pure, donc testable : [pourcent] à `null` rend le niveau intact.
     */
    fun appliquer(niveau: Int, pourcent: Int?): Int {
        if (pourcent == null) return niveau
        val borne = pourcent.coerceIn(1, 100)
        return (niveau * borne / 100f).roundToInt().coerceIn(1, niveau)
    }

    /** Lit une consigne écrite à la main : tout ce qui n'est pas un entier vaut « pas de consigne ». */
    fun lireConsigne(contenu: String?): Int? = contenu?.trim()?.toIntOrNull()

    /**
     * La consigne en vigueur, ou `null`. Toujours `null` en version publiée.
     *
     * L'échec de lecture est traité comme une absence de consigne : dans le
     * doute, on sonne normalement. Se tromper dans ce sens réveille quelqu'un
     * qui ne le voulait pas ; se tromper dans l'autre le laisse dormir.
     */
    fun consigne(context: Context): Int? {
        if (!BuildConfig.DEBUG) return null
        val consigne = runCatching {
            val fichier = File(context.getExternalFilesDir(null), FICHIER)
            if (fichier.exists()) lireConsigne(fichier.readText()) else null
        }.getOrNull()
        if (consigne != null) {
            Log.w(
                "Cocorico",
                "ATTENUATION D'ESSAI ACTIVE : le volume est bridé à $consigne % du niveau calculé. " +
                    "Ce n'est pas le comportement d'un vrai réveil.",
            )
        }
        return consigne
    }
}

package com.cocorico.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.cocorico.alarm.AlarmScheduler

data class EtatPermissions(
    val alarmesExactes: Boolean,
    val notifications: Boolean,
    val pleinEcran: Boolean,
    val batterieExemptee: Boolean,
    /**
     * Volontairement absente de [toutesAccordees] : elle n'est utile qu'au
     * défi photo, jamais aux calculs ni aux pompes, et un refus n'est pas
     * bloquant — le défi photo retombe alors sur les calculs (voir
     * `AlarmActivity.construireDefi`). L'inclure ici bloquerait l'accueil de
     * quiconque n'a jamais choisi la photo, pour une permission qu'il n'a
     * jamais eu de raison qu'on lui demande.
     */
    val camera: Boolean,
) {
    val toutesAccordees: Boolean
        get() = alarmesExactes && notifications && pleinEcran && batterieExemptee
}

object PermissionChecker {

    fun etat(context: Context) = EtatPermissions(
        alarmesExactes = AlarmScheduler(context).canScheduleExact(),
        notifications = notificationsAccordees(context),
        pleinEcran = pleinEcranAccorde(context),
        batterieExemptee = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
        camera = cameraAccordee(context),
    )

    /** Permission caméra du défi photo. Voir la KDoc de [EtatPermissions.camera]. */
    private fun cameraAccordee(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Depuis Android 14, USE_FULL_SCREEN_INTENT n'est plus accordée à
     * l'installation aux applications que le système ne reconnaît pas comme
     * réveil ou téléphonie. Sans elle, l'alarme se dégrade en simple bandeau et
     * AlarmActivity n'apparaît jamais par-dessus l'écran verrouillé : c'est tout
     * le mécanisme du produit qui tombe. En dessous d'Android 14, elle est acquise.
     */
    private fun pleinEcranAccorde(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)
                .canUseFullScreenIntent()
        } else {
            true
        }

    private fun notificationsAccordees(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}

/**
 * Certains constructeurs tuent les services en veille malgré l'exemption
 * standard. Sans ces consignes, l'alarme rate silencieusement — première cause
 * d'avis à une étoile chez les concurrents.
 */
object Constructeurs {

    fun reglageBatterie(fabricant: String): String? = when (fabricant.lowercase()) {
        "samsung" -> "Réglages › Batterie › Limites d'utilisation en arrière-plan : " +
            "retirer Cocorico des applications en veille."
        "xiaomi", "redmi", "poco" -> "Réglages › Applications › Cocorico › " +
            "Économiseur de batterie : choisir « Aucune restriction », et activer " +
            "« Démarrage automatique »."
        "oppo", "realme", "oneplus" -> "Réglages › Batterie › Utilisation en arrière-plan : " +
            "autoriser Cocorico à fonctionner en arrière-plan."
        "huawei", "honor" -> "Réglages › Batterie › Lancement d'applications : " +
            "gérer Cocorico manuellement et tout autoriser."
        "vivo" -> "Réglages › Batterie › Consommation en arrière-plan : autoriser Cocorico."
        else -> null
    }
}

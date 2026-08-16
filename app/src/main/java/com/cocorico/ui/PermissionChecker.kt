package com.cocorico.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.cocorico.alarm.AlarmScheduler

data class EtatPermissions(
    val alarmesExactes: Boolean,
    val notifications: Boolean,
    val batterieExemptee: Boolean,
) {
    val toutesAccordees: Boolean
        get() = alarmesExactes && notifications && batterieExemptee
}

object PermissionChecker {

    fun etat(context: Context) = EtatPermissions(
        alarmesExactes = AlarmScheduler(context).canScheduleExact(),
        notifications = notificationsAccordees(context),
        batterieExemptee = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
    )

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

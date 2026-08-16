package com.cocorico.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.cocorico.data.AlarmConfig
import com.cocorico.ui.MainActivity
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmScheduler(private val context: Context) {

    private val manager = context.getSystemService(AlarmManager::class.java)

    /**
     * Programme la prochaine sonnerie et renvoie son instant, ou null si
     * l'alarme est désarmée ou qu'aucun jour n'est actif.
     *
     * setAlarmClock est la seule API exemptée du Doze mode : ne pas la remplacer
     * par setExactAndAllowWhileIdle, qui est throttlé à une fois par 9 minutes.
     *
     * Sur Android 12, l'utilisateur peut retirer SCHEDULE_EXACT_ALARM à tout
     * moment après l'onboarding. `setAlarmClock` lève alors une SecurityException.
     * On renvoie null au lieu de planter : l'accueil retombe sur l'onboarding,
     * qui redemande l'autorisation.
     */
    fun schedule(config: AlarmConfig): LocalDateTime? {
        if (!config.armed) {
            cancel()
            return null
        }
        if (!canScheduleExact()) return null
        val next = NextOccurrenceCalculator.next(config, LocalDateTime.now()) ?: run {
            cancel()
            return null
        }
        val epochMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return try {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(epochMillis, pendingShowIntent()),
                pendingFireIntent(),
            )
            next
        } catch (_: SecurityException) {
            null
        }
    }

    fun cancel() {
        manager.cancel(pendingFireIntent())
    }

    /**
     * Sur Android 12 seulement : au-dessus, USE_EXACT_ALARM est accordée
     * d'office aux applications de réveil ; en dessous, aucune permission n'existe.
     */
    fun canScheduleExact(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> true
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> manager.canScheduleExactAlarms()
        else -> true
    }

    private fun pendingFireIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_DECLENCHEMENT,
        Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Cible du réveil affiché dans la barre système : ouvre l'application. */
    private fun pendingShowIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_AFFICHAGE,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_DECLENCHEMENT = 1
        const val REQUEST_AFFICHAGE = 2
    }
}

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
     * Programme la prochaine sonnerie et dit ce qui s'est passé.
     *
     * setAlarmClock est la seule API exemptée du Doze mode : ne pas la remplacer
     * par setExactAndAllowWhileIdle, qui est throttlé à une fois par 9 minutes.
     *
     * Cette fonction renvoyait `null` pour cinq situations différentes, et ses
     * trois appelants jetaient ce `null`. Une permission retirée après
     * l'onboarding faisait donc disparaître l'alarme sans un mot, pendant que
     * l'accueil continuait d'annoncer l'heure du prochain réveil. Voir
     * [ResultatPlanification] : la distinction qui compte est « l'utilisateur
     * en attendait-il une », pas « une alarme a-t-elle été posée ».
     */
    fun schedule(config: AlarmConfig): ResultatPlanification {
        if (!config.armed) {
            cancel()
            return ResultatPlanification.Desarmee
        }
        if (!canScheduleExact()) return ResultatPlanification.PermissionManquante
        val next = NextOccurrenceCalculator.next(config, LocalDateTime.now()) ?: run {
            cancel()
            return ResultatPlanification.AucunJourActif
        }
        // `atZone` résolvait seul les deux jours de bascule de l'heure d'été, et
        // sonnait une heure trop tard dans le trou du printemps. Voir
        // [InstantSonnerie] : la règle est désormais écrite et testée.
        val epochMillis = InstantSonnerie.resoudre(next, ZoneId.systemDefault()).toEpochMilli()
        return try {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(epochMillis, pendingShowIntent()),
                pendingFireIntent(),
            )
            ResultatPlanification.Programmee(next)
        } catch (_: SecurityException) {
            ResultatPlanification.PermissionManquante
        } catch (_: Exception) {
            ResultatPlanification.EchecSysteme
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

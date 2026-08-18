package com.cocorico.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cocorico.ui.MainActivity

/**
 * Filet de sécurité contre le kill de l'application : une alarme à 30 s qui
 * relance le service. Réarmée en boucle tant que le défi n'est pas résolu,
 * annulée à la résolution.
 *
 * Comme [AlarmScheduler], et pour la même raison, ce filet passe par
 * `setAlarmClock`. Il utilisait auparavant `setExactAndAllowWhileIdle`, que
 * la KDoc d'[AlarmScheduler] documente elle-même comme throttlé à une fois par
 * neuf minutes en Doze : le filet était donc inopérant précisément dans le
 * scénario qui le justifie — application tuée, appareil endormi, personne pour
 * rallumer l'écran. Trente secondes annoncées, neuf minutes réelles.
 *
 * Contrepartie assumée du changement : `setAlarmClock` ne connaît que
 * l'horloge murale, là où l'ancienne programmation partait de
 * `elapsedRealtime`, monotone. Une resynchronisation de l'heure pendant les
 * trente secondes décalerait donc le secours. Sur une fenêtre aussi courte
 * l'écart est négligeable, et sortir du Doze mode vaut largement ce risque.
 */
class SecoursScheduler(private val context: Context) {

    private val manager = context.getSystemService(AlarmManager::class.java)

    /**
     * Ne lève jamais. Sur Android 12, l'utilisateur peut retirer
     * SCHEDULE_EXACT_ALARM à tout moment ; la programmation lève alors une
     * `SecurityException`. Or `armer` est appelé depuis `onStartCommand`,
     * c'est-à-dire **pendant que l'alarme sonne** : y planter le service
     * couperait la sonnerie au lieu de la protéger. Le filet se contente
     * d'être absent, ce qui est le comportement d'avant son existence.
     */
    fun armer() {
        if (!AlarmScheduler(context).canScheduleExact()) return
        val quand = System.currentTimeMillis() + DELAI_MS
        runCatching {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(quand, pendingAffichage()),
                pending(),
            )
        }
    }

    fun annuler() {
        manager.cancel(pending())
    }

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_SECOURS,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_SECOURS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * `AlarmClockInfo` exige une cible d'affichage — celle que le système ouvre
     * quand l'utilisateur touche le réveil dans la barre d'état. La même que
     * pour l'alarme principale : l'écran d'accueil, qui redirige vers l'écran
     * d'alarme tant qu'une sonnerie est en cours.
     */
    private fun pendingAffichage(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_AFFICHAGE_SECOURS,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val DELAI_MS = 30_000L
        const val REQUEST_SECOURS = 3
        const val REQUEST_AFFICHAGE_SECOURS = 4
    }
}

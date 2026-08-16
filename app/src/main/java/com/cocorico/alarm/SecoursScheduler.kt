package com.cocorico.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Filet de sécurité contre le kill de l'application : une alarme à 30 s qui
 * relance le service. Réarmée en boucle tant que le défi n'est pas résolu,
 * annulée à la résolution.
 */
class SecoursScheduler(private val context: Context) {

    private val manager = context.getSystemService(AlarmManager::class.java)

    fun armer() {
        manager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            android.os.SystemClock.elapsedRealtime() + DELAI_MS,
            pending(),
        )
    }

    fun annuler() {
        manager.cancel(pending())
    }

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_SECOURS,
        Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val DELAI_MS = 30_000L
        const val REQUEST_SECOURS = 3
    }
}

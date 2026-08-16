package com.cocorico.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Déclenché à l'heure exacte. Ne fait rien d'autre que démarrer le service :
 * un BroadcastReceiver ne dispose que de quelques secondes avant d'être tué.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Le filet de secours ne doit ressusciter qu'une alarme réellement en
        // cours. Sans ce garde-fou, un secours déjà parti au moment où
        // l'utilisateur résout son défi relancerait la sonnerie et un nouveau
        // défi après coup, sans rien pour l'arrêter.
        if (intent.action == ACTION_SECOURS && !AlarmState.estEnCours(context)) return

        AlarmState.marquerDemarree(context)
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java),
        )
    }

    companion object {
        const val ACTION_SECOURS = "com.cocorico.SECOURS"
    }
}

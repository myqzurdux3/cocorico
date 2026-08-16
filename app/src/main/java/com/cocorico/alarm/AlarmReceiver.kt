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
        AlarmState.marquerDemarree(context)
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java),
        )
    }
}

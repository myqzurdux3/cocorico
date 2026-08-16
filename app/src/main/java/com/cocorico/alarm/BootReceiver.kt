package com.cocorico.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cocorico.data.AlarmConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Deux responsabilités après un redémarrage :
 * 1. relancer immédiatement l'alarme si elle sonnait au moment de l'extinction
 *    (contre-mesure au redémarrage comme technique de contournement),
 * 2. reprogrammer la prochaine occurrence, qu'Android a oubliée.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        if (AlarmState.estEnCours(context)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java),
            )
            return
        }

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = AlarmConfigRepository(app).current()
                AlarmScheduler(app).schedule(config)
            } finally {
                pending.finish()
            }
        }
    }
}

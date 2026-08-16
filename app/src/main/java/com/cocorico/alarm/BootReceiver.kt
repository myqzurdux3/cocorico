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
 *
 * La replanification est inconditionnelle. Elle l'était autrefois seulement quand
 * aucune alarme n'était en cours : un drapeau resté à `true` après un arrêt forcé
 * suffisait alors à faire disparaître l'alarme pour toujours, pendant que
 * l'accueil continuait d'annoncer « Réveil dans 7 h 42 min ».
 *
 * Limite connue : le récepteur n'est pas `directBootAware`, et le rendre tel
 * exigerait de basculer AlarmState et le DataStore de configuration sur le
 * stockage protégé par l'appareil. Un téléphone qui redémarre la nuit et reste
 * verrouillé ne replanifie donc qu'au premier déverrouillage.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (AlarmState.estEnCours(context)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java),
            )
        } else {
            // Purge d'un drapeau périmé : un arrêt forcé pendant l'alarme le
            // laisse à `true` sans que rien ne vienne le remettre à zéro.
            AlarmState.marquerTerminee(context)
        }

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Un `try`/`finally` sans `catch` faisait planter le processus au
                // démarrage si SCHEDULE_EXACT_ALARM avait été révoquée.
                runCatching {
                    val config = AlarmConfigRepository(app).current()
                    AlarmScheduler(app).schedule(config)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

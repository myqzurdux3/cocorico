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
 * Deux responsabilités après un redémarrage — ou après une mise à jour de
 * l'application, qui efface elle aussi les alarmes programmées :
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
        // Quatre événements effacent ou périment les alarmes programmées ;
        // le filtre qui les reconnaît vit dans `ActionsReplanification`, seul
        // endroit testable sans téléphone.
        if (!ActionsReplanification.doitReplanifier(intent.action)) return

        val redemarrage = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (redemarrage && AlarmState.estEnCours(context)) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java),
            )
        } else if (redemarrage) {
            // Purge d'un drapeau périmé : un arrêt forcé pendant l'alarme le
            // laisse à `true` sans que rien ne vienne le remettre à zéro.
            AlarmState.marquerTerminee(context)
        }

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Un `try`/`finally` sans `catch` faisait planter le processus au
                // démarrage si SCHEDULE_EXACT_ALARM avait été révoquée. L'échec
                // était en revanche avalé sans trace : si le DataStore n'était
                // pas prêt au boot, l'alarme disparaissait sans le moindre
                // signal jusqu'au matin où elle ne sonnait pas.
                val resultat = runCatching {
                    val config = AlarmConfigRepository(app).current()
                    AlarmScheduler(app).schedule(config)
                }.getOrDefault(ResultatPlanification.EchecSysteme)
                if (resultat.doitAlerter) {
                    AlerteReplanification.publier(app)
                } else {
                    AlerteReplanification.retirer(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

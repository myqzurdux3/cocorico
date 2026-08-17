package com.cocorico.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Déclenché à l'heure exacte. Ne fait rien d'autre que passer la main au
 * service, verrou en main : un BroadcastReceiver ne dispose que de quelques
 * secondes avant d'être tué.
 *
 * Les deux accès à [AlarmState] passent par un fichier de préférences, lu et
 * écrit de façon **synchrone** : `getSharedPreferences` charge le fichier au
 * premier accès et `commit()` attend l'écriture disque. Les laisser sur le
 * thread principal, à l'instant exact du déclenchement, retardait d'autant le
 * démarrage du service — sur un stockage sollicité, assez pour manger la
 * fenêtre de cinq secondes du récepteur. `goAsync()` déplace les deux hors du
 * thread principal sans que la diffusion soit considérée comme terminée, comme
 * le fait déjà [BootReceiver].
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Acquis avant toute chose : le verrou système de la diffusion tombe au
        // retour d'`onReceive`, alors que le service, lui, n'a pas encore
        // démarré. Le service le relâche dès qu'il tient le sien.
        VerrouDemarrage.acquerir(context)

        val pending = goAsync()
        val app = context.applicationContext
        val action = intent.action
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Le filet de secours ne doit ressusciter qu'une alarme réellement
                // en cours. Sans ce garde-fou, un secours déjà parti au moment où
                // l'utilisateur résout son défi relancerait la sonnerie et un
                // nouveau défi après coup, sans rien pour l'arrêter.
                if (action == ACTION_SECOURS && !AlarmState.estEnCours(app)) {
                    // Rien ne démarrera : personne d'autre ne rendrait le verrou.
                    VerrouDemarrage.relacher()
                    return@launch
                }

                AlarmState.marquerDemarree(app)
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, AlarmService::class.java),
                )
            } finally {
                // La diffusion doit être close même si le démarrage échoue :
                // une diffusion jamais terminée finit par un ANR de diffusion.
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SECOURS = "com.cocorico.SECOURS"
    }
}

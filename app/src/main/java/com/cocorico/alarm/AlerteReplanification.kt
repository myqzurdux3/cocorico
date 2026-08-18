package com.cocorico.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cocorico.R
import com.cocorico.ui.MainActivity

/**
 * Prévient que la prochaine alarme n'a pas pu être programmée.
 *
 * C'est le seul cas où Cocorico ment à l'utilisateur sans le savoir : l'accueil
 * annonce « Réveil dans 7 h 42 » alors qu'aucune alarme n'est armée dans le
 * système, parce que la permission d'alarme exacte a été retirée après
 * l'onboarding ou que la programmation a échoué. Le silence dure jusqu'au matin
 * où le réveil ne sonne pas.
 *
 * Canal séparé de celui de la sonnerie : une alerte discrète, qu'on peut couper
 * sans couper l'alarme elle-même. Elle est **persistante** — pas de balayage
 * accidentel à 23 h pour un message qui décide du lendemain — mais un appui
 * ouvre l'application, qui redemande l'autorisation.
 */
object AlerteReplanification {

    fun publier(context: Context) {
        val gestionnaire = context.getSystemService(NotificationManager::class.java) ?: return
        gestionnaire.createNotificationChannel(
            NotificationChannel(
                CANAL_ID,
                "Alarme non programmée",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Prévient quand le réveil n'a pas pu être armé."
            },
        )

        val notification = Notification.Builder(context, CANAL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Le réveil n'est pas armé")
            .setContentText("L'autorisation d'alarme exacte manque. Touche ici pour la redonner.")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "Cocorico n'a pas pu programmer la prochaine sonnerie : " +
                        "l'autorisation d'alarme exacte a été retirée. " +
                        "Tant qu'elle manque, aucun réveil ne sonnera.",
                ),
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    REQUEST_ALERTE,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        // `notify` lève si POST_NOTIFICATIONS a été refusée : ne jamais laisser
        // l'alerte faire tomber le chemin qu'elle sert à surveiller.
        runCatching { gestionnaire.notify(NOTIF_ID, notification) }
    }

    /** Retirée dès qu'une alarme est de nouveau programmée. */
    fun retirer(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
        }
    }

    private const val CANAL_ID = "cocorico_alerte_planification"
    private const val NOTIF_ID = 42
    private const val REQUEST_ALERTE = 5
}

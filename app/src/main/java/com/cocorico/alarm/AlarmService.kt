package com.cocorico.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.cocorico.R
import com.cocorico.data.AlarmConfigRepository
import com.cocorico.ring.RingtonePlayer
import com.cocorico.ring.Sonneries
import com.cocorico.ui.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Porte l'alarme du déclenchement jusqu'à la résolution du défi. C'est le seul
 * composant qui survit au kill de l'application : START_STICKY le relance, et
 * l'alarme de secours le ressuscite si même ça échoue.
 */
class AlarmService : Service() {

    private lateinit var player: RingtonePlayer
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        player = RingtonePlayer(this)
        creerCanal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DEFI_RESOLU) {
            terminer()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, construireNotification())
        AlarmState.marquerDemarree(this)
        acquerirWakeLock()

        scope.launch {
            val config = AlarmConfigRepository(applicationContext).current()
            player.demarrer(Sonneries.parId(config.ringtoneId))
        }

        demarrerActivitePleinEcran()
        return START_STICKY
    }

    /**
     * La replanification est faite AVANT l'arrêt, dans la même coroutine et en
     * `NonCancellable` : `stopSelf()` déclenche `onDestroy`, qui annule le scope.
     * Lancer la replanification puis s'arrêter aussitôt la ferait perdre une fois
     * sur deux, et l'alarme ne sonnerait plus jamais après le premier réveil.
     */
    private fun terminer() {
        AlarmState.marquerTerminee(this)
        player.arreter()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        scope.launch {
            withContext(NonCancellable) {
                val repo = AlarmConfigRepository(applicationContext)
                AlarmScheduler(applicationContext).schedule(repo.current())
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun demarrerActivitePleinEcran() {
        startActivity(
            Intent(this, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
    }

    private fun acquerirWakeLock() {
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "cocorico:alarme",
        ).apply { acquire(30 * 60 * 1000L) }
    }

    private fun creerCanal() {
        val canal = NotificationChannel(
            CANAL_ID,
            "Alarme",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    private fun construireNotification(): Notification {
        val plein = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CANAL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Debout.")
            .setContentText("Y'a pas de bouton.")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(plein, true)
            .build()
    }

    override fun onDestroy() {
        // Filet : si le service meurt sans passer par terminer(), le volume
        // système est quand même restauré. arreter() est idempotent.
        player.arreter()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_DEFI_RESOLU = "com.cocorico.DEFI_RESOLU"
        private const val CANAL_ID = "cocorico_alarme"
        private const val NOTIF_ID = 1

        /** Le défi est résolu : coupe la sonnerie et replanifie. */
        fun arreter(context: Context) {
            context.startService(
                Intent(context, AlarmService::class.java).setAction(ACTION_DEFI_RESOLU),
            )
        }
    }
}

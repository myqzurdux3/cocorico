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
import com.cocorico.data.AlarmConfig
import com.cocorico.data.AlarmConfigRepository
import com.cocorico.ring.RingtonePlayer
import com.cocorico.ring.Sonneries
import com.cocorico.ui.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val secours by lazy { SecoursScheduler(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Vrai entre le premier démarrage effectif et [terminer]. Voir [onStartCommand]. */
    private var alarmeActive = false

    override fun onCreate() {
        super.onCreate()
        player = RingtonePlayer(this)
        creerCanal()
    }

    /**
     * Le filet de secours retombe sur ce service toutes les 30 s tant que le défi
     * n'est pas résolu : `onStartCommand` est donc rappelé pendant que l'alarme
     * sonne déjà. Ce chemin doit être idempotent tant que la sonnerie tourne
     * réellement : la relancer empilerait des MediaPlayer irrécupérables,
     * remettrait le volume à fond alors que l'utilisateur a le téléphone en main,
     * et écraserait le WakeLock précédent sans le relâcher. La seule exception
     * est la lecture : si elle s'est éteinte (échec de création au tout premier
     * démarrage), on la relance seule, sans toucher au WakeLock ni au volume.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DEFI_RESOLU) {
            terminer()
            return START_NOT_STICKY
        }

        // Chaque startForegroundService exige un startForeground, y compris quand
        // le service est déjà au premier plan : la notification est simplement
        // republiée à l'identique.
        startForeground(NOTIF_ID, construireNotification())

        if (alarmeActive) {
            secours.armer()
            // L'écran d'alarme est le seul composant capable d'arrêter la
            // sonnerie : s'il a été tué, il faut le ramener. En singleInstance,
            // l'instance vivante reçoit onNewIntent et ne perd pas sa progression.
            demarrerActivitePleinEcran()
            // Le tout premier démarrage a pu échouer à créer un lecteur (sonnerie
            // choisie ET repli embarqué introuvables) : sans nouvelle tentative
            // ici, `alarmeActive` resterait vrai et l'alarme serait silencieuse
            // pour le reste du passage du filet de secours. On ne relance que la
            // lecture : ni le WakeLock ni le volume ne sont retouchés.
            if (!player.estEnLecture()) {
                demarrerLecture()
            }
            return START_STICKY
        }
        alarmeActive = true

        AlarmState.marquerDemarree(this)
        acquerirWakeLock()
        secours.armer()

        demarrerLecture()

        demarrerActivitePleinEcran()
        return START_STICKY
    }

    /** Lit la config puis lance la sonnerie. Utilisé au premier démarrage et en repli. */
    private fun demarrerLecture() {
        scope.launch {
            // Une lecture de configuration qui échoue ne doit pas laisser le
            // réveil muet : on sonne avec la sonnerie par défaut.
            val config = runCatching { AlarmConfigRepository(applicationContext).current() }
                .getOrDefault(AlarmConfig.DEFAULT)
            // `runCatching` autour d'un appel suspendu avale aussi
            // CancellationException : sans cette vérification, un scope.cancel()
            // déclenché par onDestroy() pendant la lecture de la config serait
            // ignoré, et player.demarrer (pas suspendu, donc jamais interrompu)
            // démarrerait un MediaPlayer en boucle dont plus personne ne détient
            // de référence pour l'arrêter.
            currentCoroutineContext().ensureActive()
            // Posé avant de démarrer : c'est `demarrer` qui applique le volume
            // plein, et le poser après laisserait la première seconde de
            // sonnerie sortir au maximum de l'appareil — précisément ce que ce
            // réglage existe pour éviter.
            player.volumeMaxPourcent = config.volumeMaxPourcent
            player.demarrer(Sonneries.parId(config.ringtoneId))
        }
    }

    /**
     * La replanification est faite AVANT l'arrêt, dans la même coroutine et en
     * `NonCancellable` : `stopSelf()` déclenche `onDestroy`, qui annule le scope.
     * Lancer la replanification puis s'arrêter aussitôt la ferait perdre une fois
     * sur deux, et l'alarme ne sonnerait plus jamais après le premier réveil.
     *
     * Le `runCatching` est indispensable : sur Android 12, SCHEDULE_EXACT_ALARM
     * est révocable à tout moment et `schedule()` peut lever une SecurityException.
     * Sans lui, l'exception sortirait du bloc et `stopForeground` / `stopSelf` ne
     * s'exécuteraient jamais — notification d'alarme orpheline et service zombie.
     */
    private fun terminer() {
        alarmeActive = false
        AlarmState.marquerTerminee(this)
        player.arreter()
        secours.annuler()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        scope.launch {
            withContext(NonCancellable) {
                runCatching {
                    val repo = AlarmConfigRepository(applicationContext)
                    AlarmScheduler(applicationContext).schedule(repo.current())
                }
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
            // Sans contentIntent, une notification ne fait rien quand on la
            // touche : Android ne se rabat pas sur le FullScreenIntent. Dès que
            // celui-ci est dégradé en bandeau (Android 14 sans l'autorisation,
            // surcouche constructeur, application immersive au premier plan),
            // c'est le seul chemin qui reste vers l'écran de défi.
            .setContentIntent(plein)
            .build()
    }

    override fun onDestroy() {
        // Filet : si le service meurt sans passer par terminer(), le volume
        // système est quand même restauré. Le secours n'est PAS annulé ici :
        // c'est exactement le cas pour lequel il existe — service arrêté par le
        // système sous pression mémoire, défi non résolu. Après terminer(),
        // AlarmState.estEnCours est faux et AlarmReceiver refuse déjà le secours.
        alarmeActive = false
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

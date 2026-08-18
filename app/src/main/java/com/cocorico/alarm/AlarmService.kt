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
import kotlinx.coroutines.Job
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

    /**
     * Démarrage de sonnerie en vol. Le lecteur ne se déclare « en lecture »
     * qu'à la fin de la lecture asynchrone de la configuration : ce Job est la
     * seule chose qui distingue « rien ne sonne » de « rien ne sonne encore ».
     */
    private var lectureJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        player = RingtonePlayer(this)
        creerCanal()
    }

    /**
     * Le filet de secours retombe sur ce service toutes les 30 s tant que le défi
     * n'est pas résolu : `onStartCommand` est donc rappelé pendant que l'alarme
     * sonne déjà. Ce chemin doit être idempotent tant que la sonnerie tourne
     * réellement : la relancer empilerait des MediaPlayer irrécupérables et
     * remettrait le volume à fond alors que l'utilisateur a le téléphone en
     * main. La seule exception est la lecture : si elle s'est éteinte (échec de
     * création au tout premier démarrage), on la relance seule, sans toucher au
     * volume. Le WakeLock, lui, est réarmé à chaque passage : voir
     * [assurerWakeLock].
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Chaque startForegroundService exige un startForeground, y compris quand
        // le service est déjà au premier plan : la notification est simplement
        // republiée à l'identique.
        //
        // Avant tout test d'action, y compris ACTION_DEFI_RESOLU : ce chemin
        // sortait d'ici sans jamais passer au premier plan, et s'il était traité
        // pendant qu'un startForegroundService du filet de secours attendait
        // encore sa promesse, le système levait une
        // ForegroundServiceDidNotStartInTimeException — un plantage à la
        // seconde même où l'utilisateur vient de résoudre son défi.
        //
        // Cette republication est aussi le chemin de récupération de l'écran
        // d'alarme : la notification porte un full-screen intent, et le système
        // le réévalue à chaque publication.
        startForeground(NOTIF_ID, construireNotification())

        if (intent?.action == ACTION_DEFI_RESOLU) {
            terminer()
            return START_NOT_STICKY
        }

        if (alarmeActive) {
            secours.armer()
            // Réarme le verrou : le filet de secours peut faire durer la
            // sonnerie bien au-delà de sa durée initiale.
            assurerWakeLock()
            // L'écran d'alarme est le seul composant capable d'arrêter la
            // sonnerie : s'il a été tué, il faut le ramener. C'est le
            // `startForeground` ci-dessus qui s'en charge, via le full-screen
            // intent de la notification. En singleInstance, l'instance vivante
            // reçoit onNewIntent et ne perd pas sa progression.
            //
            // Le tout premier démarrage a pu échouer à créer un lecteur (sonnerie
            // choisie ET repli embarqué introuvables) : sans nouvelle tentative
            // ici, `alarmeActive` resterait vrai et l'alarme serait silencieuse
            // pour le reste du passage du filet de secours.
            val doitRelancer = RelanceLecture.doitRelancer(
                // Le lecteur n'annonce sa lecture qu'après la lecture
                // asynchrone de la configuration : sans ce premier terme, un
                // secours tombant dans cette fenêtre démarrait un second
                // MediaPlayer que plus personne ne pouvait arrêter.
                demarrageEnCours = lectureJob?.isActive == true,
                sonneEffectivement = player.estEnLecture(),
            )
            if (doitRelancer) {
                // Le volume n'est pas retouché : la machine à états l'a
                // peut-être déjà baissé parce que l'utilisateur a le téléphone
                // en main, et elle ne renotifierait jamais une baisse qu'elle
                // croit toujours en vigueur. C'est ce que ce commentaire
                // promettait déjà sans que le code le fasse.
                demarrerLecture(appliquerVolume = false)
            }
            return START_STICKY
        }
        alarmeActive = true

        AlarmState.marquerDemarree(this)
        AlarmState.marquerDeclenchement(this)
        assurerWakeLock()
        secours.armer()
        replanifierEnFond()

        demarrerLecture()

        return START_STICKY
    }

    /**
     * Lit la config puis lance la sonnerie.
     *
     * [appliquerVolume] distingue les deux appelants : le premier démarrage
     * pousse le volume à plein, le filet de secours ne relance que la lecture.
     */
    private fun demarrerLecture(appliquerVolume: Boolean = true) {
        lectureJob = scope.launch {
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
            val sonnerie = Sonneries.parId(config.ringtoneId)
            if (appliquerVolume) player.demarrer(sonnerie) else player.demarrerLecture(sonnerie)
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
                replanifierEtAlerter()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Replanifie dès le déclenchement, sans attendre la résolution du défi.
     *
     * [terminer] replanifie déjà, mais seulement si l'utilisateur va au bout.
     * Une mort du service sans résolution — arrêt forcé, tueur de tâches d'un
     * constructeur, plantage — laissait le réveil du lendemain **non
     * programmé** jusqu'à un redémarrage ou une ouverture de l'application. La
     * programmation est idempotente : la faire deux fois ne coûte rien, ne pas
     * la faire du tout coûte un réveil.
     *
     * `NonCancellable` pour la même raison que dans [terminer], et plus encore
     * ici : ce filet existe précisément pour le cas où le service est arrêté
     * sans prévenir, et `onDestroy` annule le scope. Une replanification
     * abandonnée à mi-chemin ne protégerait de rien.
     */
    private fun replanifierEnFond() {
        scope.launch { withContext(NonCancellable) { replanifierEtAlerter() } }
    }

    /**
     * Le résultat était jeté : un `null` signifiait « plus jamais d'alarme » et
     * passait inaperçu au moment le plus critique du cycle — juste après un
     * réveil réussi, quand plus rien ne repassera par ici avant le lendemain.
     */
    private suspend fun replanifierEtAlerter() {
        val resultat = runCatching {
            val repo = AlarmConfigRepository(applicationContext)
            AlarmScheduler(applicationContext).schedule(repo.current())
        }.getOrDefault(ResultatPlanification.EchecSysteme)
        if (resultat.doitAlerter) {
            AlerteReplanification.publier(applicationContext)
        } else {
            AlerteReplanification.retirer(applicationContext)
        }
    }

    /**
     * Acquiert le verrou, ou réarme son délai s'il est déjà là.
     *
     * Il était pris une fois pour trente minutes et jamais renouvelé, alors que
     * le filet de secours peut faire durer la sonnerie sans limite : passé ce
     * délai, plus rien ne retenait le CPU côté service. Chaque passage du
     * secours repasse donc ici.
     *
     * Le verrou n'est pas compté par référence : les acquisitions successives ne
     * font que repousser l'échéance, et le `release` unique de [terminer] suffit
     * toujours à le rendre.
     */
    private fun assurerWakeLock() {
        val verrou = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cocorico:alarme")
            .also { it.setReferenceCounted(false) }
            .also { wakeLock = it }
        verrou.acquire(DUREE_VERROU_MS)
        // Le relais du récepteur a rempli son rôle : le service tient désormais
        // son propre verrou. Relâché ici et pas plus tôt, pour qu'il n'existe
        // aucun instant sans verrou entre les deux composants.
        VerrouDemarrage.relacher()
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
        // Cas où le service meurt avant d'avoir acquis son propre verrou : sans
        // ça, le relais du récepteur tiendrait l'appareil éveillé jusqu'à son
        // délai d'expiration.
        VerrouDemarrage.relacher()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_DEFI_RESOLU = "com.cocorico.DEFI_RESOLU"
        private const val CANAL_ID = "cocorico_alarme"
        private const val NOTIF_ID = 1

        /**
         * Durée d'un verrou, pas de l'alarme : elle est réarmée à chaque passage
         * du filet de secours. Ce délai ne sert qu'à borner un verrou orphelin
         * si le service meurt sans passer par `onDestroy`.
         */
        private const val DUREE_VERROU_MS = 30 * 60 * 1000L

        /** Le défi est résolu : coupe la sonnerie et replanifie. */
        fun arreter(context: Context) {
            context.startService(
                Intent(context, AlarmService::class.java).setAction(ACTION_DEFI_RESOLU),
            )
        }
    }
}

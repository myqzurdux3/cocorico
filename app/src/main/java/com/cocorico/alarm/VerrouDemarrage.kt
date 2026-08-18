package com.cocorico.alarm

import android.content.Context
import android.os.PowerManager

/**
 * Relais de WakeLock entre [AlarmReceiver] et [AlarmService].
 *
 * Le verrou que le système tient pendant une diffusion tombe au retour
 * d'`onReceive`, alors que `startForegroundService` est asynchrone : le service
 * n'a pas encore acquis le sien. Entre les deux, plus rien ne retient le CPU et
 * un appareil endormi peut se rendormir aussitôt — l'alarme sonne alors avec
 * plusieurs secondes de retard, ou pas du tout.
 *
 * Le verrou est statique parce qu'il doit survivre à deux composants distincts.
 * Il n'est pas compté par référence : deux acquisitions rapprochées (l'alarme
 * puis un passage du filet de secours) ne doivent pas exiger deux relâchements,
 * sans quoi un verrou orphelin viderait la batterie jusqu'au bout.
 */
object VerrouDemarrage {

    /**
     * Large par rapport au temps de démarrage d'un service (quelques centaines
     * de millisecondes), mais borné : si le service ne démarre jamais, ce délai
     * est la seule chose qui empêche le verrou de tenir l'appareil éveillé
     * indéfiniment.
     */
    private const val DUREE_MS = 60_000L

    private var verrou: PowerManager.WakeLock? = null

    /**
     * Ne lève jamais : cette fonction est appelée à l'instant du déclenchement,
     * et une exception ici tuerait le récepteur avant qu'il ait démarré le
     * service — c'est-à-dire l'alarme entière, pour un confort de batterie.
     */
    @Synchronized
    fun acquerir(context: Context) {
        runCatching {
            val existant = verrou ?: context.applicationContext
                .getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cocorico:demarrage")
                .also { it.setReferenceCounted(false) }
                .also { verrou = it }
            existant.acquire(DUREE_MS)
        }
    }

    /** Appelée par le service dès qu'il tient son propre verrou. Idempotente. */
    @Synchronized
    fun relacher() {
        runCatching { verrou?.takeIf { it.isHeld }?.release() }
    }
}

package com.cocorico.alarm

/**
 * Décide, à partir de la seule action reçue, si le réveil doit être
 * reprogrammé. Classe pure, sans import `android.*` : c'est ce qui la rend
 * testable, et ce filtre mérite un test parce qu'une erreur ici ne se voit
 * qu'un matin où l'alarme ne sonne pas.
 *
 * Les quatre actions sont écrites en toutes lettres plutôt que reprises de
 * `Intent` : ce sont des constantes de plateforme gelées, et les recopier est
 * le prix à payer pour garder ce fichier hors d'Android. Le manifeste doit
 * déclarer exactement les mêmes.
 *
 * Pourquoi l'heure et le fuseau comptent autant que le démarrage :
 * `setAlarmClock` mémorise un **instant absolu**, calculé une fois pour
 * toutes à partir de l'heure murale choisie. Un vol vers un autre fuseau, ou
 * un simple réglage manuel de l'horloge, laisse cet instant intact alors que
 * l'heure murale visée a changé — le réveil sonne alors au mauvais moment, ou
 * saute son tour. La replanification de [BootReceiver] étant idempotente, la
 * rejouer sur ces deux événements ne coûte rien et remet l'instant en face de
 * l'heure demandée.
 */
object ActionsReplanification {

    private val ACTIONS = setOf(
        "android.intent.action.BOOT_COMPLETED",
        // Android annule les alarmes d'une application quand on la met à jour.
        "android.intent.action.MY_PACKAGE_REPLACED",
        "android.intent.action.TIMEZONE_CHANGED",
        "android.intent.action.TIME_SET",
    )

    /**
     * Le récepteur est exporté : n'importe quelle application peut le réveiller
     * avec n'importe quelle action. Tout ce qui n'est pas dans la liste est
     * ignoré — replanifier sur commande étrangère déplacerait l'alarme sans que
     * l'utilisateur l'ait demandé.
     */
    fun doitReplanifier(action: String?): Boolean = action in ACTIONS
}

package com.cocorico.alarm

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Décide si une sonnerie attendue a été manquée, et comment le dire.
 *
 * Le réveil savait programmer une alarme, mais pas constater qu'elle n'était
 * jamais partie : téléphone éteint à l'heure dite, processus tué, permission
 * d'alarme exacte retirée après l'onboarding — dans tous ces cas l'utilisateur
 * ne l'apprenait qu'en ne se réveillant pas. Le mécanisme tient en une donnée :
 * l'instant attendu, écrit à la programmation et effacé par le service quand la
 * sonnerie part réellement. Une attente passée et jamais effacée est un échec.
 *
 * Pur, sans import `android.*`. Ce qu'il décide se lit sur l'écran d'accueil et
 * accuse le réveil d'avoir failli : ça se vérifie sans téléphone, donc ça doit
 * l'être.
 *
 * **Ce que ce garde-fou ne couvre pas** : si l'application disparaît du
 * téléphone, l'attente disparaît avec elle. Aucun code embarqué ne peut
 * signaler sa propre absence.
 */
object AttenteSonnerie {

    /**
     * Délai au-delà duquel une attente non honorée compte comme manquée.
     *
     * Entre le déclenchement par `AlarmManager` et l'instant où le service
     * marque la sonnerie, il s'écoule un court délai — démarrage du processus,
     * création du service. Conclure avant, c'est risquer d'annoncer un échec
     * pendant que l'alarme est en train de partir. Deux minutes laissent une
     * marge très large devant ce délai, tout en restant sans commune mesure
     * avec l'écart qui sépare deux réveils : aucun vrai manquement ne passe au
     * travers.
     */
    const val MARGE_MS = 120_000L

    /**
     * [attendue] est l'instant de la sonnerie attendue, ou `0` si aucune n'est
     * en attente. Une horloge qui recule — fuseau, correction réseau — rend
     * simplement `false` : le cas n'est pas « pas de manquement », c'est
     * « on ne conclut pas », et ne rien annoncer est la bonne réponse aux deux.
     */
    fun estManquee(attendue: Long, maintenant: Long): Boolean = attendue > 0L && maintenant - attendue >= MARGE_MS

    /**
     * Le jour est nommé plutôt que daté en chiffres : « mardi 18 août » se
     * reconnaît d'un coup d'œil au réveil, « 18/08 » demande de réfléchir.
     */
    fun libelle(quand: LocalDateTime): String = "Le réveil de ${quand.format(FORMAT)} n'a pas sonné."

    private val FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH:mm", Locale.FRENCH)
}

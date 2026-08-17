package com.cocorico.alarm

/**
 * Décide si le filet de secours doit relancer la sonnerie.
 *
 * Le service ne regardait que « le lecteur joue-t-il ? ». Ce booléen est faux
 * pendant toute la lecture asynchrone de la configuration : un secours qui
 * tombe dans cette fenêtre — elle s'ouvre au déclenchement, exactement quand le
 * secours est le plus susceptible d'arriver — lançait une **seconde** lecture.
 * Deux MediaPlayer sonnaient alors ensemble, et `arreter()` n'en connaissait
 * qu'un : le défi résolu ne coupait pas le bruit.
 *
 * Classe pure, sans import `android.*` : la course est une décision, pas un
 * appel système, et elle se teste sans téléphone.
 */
object RelanceLecture {

    /**
     * @param demarrageEnCours une coroutine de démarrage n'a pas encore fini.
     * @param sonneEffectivement le lecteur joue déjà.
     */
    fun doitRelancer(demarrageEnCours: Boolean, sonneEffectivement: Boolean): Boolean =
        !demarrageEnCours && !sonneEffectivement
}

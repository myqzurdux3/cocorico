package com.cocorico.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Le retour de tous les écrans de réglages : statistiques, défi, sonnerie,
 * sélection d'objets, essai photo.
 *
 * Cinq écrans posaient chacun leur `Text` cliquable nu. Une cible tactile large
 * de six caractères et haute de la seule ligne de texte — une vingtaine de dp,
 * moins de la moitié du minimum utilisable — sur le seul chemin de sortie de
 * l'écran : un appui manqué laisse l'utilisateur coincé dans un réglage. Un
 * composable unique plutôt que cinq corrections parallèles, parce que c'est une
 * duplication réelle et que la sixième copie serait écrite pareil.
 *
 * La hauteur minimale est obtenue par la marge intérieure et [heightIn] : la
 * marge élargit aussi la cible horizontalement, ce que `size` n'aurait pas
 * fait, et le texte reste centré dans sa hauteur.
 */
@Composable
fun BoutonRetour(onRetour: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = "‹ Retour",
        fontSize = 16.sp,
        modifier = modifier
            .heightIn(min = HAUTEUR_CIBLE_TACTILE)
            // Le clic vient avant la marge : la zone cliquable englobe donc la
            // marge au lieu de s'arrêter au texte.
            .clickable(onClick = onRetour)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .wrapContentHeight(),
    )
}

/**
 * Minimum d'une cible tactile, commun aux recommandations d'accessibilité
 * d'Android et de Material. En dessous, l'appui rate régulièrement — et sur ces
 * écrans, l'appui qui rate est celui qui fait sortir.
 */
val HAUTEUR_CIBLE_TACTILE = 48.dp

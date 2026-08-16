package com.cocorico.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Marge de sécurité pour le contenu d'un écran, barres système comprises
 * (statut, navigation, encoche, poignée gestuelle).
 *
 * Depuis le `targetSdk 35`, Android impose le bord-à-bord : la fenêtre occupe
 * tout l'écran et rien ne décale plus le contenu. Sans cette marge, le titre
 * passait sous l'horloge de la barre de statut et — bien plus grave — la
 * dernière rangée du pavé numérique passait sous la barre de navigation
 * gestuelle : touche de validation inatteignable, donc alarme inarrêtable.
 *
 * À poser **après** la couleur de fond et **avant** la marge visuelle de
 * l'écran : les modificateurs s'appliquent de l'extérieur vers l'intérieur, si
 * bien que le fond continue de couvrir toute la surface physique — bord-à-bord,
 * comme voulu — tandis que le contenu se range dans la zone sûre.
 *
 * ```
 * Modifier.fillMaxSize().background(couleur).zoneSure().padding(24.dp)
 * ```
 */
@Composable
fun Modifier.zoneSure(): Modifier = windowInsetsPadding(WindowInsets.safeDrawing)

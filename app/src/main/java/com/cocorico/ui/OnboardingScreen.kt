package com.cocorico.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocorico.data.ChallengeId

/**
 * [challengeId] ne sert qu'à décider si la carte caméra doit apparaître :
 * elle ne concerne que le défi photo, jamais les calculs ni les pompes (voir
 * la KDoc d'[EtatPermissions.camera]).
 */
@Composable
fun OnboardingScreen(etat: EtatPermissions, challengeId: ChallengeId, onRafraichir: () -> Unit) {
    val context = LocalContext.current
    val demanderCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onRafraichir() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .zoneSure()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Avant de commencer", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Sans ces autorisations, l'alarme peut rater. Ce serait dommage.",
            fontSize = 15.sp,
        )

        if (!etat.alarmesExactes) {
            Exigence(
                titre = "Alarmes exactes",
                detail = "Autorise Cocorico à sonner à la seconde près.",
                action = "Autoriser",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ouvrirPremierDisponible(
                            context,
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
                            ficheApplication(context),
                        )
                    }
                },
            )
        }

        if (!etat.notifications) {
            Exigence(
                titre = "Notifications",
                detail = "Nécessaires pour afficher l'alarme par-dessus l'écran verrouillé.",
                action = "Autoriser",
                onClick = {
                    ouvrirPremierDisponible(
                        context,
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        ficheApplication(context),
                    )
                },
            )
        }

        if (!etat.pleinEcran) {
            Exigence(
                titre = "Alarme en plein écran",
                detail = "Sans ça, le réveil se contente d'une notification " +
                    "derrière l'écran verrouillé. Tu dormirais encore.",
                action = "Autoriser",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ouvrirPremierDisponible(
                            context,
                            Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:${context.packageName}"),
                            ),
                            ficheApplication(context),
                        )
                    }
                },
            )
        }

        if (!etat.batterieExemptee) {
            Exigence(
                titre = "Optimisation de la batterie",
                detail = Constructeurs.reglageBatterie(Build.MANUFACTURER)
                    ?: "Autorise Cocorico à rester actif pendant la nuit.",
                action = "Ouvrir les réglages",
                onClick = {
                    ouvrirPremierDisponible(
                        context,
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                        ficheApplication(context),
                    )
                },
            )
        }

        // Uniquement quand la photo est le défi choisi : la réclamer à
        // quelqu'un qui a choisi le calcul mental serait injustifiable. Un
        // refus n'est pas bloquant — le défi retombe sur les calculs, voir
        // `AlarmActivity.construireDefi` — donc cette carte ne fait jamais
        // partie d'[EtatPermissions.toutesAccordees] et ne peut pas bloquer
        // cet écran.
        if (challengeId == ChallengeId.PHOTO && !etat.camera) {
            Exigence(
                titre = "Appareil photo",
                detail = "Nécessaire pour valider le défi photo. Sans elle, " +
                    "l'alarme retombe sur les calculs.",
                action = "Autoriser",
                onClick = { demanderCamera.launch(Manifest.permission.CAMERA) },
            )
        }

        Button(onClick = onRafraichir, modifier = Modifier.fillMaxWidth()) {
            Text("J'ai tout autorisé", fontSize = 17.sp)
        }
    }
}

/**
 * Certaines surcouches constructeur ne déclarent pas l'écran de réglages visé.
 * Un `startActivity` nu y lève `ActivityNotFoundException` et fait planter
 * l'application sur son tout premier écran. On essaie donc chaque cible dans
 * l'ordre, en terminant par la fiche de l'application, toujours présente.
 */
private fun ouvrirPremierDisponible(context: Context, vararg cibles: Intent) {
    for (cible in cibles) {
        if (runCatching { context.startActivity(cible) }.isSuccess) return
    }
}

private fun ficheApplication(context: Context) = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.parse("package:${context.packageName}"),
)

@Composable
private fun Exigence(titre: String, detail: String, action: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(titre, fontSize = 17.sp)
        Text(detail, fontSize = 15.sp)
        Button(onClick = onClick) { Text(action) }
    }
}

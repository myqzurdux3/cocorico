package com.cocorico.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

@Composable
fun OnboardingScreen(etat: EtatPermissions, onRafraichir: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
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
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
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
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
            )
        }

        Button(onClick = onRafraichir, modifier = Modifier.fillMaxWidth()) {
            Text("J'ai tout autorisé", fontSize = 17.sp)
        }
    }
}

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

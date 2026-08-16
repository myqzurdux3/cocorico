package com.cocorico.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cocorico.ui.theme.CocoricoTheme

private enum class Ecran { ACCUEIL, DEFI, SONNERIE, VICTOIRE }

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    /**
     * L'activité est en `singleTop` : si elle tourne déjà quand le défi est
     * résolu, Android appelle `onNewIntent` et non `onCreate`. Sans cet état
     * observable, l'écran de victoire ne s'afficherait jamais pour un
     * utilisateur qui a laissé l'application ouverte la veille — le cas courant.
     */
    private val victoire = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        victoire.value = intent.getBooleanExtra(EXTRA_VICTOIRE, false)

        setContent {
            CocoricoTheme {
                var etat by remember { mutableStateOf(PermissionChecker.etat(this)) }
                if (!etat.toutesAccordees) {
                    OnboardingScreen(etat) { etat = PermissionChecker.etat(this) }
                    return@CocoricoTheme
                }
                var ecran by remember {
                    mutableStateOf(if (victoire.value) Ecran.VICTOIRE else Ecran.ACCUEIL)
                }
                LaunchedEffect(victoire.value) {
                    if (victoire.value) ecran = Ecran.VICTOIRE
                }
                when (ecran) {
                    Ecran.ACCUEIL -> HomeScreen(
                        viewModel = viewModel,
                        onOuvrirDefi = { ecran = Ecran.DEFI },
                        onOuvrirSonnerie = { ecran = Ecran.SONNERIE },
                        onChoisirHeure = { ouvrirSelecteurHeure() },
                    )
                    Ecran.DEFI -> ChallengeSettingsScreen(viewModel) { ecran = Ecran.ACCUEIL }
                    Ecran.SONNERIE -> RingtoneScreen(viewModel) { ecran = Ecran.ACCUEIL }
                    Ecran.VICTOIRE -> VictoryScreen {
                        victoire.value = false
                        ecran = Ecran.ACCUEIL
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        victoire.value = intent.getBooleanExtra(EXTRA_VICTOIRE, false)
    }

    private fun ouvrirSelecteurHeure() {
        val config = viewModel.config.value
        TimePickerDialog(
            this,
            { _, heure, minute -> viewModel.majHeure(heure, minute) },
            config.hour,
            config.minute,
            true,
        ).show()
    }

    companion object {
        const val EXTRA_VICTOIRE = "com.cocorico.EXTRA_VICTOIRE"
    }
}

package com.cocorico.ui

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cocorico.ui.theme.CocoricoTheme

private enum class Ecran { ACCUEIL, DEFI, SONNERIE, VICTOIRE }

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val victoire = intent.getBooleanExtra(EXTRA_VICTOIRE, false)

        setContent {
            CocoricoTheme {
                var ecran by remember {
                    mutableStateOf(if (victoire) Ecran.VICTOIRE else Ecran.ACCUEIL)
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
                    Ecran.VICTOIRE -> VictoryScreen { ecran = Ecran.ACCUEIL }
                }
            }
        }
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

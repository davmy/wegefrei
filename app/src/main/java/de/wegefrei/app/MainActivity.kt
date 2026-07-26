package de.wegefrei.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import de.wegefrei.app.core.designsystem.WegefreiTheme
import de.wegefrei.app.feature.photocapture.api.PhotoCaptureRoute
import de.wegefrei.app.feature.photocapture.impl.photoCaptureScreen
import de.wegefrei.app.feature.witness.api.WitnessDetailsRoute
import de.wegefrei.app.feature.witness.impl.witnessDetailsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WegefreiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WegefreiNavHost()
                }
            }
        }
    }
}

@Composable
private fun WegefreiNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = PhotoCaptureRoute) {
        photoCaptureScreen(
            onOpenWitnessDetailsRequested = { navController.navigate(WitnessDetailsRoute) },
        )
        witnessDetailsScreen(
            onBackRequested = { navController.navigateUp() },
        )
    }
}

package de.wegefrei.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import de.wegefrei.app.core.designsystem.WegefreiTheme
import de.wegefrei.app.feature.photocapture.api.PhotoCaptureRoute
import de.wegefrei.app.feature.photocapture.impl.EmailAttachmentPreparer
import de.wegefrei.app.feature.photocapture.impl.emailAttachmentPreparer
import de.wegefrei.app.feature.photocapture.impl.photoCaptureScreen
import de.wegefrei.app.feature.witness.api.WitnessDetailsRoute
import de.wegefrei.app.feature.witness.impl.areWitnessDetailsComplete
import de.wegefrei.app.feature.witness.impl.readWitnessDetails
import de.wegefrei.app.feature.witness.impl.witnessDetailsScreen
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val attachmentPreparer: EmailAttachmentPreparer = remember { emailAttachmentPreparer(context.applicationContext) }

    NavHost(navController = navController, startDestination = PhotoCaptureRoute) {
        photoCaptureScreen(
            onOpenWitnessDetailsRequested = {
                navController.navigate(WitnessDetailsRoute) { launchSingleTop = true }
            },
            onWeiterRequested = { reportDetails ->
                coroutineScope.launch {
                    if (!areWitnessDetailsComplete(context)) {
                        navController.navigate(WitnessDetailsRoute) { launchSingleTop = true }
                        return@launch
                    }

                    val witnessDetails = readWitnessDetails(context)
                    val attachmentUris = attachmentPreparer.prepareAttachments(reportDetails.photoUris)
                    val subject = buildReportEmailSubject()
                    val body = buildReportEmailBody(witnessDetails, reportDetails)
                    val intent =
                        buildReportEmailIntent(subject, body, attachmentUris, witnessDetails.authorityEmail)

                    try {
                        context.startActivity(
                            Intent.createChooser(intent, "E-Mail senden"),
                        )
                    } catch (e: ActivityNotFoundException) {
                        // No email app installed — silent no-op, consistent with this app's
                        // existing style for failure paths with no dedicated error UI yet.
                    }
                }
            },
        )
        witnessDetailsScreen(
            onBackRequested = { navController.navigateUp() },
        )
    }
}

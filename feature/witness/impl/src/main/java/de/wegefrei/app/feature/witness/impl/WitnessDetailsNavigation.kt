package de.wegefrei.app.feature.witness.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.wegefrei.app.feature.witness.api.WitnessDetailsRoute

fun NavGraphBuilder.witnessDetailsScreen(onBackRequested: () -> Unit) {
    composable<WitnessDetailsRoute> {
        WitnessDetailsRoot(onBackRequested = onBackRequested)
    }
}

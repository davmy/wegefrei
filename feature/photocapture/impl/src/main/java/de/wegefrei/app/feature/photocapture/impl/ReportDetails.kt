package de.wegefrei.app.feature.photocapture.impl

import android.net.Uri
import java.time.LocalDateTime

data class ReportDetails(
    val licensePlate: String,
    val make: String,
    val color: String,
    val address: String,
    val incidentDateTime: LocalDateTime,
    val violation: String,
    val obstruction: String,
    val photoUris: List<Uri>,
)

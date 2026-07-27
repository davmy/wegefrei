package de.wegefrei.app.feature.photocapture.impl

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Lists can contain the same photo more than once (picked or captured twice), so the
// index must be part of the key — using uri.toString() alone crashes LazyRow with a
// duplicate-key error.
internal fun photoThumbnailKey(index: Int, uri: Uri): String = "$index-$uri"

internal val germanCarBrands = listOf(
    "Volkswagen", "Mercedes-Benz", "BMW", "Audi", "Opel", "Škoda", "Ford",
    "Seat", "Renault", "Hyundai", "Kia", "Toyota", "Peugeot", "Fiat",
    "Volvo", "Mini", "Citroën", "Dacia", "Nissan", "Mazda", "Porsche",
    "Smart", "Honda", "Suzuki", "Tesla",
)

internal val germanCarColors = listOf(
    "Schwarz", "Weiß", "Silber", "Grau", "Blau", "Rot", "Braun", "Grün",
    "Beige", "Gelb", "Orange", "Violett", "Gold", "Bronze",
)

internal val germanTrafficViolations = listOf(
    "Parken auf Gehweg",
    "Parken im absoluten Halteverbot",
    "Parken weniger als 5 Meter von Kreuzung",
    "Parken weniger als 5 Meter von Einmündung",
    "Parken auf Radweg (Zeichen 237)",
    "Parken auf Radfahrstreifen",
    "Parken auf Geh- und Radweg (Zeichen 240/241)",
    "Parken auf Sperrfläche",
    "Parken auf unbeschildertem Radweg",
    "Parken in verkehrsberuhigten Bereich (Zeichen 325.1)",
    "Parken in eingeschränktem Halteverbot (Zeichen 286)",
)

internal fun filterOptions(query: String, options: List<String>): List<String> =
    options.filter { it.contains(query, ignoreCase = true) }

internal fun trafficViolationOptions(parkOrHalt: String): List<String> =
    germanTrafficViolations.map { it.replaceFirst("Parken", parkOrHalt) }

@Composable
internal fun PhotoCaptureRoot(
    viewModel: PhotoCaptureViewModel = viewModel(),
    onOpenWitnessDetailsRequested: () -> Unit,
    onWeiterRequested: (ReportDetails) -> Unit,
) {
    val context = LocalContext.current
    val photoUris by viewModel.photoUris.collectAsState()
    val addressText by viewModel.addressText.collectAsState()
    val licensePlateText by viewModel.licensePlateText.collectAsState()
    val makeText by viewModel.makeText.collectAsState()
    val colorText by viewModel.colorText.collectAsState()
    val parkOrHaltText by viewModel.parkOrHaltText.collectAsState()
    val violationText by viewModel.violationText.collectAsState()
    val obstructionText by viewModel.obstructionText.collectAsState()
    val durationOver60MinutesText by viewModel.durationOver60MinutesText.collectAsState()
    val incidentDateTime by viewModel.incidentDateTime.collectAsState()
    var showCamera by remember { mutableStateOf(false) }
    var isLookingUpAddressFromPhoto by remember { mutableStateOf(false) }
    var isLookingUpAddressFromLocation by remember { mutableStateOf(false) }

    val locationExtractor = remember { ExifPhotoLocationExtractor(context) }
    val addressLookupService = remember { NominatimAddressLookupService() }
    val currentLocationProvider = remember { AndroidCurrentLocationProvider(context) }
    val timestampExtractor = remember { ExifPhotoTimestampExtractor(context) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(photoUris.firstOrNull()) {
        val firstUri = photoUris.firstOrNull()
        if (firstUri == null) {
            isLookingUpAddressFromPhoto = false
            return@LaunchedEffect
        }
        lookupAndReportAddress(
            setLoading = { isLookingUpAddressFromPhoto = it },
            onAddressFound = viewModel::onAddressAutoDetected,
            fetchLatLng = { locationExtractor.extractLocation(firstUri) },
            addressLookupService = addressLookupService,
        )
    }

    LaunchedEffect(photoUris) {
        val timestamps = photoUris.mapNotNull { uri -> timestampExtractor.extractTimestamp(uri) }
        viewModel.onPhotoTimestampsExtracted(timestamps)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                coroutineScope.launch {
                    lookupAndReportAddress(
                        setLoading = { isLookingUpAddressFromLocation = it },
                        onAddressFound = viewModel::onCurrentLocationAddressReceived,
                        fetchLatLng = { currentLocationProvider.getCurrentLocation() },
                        addressLookupService = addressLookupService,
                    )
                }
            }
        },
    )

    if (showCamera) {
        CameraCaptureScreen(
            onPhotoCaptured = { uri ->
                viewModel.onPhotoCaptured(uri)
                showCamera = false
            },
        )
    } else {
        PhotoCaptureScreen(
            photoUris = photoUris,
            onImagesPicked = viewModel::onImagesPicked,
            onTakePhotoRequested = { showCamera = true },
            onPhotoRemoved = viewModel::onPhotoRemoved,
            licensePlateText = licensePlateText,
            onLicensePlateTextChanged = viewModel::onLicensePlateTextChanged,
            makeText = makeText,
            onMakeTextChanged = viewModel::onMakeTextChanged,
            colorText = colorText,
            onColorTextChanged = viewModel::onColorTextChanged,
            parkOrHaltText = parkOrHaltText,
            onParkOrHaltTextChanged = viewModel::onParkOrHaltTextChanged,
            violationText = violationText,
            onViolationTextChanged = viewModel::onViolationTextChanged,
            obstructionText = obstructionText,
            onObstructionTextChanged = viewModel::onObstructionTextChanged,
            durationOver60MinutesText = durationOver60MinutesText,
            onDurationOver60MinutesTextChanged = viewModel::onDurationOver60MinutesTextChanged,
            incidentDateTime = incidentDateTime,
            onIncidentDateTimeChanged = viewModel::onIncidentDateTimeChanged,
            addressText = addressText,
            onAddressTextChanged = viewModel::onAddressTextChanged,
            isLookingUpAddress = isLookingUpAddressFromPhoto || isLookingUpAddressFromLocation,
            onUseCurrentLocationRequested = {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
            onOpenWitnessDetailsRequested = onOpenWitnessDetailsRequested,
            onWeiterRequested = onWeiterRequested,
        )
    }
}

// Shared by the EXIF-based and current-location-based lookup chains: fetches a LatLng,
// reverse-geocodes it, and reports the address. Every failure path (other than
// cancellation, which must propagate for structured concurrency) resolves to a silent
// no-op per spec, and the loading flag is always cleared.
private suspend fun lookupAndReportAddress(
    setLoading: (Boolean) -> Unit,
    onAddressFound: (String) -> Unit,
    fetchLatLng: suspend () -> LatLng?,
    addressLookupService: AddressLookupService,
) {
    setLoading(true)
    try {
        val latLng = fetchLatLng()
        if (latLng != null) {
            val address = addressLookupService.reverseGeocode(latLng.latitude, latLng.longitude)
            if (address != null) {
                onAddressFound(address)
            }
        }
    } catch (c: CancellationException) {
        throw c
    } catch (e: Exception) {
        // Silent failure per spec — every lookup failure path resolves to no-op.
    } finally {
        setLoading(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoCaptureScreen(
    photoUris: List<Uri>,
    onImagesPicked: (List<Uri>) -> Unit,
    onTakePhotoRequested: () -> Unit,
    onPhotoRemoved: (Int) -> Unit,
    licensePlateText: String,
    onLicensePlateTextChanged: (String) -> Unit,
    makeText: String,
    onMakeTextChanged: (String) -> Unit,
    colorText: String,
    onColorTextChanged: (String) -> Unit,
    parkOrHaltText: String,
    onParkOrHaltTextChanged: (String) -> Unit,
    violationText: String,
    onViolationTextChanged: (String) -> Unit,
    obstructionText: String,
    onObstructionTextChanged: (String) -> Unit,
    durationOver60MinutesText: String,
    onDurationOver60MinutesTextChanged: (String) -> Unit,
    incidentDateTime: LocalDateTime,
    onIncidentDateTimeChanged: (LocalDateTime) -> Unit,
    addressText: String,
    onAddressTextChanged: (String) -> Unit,
    isLookingUpAddress: Boolean,
    onUseCurrentLocationRequested: () -> Unit,
    onOpenWitnessDetailsRequested: () -> Unit,
    onWeiterRequested: (ReportDetails) -> Unit,
) {
    val remainingSlots = MAX_PHOTOS - photoUris.size
    val canAddMore = remainingSlots > 0
    var previewUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = remainingSlots.coerceAtLeast(2)),
        onResult = onImagesPicked,
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> if (granted) onTakePhotoRequested() },
    )

    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Falschparker melden") },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menü")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "Meine Angaben") },
                            onClick = {
                                showMenu = false
                                onOpenWitnessDetailsRequested()
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Beweisfotos",
                style = MaterialTheme.typography.titleLarge,
            )

            Text(text = "${photoUris.size} / $MAX_PHOTOS Fotos")

            if (photoUris.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(
                        items = photoUris,
                        key = { index, uri -> photoThumbnailKey(index, uri) },
                    ) { index, uri ->
                        PhotoThumbnail(
                            uri = uri,
                            onClick = { previewUri = uri },
                            onRemove = { onPhotoRemoved(index) },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = canAddMore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Aus Galerie wählen")
            }

            Button(
                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                enabled = canAddMore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Foto aufnehmen")
            }

            Text(
                text = "Fahrzeug",
                style = MaterialTheme.typography.titleLarge,
            )

            RequiredTextField(
                value = licensePlateText,
                onValueChange = onLicensePlateTextChanged,
                label = "Kennzeichen",
            )

            RequiredOptionDropdownField(
                value = makeText,
                onValueChange = onMakeTextChanged,
                label = "Marke",
                options = germanCarBrands,
            )

            RequiredOptionDropdownField(
                value = colorText,
                onValueChange = onColorTextChanged,
                label = "Farbe",
                options = germanCarColors,
            )

            Text(
                text = "Tatzeitpunkt",
                style = MaterialTheme.typography.titleLarge,
            )

            IncidentDateTimePicker(
                incidentDateTime = incidentDateTime,
                onIncidentDateTimeChanged = onIncidentDateTimeChanged,
            )

            Text(
                text = "Tatort",
                style = MaterialTheme.typography.titleLarge,
            )

            RequiredTextField(
                value = addressText,
                onValueChange = onAddressTextChanged,
                label = "Tatort",
            )

            Text(
                text = "Für die Adresssuche wird der Standort an OpenStreetMap (Nominatim) übermittelt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isLookingUpAddress) {
                CircularProgressIndicator()
            }

            Button(
                onClick = onUseCurrentLocationRequested,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Aktuellen Standort verwenden")
            }

            Text(
                text = "Verstoß",
                style = MaterialTheme.typography.titleLarge,
            )

            RequiredSwitchField(
                value = parkOrHaltText,
                onValueChange = onParkOrHaltTextChanged,
                label = parkOrHaltText,
                onLabel = "Parken",
                offLabel = "Halten",
            )

            RequiredOptionDropdownField(
                value = violationText,
                onValueChange = onViolationTextChanged,
                label = "Verstoß",
                options = trafficViolationOptions(parkOrHaltText),
            )

            RequiredSwitchField(
                value = obstructionText,
                onValueChange = onObstructionTextChanged,
                label = "Behinderung",
                onLabel = "Ja",
                offLabel = "Nein",
            )

            RequiredSwitchField(
                value = durationOver60MinutesText,
                onValueChange = onDurationOver60MinutesTextChanged,
                label = "Mehr als 60 Minuten",
                onLabel = "Ja",
                offLabel = "Nein",
            )

            Button(
                onClick = {
                    onWeiterRequested(
                        ReportDetails(
                            licensePlate = licensePlateText,
                            make = makeText,
                            color = colorText,
                            address = addressText,
                            incidentDateTime = incidentDateTime,
                            violation = violationText,
                            obstruction = obstructionText,
                            durationOver60Minutes = durationOver60MinutesText,
                            photoUris = photoUris,
                        ),
                    )
                },
                enabled = licensePlateText.isNotBlank() && makeText.isNotBlank() && colorText.isNotBlank() &&
                    violationText.isNotBlank() && obstructionText.isNotBlank() && addressText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Weiter")
            }
        }
    }

    val previewedUri = previewUri
    if (previewedUri != null) {
        PhotoPreviewDialog(uri = previewedUri, onDismiss = { previewUri = null })
    }
}

private val INCIDENT_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncidentDateTimePicker(
    incidentDateTime: LocalDateTime,
    onIncidentDateTimeChanged: (LocalDateTime) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }

    Button(
        onClick = { showDatePicker = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = incidentDateTime.format(INCIDENT_DATE_TIME_FORMATTER))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = incidentDateTime.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            pendingDate = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            showDatePicker = false
                            showTimePicker = true
                        } else {
                            showDatePicker = false
                        }
                    },
                ) {
                    Text(text = "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = "Abbrechen")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val datePendingTime = pendingDate
    if (showTimePicker && datePendingTime != null) {
        val timePickerState = rememberTimePickerState(
            initialHour = incidentDateTime.hour,
            initialMinute = incidentDateTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIncidentDateTimeChanged(
                            datePendingTime.atTime(timePickerState.hour, timePickerState.minute),
                        )
                        showTimePicker = false
                    },
                ) {
                    Text(text = "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(text = "Abbrechen")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            },
        )
    }
}

@Composable
private fun RequiredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var wasFocused by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    val isError = touched && value.isBlank()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = "$label *") },
        isError = isError,
        supportingText = if (isError) {
            { Text(text = "Pflichtfeld") }
        } else {
            null
        },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    wasFocused = true
                } else if (wasFocused) {
                    touched = true
                }
            },
    )
}

@Composable
private fun RequiredSwitchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onLabel: String,
    offLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = value == onLabel,
            onCheckedChange = { checked -> onValueChange(if (checked) onLabel else offLabel) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequiredOptionDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier,
) {
    var wasFocused by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val isError = touched && value.isBlank()
    val filteredOptions = remember(value, options) {
        filterOptions(value, options)
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredOptions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(text = "$label *") },
            isError = isError,
            supportingText = if (isError) {
                { Text(text = "Pflichtfeld") }
            } else {
                null
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                    modifier = Modifier.menuAnchor(MenuAnchorType.SecondaryEditable),
                )
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        wasFocused = true
                        expanded = true
                    } else if (wasFocused) {
                        touched = true
                        expanded = false
                    }
                },
        )

        ExposedDropdownMenu(
            expanded = expanded && filteredOptions.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filteredOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(
    uri: Uri,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.size(96.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = "Foto",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "×",
                color = Color.White,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun PhotoPreviewDialog(
    uri: Uri,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Fotovorschau",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
    }
}

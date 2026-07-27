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
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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

private val germanCarBrands = listOf(
    "Volkswagen", "Mercedes-Benz", "BMW", "Audi", "Opel", "Škoda", "Ford",
    "Seat", "Renault", "Hyundai", "Kia", "Toyota", "Peugeot", "Fiat",
    "Volvo", "Mini", "Citroën", "Dacia", "Nissan", "Mazda", "Porsche",
    "Smart", "Honda", "Suzuki", "Tesla",
)

@Composable
internal fun PhotoCaptureRoot(
    viewModel: PhotoCaptureViewModel = viewModel(),
    onOpenWitnessDetailsRequested: () -> Unit,
    onWeiterRequested: () -> Unit,
) {
    val context = LocalContext.current
    val photoUris by viewModel.photoUris.collectAsState()
    val addressText by viewModel.addressText.collectAsState()
    val licensePlateText by viewModel.licensePlateText.collectAsState()
    val makeText by viewModel.makeText.collectAsState()
    val colorText by viewModel.colorText.collectAsState()
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
    incidentDateTime: LocalDateTime,
    onIncidentDateTimeChanged: (LocalDateTime) -> Unit,
    addressText: String,
    onAddressTextChanged: (String) -> Unit,
    isLookingUpAddress: Boolean,
    onUseCurrentLocationRequested: () -> Unit,
    onOpenWitnessDetailsRequested: () -> Unit,
    onWeiterRequested: () -> Unit,
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
                text = "Fotos des Falschparkers",
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

            RequiredBrandDropdownField(
                value = makeText,
                onValueChange = onMakeTextChanged,
            )

            RequiredTextField(
                value = colorText,
                onValueChange = onColorTextChanged,
                label = "Farbe",
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

            OutlinedTextField(
                value = addressText,
                onValueChange = onAddressTextChanged,
                label = { Text(text = "Tatort") },
                modifier = Modifier.fillMaxWidth(),
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

            Button(
                onClick = onWeiterRequested,
                enabled = licensePlateText.isNotBlank() && makeText.isNotBlank() && colorText.isNotBlank(),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequiredBrandDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var wasFocused by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val isError = touched && value.isBlank()
    val filteredBrands = remember(value) {
        germanCarBrands.filter { it.contains(value, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredBrands.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(text = "Marke *") },
            isError = isError,
            supportingText = if (isError) {
                { Text(text = "Pflichtfeld") }
            } else {
                null
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuDefaults.PrimaryEditable)
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
            expanded = expanded && filteredBrands.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filteredBrands.forEach { brand ->
                DropdownMenuItem(
                    text = { Text(text = brand) },
                    onClick = {
                        onValueChange(brand)
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

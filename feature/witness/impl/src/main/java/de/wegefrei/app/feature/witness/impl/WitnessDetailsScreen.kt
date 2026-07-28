package de.wegefrei.app.feature.witness.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.wegefrei.app.core.designsystem.rememberTouchedFieldState

@Composable
internal fun WitnessDetailsRoot(onBackRequested: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val viewModel: WitnessDetailsViewModel =
        viewModel(
            factory =
                remember {
                    viewModelFactory {
                        initializer {
                            WitnessDetailsViewModel(DataStoreWitnessDetailsRepository(context))
                        }
                    }
                },
        )

    val name by viewModel.name.collectAsState()
    val address by viewModel.address.collectAsState()
    val email by viewModel.email.collectAsState()
    val authorityEmail by viewModel.authorityEmail.collectAsState()

    WitnessDetailsScreen(
        name = name,
        onNameChanged = viewModel::onNameChanged,
        address = address,
        onAddressChanged = viewModel::onAddressChanged,
        email = email,
        onEmailChanged = viewModel::onEmailChanged,
        authorityEmail = authorityEmail,
        onAuthorityEmailChanged = viewModel::onAuthorityEmailChanged,
        onBackRequested = onBackRequested,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WitnessDetailsScreen(
    name: String,
    onNameChanged: (String) -> Unit,
    address: String,
    onAddressChanged: (String) -> Unit,
    email: String,
    onEmailChanged: (String) -> Unit,
    authorityEmail: String,
    onAuthorityEmailChanged: (String) -> Unit,
    onBackRequested: () -> Unit,
) {
    var showAllErrors by remember { mutableStateOf(false) }
    val isValid =
        name.isNotBlank() &&
            address.isNotBlank() &&
            isValidEmail(email) &&
            (authorityEmail.isBlank() || isValidEmail(authorityEmail))
    val attemptBack = {
        if (isValid) onBackRequested() else showAllErrors = true
    }

    BackHandler(onBack = attemptBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.title_witness_details_screen)) },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WitnessTextField(
                value = name,
                onValueChange = onNameChanged,
                label = stringResource(R.string.label_name),
                forceShowErrors = showAllErrors,
            )

            WitnessTextField(
                value = address,
                onValueChange = onAddressChanged,
                label = stringResource(R.string.label_address),
                forceShowErrors = showAllErrors,
            )

            WitnessTextField(
                value = email,
                onValueChange = onEmailChanged,
                label = stringResource(R.string.label_email),
                forceShowErrors = showAllErrors,
                validate = { value ->
                    when {
                        value.isBlank() -> stringResource(R.string.error_required_field)
                        !isValidEmail(value) -> stringResource(R.string.error_invalid_email)
                        else -> null
                    }
                },
            )

            HorizontalDivider()

            WitnessTextField(
                value = authorityEmail,
                onValueChange = onAuthorityEmailChanged,
                label = stringResource(R.string.label_email_authority),
                required = false,
                forceShowErrors = showAllErrors,
                validate = { value ->
                    if (value.isNotBlank() && !isValidEmail(value)) stringResource(R.string.error_invalid_email) else null
                },
            )
        }
    }
}

@Composable
private fun WitnessTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = true,
    forceShowErrors: Boolean = false,
    validate: @Composable (String) -> String? = { if (it.isBlank()) stringResource(R.string.error_required_field) else null },
) {
    val touchedField = rememberTouchedFieldState()
    val errorMessage = if (touchedField.touched || forceShowErrors) validate(value) else null

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = if (required) stringResource(R.string.label_required_suffix, label) else label) },
        isError = errorMessage != null,
        supportingText = errorMessage?.let { message -> { Text(text = message) } },
        modifier =
            modifier
                .fillMaxWidth()
                .then(touchedField.modifier),
    )
}

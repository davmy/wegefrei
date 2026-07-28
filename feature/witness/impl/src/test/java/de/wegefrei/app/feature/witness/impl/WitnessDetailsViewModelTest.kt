package de.wegefrei.app.feature.witness.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WitnessDetailsViewModelTest {
    private val repository = FakeWitnessDetailsRepository()
    private lateinit var viewModel: WitnessDetailsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = WitnessDetailsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onNameChanged updates the name state and persists it`() {
        viewModel.onNameChanged("Max Mustermann")

        assertEquals("Max Mustermann", viewModel.name.value)
        assertEquals("Max Mustermann", repository.savedName)
    }

    @Test
    fun `onAddressChanged updates the address state and persists it`() {
        viewModel.onAddressChanged("Musterstraße 1, 12345 Musterstadt")

        assertEquals("Musterstraße 1, 12345 Musterstadt", viewModel.address.value)
        assertEquals("Musterstraße 1, 12345 Musterstadt", repository.savedAddress)
    }

    @Test
    fun `onEmailChanged updates the email state and persists it`() {
        viewModel.onEmailChanged("max@example.com")

        assertEquals("max@example.com", viewModel.email.value)
        assertEquals("max@example.com", repository.savedEmail)
    }

    @Test
    fun `authorityEmail defaults to blank`() {
        assertEquals("", viewModel.authorityEmail.value)
    }

    @Test
    fun `onAuthorityEmailChanged updates the state and persists it`() {
        viewModel.onAuthorityEmailChanged("ordnungsamt@example.com")

        assertEquals("ordnungsamt@example.com", viewModel.authorityEmail.value)
        assertEquals("ordnungsamt@example.com", repository.savedAuthorityEmail)
    }

    @Test
    fun `loads persisted values from the repository on init`() {
        val prefilled =
            FakeWitnessDetailsRepository(
                initialName = "Erika Musterfrau",
                initialAddress = "Beispielweg 2",
                initialEmail = "erika@example.com",
                initialAuthorityEmail = "ordnungsamt@example.com",
            )

        val loadedViewModel = WitnessDetailsViewModel(prefilled)

        assertEquals("Erika Musterfrau", loadedViewModel.name.value)
        assertEquals("Beispielweg 2", loadedViewModel.address.value)
        assertEquals("erika@example.com", loadedViewModel.email.value)
        assertEquals("ordnungsamt@example.com", loadedViewModel.authorityEmail.value)
    }
}

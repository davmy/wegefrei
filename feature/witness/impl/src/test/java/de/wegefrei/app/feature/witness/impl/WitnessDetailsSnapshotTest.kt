package de.wegefrei.app.feature.witness.impl

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Exercises [readWitnessDetails] against a real (Robolectric-backed)
 * [DataStoreWitnessDetailsRepository], proving the four DataStore reads are wired into the
 * correct [WitnessDetails] fields (e.g. no field mix-up) — the same class of plumbing risk
 * [WitnessDetailsCompletionAcceptanceTest] covers for [areWitnessDetailsComplete].
 *
 * Kept to a single [Test] method deliberately, matching [DataStoreWitnessDetailsRepositoryTest]
 * and [WitnessDetailsCompletionAcceptanceTest].
 */
@RunWith(RobolectricTestRunner::class)
class WitnessDetailsSnapshotTest {
    @Test
    fun `readWitnessDetails reflects the current persisted state`() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val repository = DataStoreWitnessDetailsRepository(context)

            repository.saveName("Max Mustermann")
            repository.saveAddress("Musterstraße 1")
            repository.saveEmail("max@example.com")
            repository.saveAuthorityEmail("ordnungsamt@example.com")

            val details = readWitnessDetails(context)

            assertEquals(
                WitnessDetails("Max Mustermann", "Musterstraße 1", "max@example.com", "ordnungsamt@example.com"),
                details,
            )
        }
}

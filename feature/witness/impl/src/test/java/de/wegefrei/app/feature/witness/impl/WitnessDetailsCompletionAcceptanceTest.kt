package de.wegefrei.app.feature.witness.impl

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Exercises [areWitnessDetailsComplete] against a real (Robolectric-backed)
 * [DataStoreWitnessDetailsRepository], proving the DataStore reads are wired into
 * [isWitnessDetailsRecordComplete] correctly (e.g. no field mix-up) - unlike the pure-function
 * tests for [isWitnessDetailsRecordComplete], which never touch DataStore at all.
 *
 * Kept to a single [Test] method deliberately, matching [DataStoreWitnessDetailsRepositoryTest]:
 * the `witnessDetailsDataStore` delegate backing [DataStoreWitnessDetailsRepository] is a
 * top-level singleton-per-delegate-instance (see `WitnessDetailsRepository.kt`), and Robolectric
 * only guarantees a fresh `Application`/classloader per test *method*, not per assertion within
 * one.
 */
@RunWith(RobolectricTestRunner::class)
class WitnessDetailsCompletionAcceptanceTest {

    @Test
    fun `areWitnessDetailsComplete reflects the current persisted state`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val repository = DataStoreWitnessDetailsRepository(context)

        repository.saveName("Max Mustermann")
        repository.saveAddress("Musterstraße 1")
        repository.saveEmail("max@example.com")

        assertTrue(areWitnessDetailsComplete(context))

        repository.saveEmail("")

        assertFalse(areWitnessDetailsComplete(context))
    }
}

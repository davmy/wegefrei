package de.wegefrei.app.feature.witness.impl

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Exercises the real [DataStoreWitnessDetailsRepository] against real (Robolectric-backed)
 * Android DataStore I/O, proving the persistence round-trip actually works end to end -
 * unlike [WitnessDetailsViewModelTest], which only ever talks to [FakeWitnessDetailsRepository].
 *
 * Kept to a single [Test] method deliberately: the `witnessDetailsDataStore` delegate backing
 * [DataStoreWitnessDetailsRepository] is a top-level singleton-per-delegate-instance (see
 * `WitnessDetailsRepository.kt`), and Robolectric only guarantees a fresh `Application`/classloader
 * per test *method*, not per assertion within one. A single method sidesteps any question of
 * whether the underlying DataStore file/instance is shared across test invocations.
 */
@RunWith(RobolectricTestRunner::class)
class DataStoreWitnessDetailsRepositoryTest {
    @Test
    fun `saveName persists and is readable back through a new repository instance`() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val repository = DataStoreWitnessDetailsRepository(context)

            repository.saveName("Max Mustermann")

            val newRepositoryInstance = DataStoreWitnessDetailsRepository(context)
            assertEquals("Max Mustermann", newRepositoryInstance.name.first())
        }
}

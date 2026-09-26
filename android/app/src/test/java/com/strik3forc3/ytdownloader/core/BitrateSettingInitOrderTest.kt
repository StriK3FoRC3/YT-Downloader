package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * `BitrateSetting.ALL` must not contain nulls, whichever of the sealed interface's
 * members is touched first.
 *
 * This test has to be the first thing in its JVM to touch `BitrateSetting`, so it must
 * stay in a class of its own — sharing a JVM with a test that reads `ALL` first would
 * initialise the classes in the safe order and pass vacuously.
 */
class BitrateSettingInitOrderTest {

    @Test
    fun `ALL has no null entries when Automatic is initialised first`() {
        // Touching Automatic first is what the app does: it is the default a profile
        // carries, so settings load it long before the Quality picker reads ALL.
        val first: BitrateSetting = BitrateSetting.Automatic
        assertThat(first).isNotNull()

        val all = BitrateSetting.ALL
        assertThat(all).doesNotContain(null)
        assertThat(all.first()).isEqualTo(BitrateSetting.Automatic)
        // Every entry must be able to answer the interface's default method — this is the
        // exact call that crashed the release build on Android 8.
        assertThat(all.map { it.label }).contains("Automatic")
    }
}

package com.chatwithwork.app.bridge

import androidx.core.view.HapticFeedbackConstantsCompat
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HapticComponentTest {
    @Test
    fun `maps every feedback name to an Android effect`() {
        assertThat(HapticComponent.constantFor("success")).isEqualTo(HapticFeedbackConstantsCompat.CONFIRM)
        assertThat(HapticComponent.constantFor("warning")).isEqualTo(HapticFeedbackConstantsCompat.REJECT)
        assertThat(HapticComponent.constantFor("error")).isEqualTo(HapticFeedbackConstantsCompat.REJECT)
        assertThat(HapticComponent.constantFor("selection")).isEqualTo(HapticFeedbackConstantsCompat.SEGMENT_TICK)
        assertThat(HapticComponent.constantFor("heavy")).isEqualTo(HapticFeedbackConstantsCompat.LONG_PRESS)
        assertThat(HapticComponent.constantFor("soft")).isEqualTo(HapticFeedbackConstantsCompat.CLOCK_TICK)
        assertThat(HapticComponent.constantFor("rigid")).isEqualTo(HapticFeedbackConstantsCompat.KEYBOARD_TAP)
    }

    @Test
    fun `unknown feedback confirms, as Joe Masilotti's does`() {
        assertThat(HapticComponent.constantFor("sparkle")).isEqualTo(HapticFeedbackConstantsCompat.CONFIRM)
        assertThat(HapticComponent.constantFor(null)).isEqualTo(HapticFeedbackConstantsCompat.CONFIRM)
    }
}

package com.recall.android.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityCapturePolicyTest {
    @Test
    fun `accepts a tree from the event application`() {
        assertTrue(
            AccessibilityCapturePolicy.belongsToEventPackage(
                eventPackage = "com.android.chrome",
                nodePackage = "com.android.chrome",
            ),
        )
    }

    @Test
    fun `rejects a launcher tree attached to another application event`() {
        assertFalse(
            AccessibilityCapturePolicy.belongsToEventPackage(
                eventPackage = "com.android.chrome",
                nodePackage = "com.teslacoilsw.launcher",
            ),
        )
    }

    @Test
    fun `rejects a tree with no owning package`() {
        assertFalse(
            AccessibilityCapturePolicy.belongsToEventPackage(
                eventPackage = "com.android.chrome",
                nodePackage = null,
            ),
        )
    }
}

package com.jsos.phone.glasses

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RokidAiEventOwnershipTest {
    @Test
    fun `only direct cxr m owns rokid ai events`() {
        assertTrue(shouldEnableRokidAiEvents(GlassesConnectionManager.Transport.DIRECT_CXR_M))
        assertFalse(shouldEnableRokidAiEvents(GlassesConnectionManager.Transport.HI_ROKID_CXR_L))
        assertFalse(shouldEnableRokidAiEvents(GlassesConnectionManager.Transport.DEBUG_WEBSOCKET))
    }
}

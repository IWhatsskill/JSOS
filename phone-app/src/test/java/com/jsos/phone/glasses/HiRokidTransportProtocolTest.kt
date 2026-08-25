package com.jsos.phone.glasses

import com.jsos.shared.HiRokidTransportProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HiRokidTransportProtocolTest {
    @Test
    fun `CXR-L phone payload unwraps command route`() {
        assertEquals(
            "{\"type\":\"chat_message\"}",
            HiRokidTransportProtocol.decodePhoneToGlasses(
                listOf("command", "{\"type\":\"chat_message\"}"),
            ),
        )
    }

    @Test
    fun `legacy CXR-M phone payload stays compatible`() {
        assertEquals(
            "{\"type\":\"request_state\"}",
            HiRokidTransportProtocol.decodePhoneToGlasses(
                listOf("{\"type\":\"request_state\"}"),
            ),
        )
    }

    @Test
    fun `glasses reply accepts only command channel`() {
        val payload = listOf("{\"type\":\"wake_ack\"}")
        assertEquals(
            payload[0],
            HiRokidTransportProtocol.decodeGlassesToPhone("command", payload),
        )
        assertNull(HiRokidTransportProtocol.decodeGlassesToPhone("terminal", payload))
    }

    @Test
    fun `empty payloads are rejected`() {
        assertNull(HiRokidTransportProtocol.decodePhoneToGlasses(emptyList()))
        assertNull(HiRokidTransportProtocol.decodeGlassesToPhone("command", listOf("")))
    }
}

package com.jsos.phone

import com.jsos.phone.openclaw.GatewayUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayUrlTest {
    @Test
    fun bareHostsUseConfiguredPort() {
        assertEquals("ws://10.0.0.5:18789", GatewayUrl.displayUrl("10.0.0.5", "18789"))
        assertEquals("ws://gateway.local:18888", GatewayUrl.displayUrl("gateway.local", "18888"))
    }

    @Test
    fun bareHostsWithExistingPortsArePreserved() {
        assertEquals("ws://10.0.0.5:19999", GatewayUrl.displayUrl("10.0.0.5:19999", "18789"))
    }

    @Test
    fun completeWebSocketUrlsAreNotRewritten() {
        assertEquals("wss://example.com/socket", GatewayUrl.displayUrl("wss://example.com/socket", "18789"))
        assertEquals("wss://example.com/socket?token=abc", GatewayUrl.displayUrl("wss://example.com/socket?token=abc", "18789"))
        assertEquals("ws://192.168.1.20:18789", GatewayUrl.displayUrl("ws://192.168.1.20:18789", "19999"))
    }

    @Test
    fun originUsesOnlySchemeAndAuthority() {
        assertEquals("https://example.com", GatewayUrl.originUrl("wss://example.com/socket"))
        assertEquals("http://10.0.0.5:18789", GatewayUrl.originUrl("ws://10.0.0.5:18789/path"))
    }
}

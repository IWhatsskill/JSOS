package com.jsos.shared

/**
 * Wire framing shared by the direct CXR-M bridge and the Hi Rokid CXR-L bridge.
 *
 * CXR-M delivers the JSON as the first Caps value. CXR-L custom commands add a
 * routing value before that JSON, so the glasses app must accept both shapes.
 */
object HiRokidTransportProtocol {
    const val PHONE_TO_GLASSES_CHANNEL = "terminal"
    const val PHONE_TO_GLASSES_ROUTE = "command"
    const val GLASSES_TO_PHONE_CHANNEL = "command"

    fun decodePhoneToGlasses(values: List<String>): String? {
        val payload = when {
            values.size >= 2 && values[0] == PHONE_TO_GLASSES_ROUTE -> values[1]
            values.isNotEmpty() -> values[0]
            else -> return null
        }
        return payload.takeIf { it.isNotBlank() }
    }

    fun decodeGlassesToPhone(channel: String?, values: List<String>): String? {
        if (channel != GLASSES_TO_PHONE_CHANNEL) return null
        return values.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}

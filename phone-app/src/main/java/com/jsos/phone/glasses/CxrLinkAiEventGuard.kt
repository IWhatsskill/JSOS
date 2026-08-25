package com.jsos.phone.glasses

import android.util.Log
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.ICXRLinkCbk
import com.rokid.sprite.aiapp.externalapp.IAiEventCallback
import com.rokid.sprite.aiapp.externalapp.IMediaStreamService
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap

/**
 * Keeps the native Hi Rokid assistant alive while JSOS uses CXR-L.
 *
 * Rokid client-l's built-in AI callback sends sendExit(false) before forwarding
 * onAiKeyDown. JSOS only needs the callback notification, so replace that one
 * callback while delegating every other event to the vendor implementation.
 */
internal object CxrLinkAiEventGuard {
    private const val TAG = "CxrLinkAiEventGuard"
    private data class Registration(
        val service: IMediaStreamService,
        val original: IAiEventCallback,
        val passive: IAiEventCallback,
    )

    private val registrations = Collections.synchronizedMap(
        WeakHashMap<CXRLink, Registration>()
    )

    fun install(link: CXRLink): Boolean = runCatching {
        if (registrations.containsKey(link)) return true

        val service = findAssignableField(link, IMediaStreamService::class.java)
            .get(link) as IMediaStreamService
        val aiCallbackField = findAssignableField(link, IAiEventCallback::class.java)
        val original = aiCallbackField.get(link) as IAiEventCallback
        val linkCallback = findAssignableField(link, ICXRLinkCbk::class.java)
            .get(link) as ICXRLinkCbk

        val passive = object : IAiEventCallback.Stub() {
            override fun onAiKeyDown() {
                Log.i(TAG, "CXR-L AI key passed through without automatic exit")
                linkCallback.onGlassAiAssistStart()
            }

            override fun onAiKeyUp() {
                original.onAiKeyUp()
            }

            override fun onAiExit() {
                original.onAiExit()
            }

            override fun onGlassAppResumeChange(packageName: String?, state: String?) {
                original.onGlassAppResumeChange(packageName, state)
            }
        }

        check(service.unregistAiEventCallback(original)) {
            "Vendor AI callback could not be unregistered"
        }
        if (!service.registAiEventCallback(passive)) {
            service.registAiEventCallback(original)
            error("Passive AI callback could not be registered")
        }
        registrations[link] = Registration(service, original, passive)
        Log.i(TAG, "CXR-L automatic AI exit disabled")
        true
    }.getOrElse { error ->
        Log.e(TAG, "Failed to install CXR-L AI event guard", error)
        false
    }

    fun uninstall(link: CXRLink) {
        val registration = registrations.remove(link) ?: return
        runCatching {
            registration.service.unregistAiEventCallback(registration.passive)
            registration.service.registAiEventCallback(registration.original)
        }.onFailure { error ->
            Log.w(TAG, "Failed to restore vendor CXR-L AI callback during disconnect", error)
        }
    }

    private fun findAssignableField(instance: Any, fieldType: Class<*>): Field {
        var type: Class<*>? = instance.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { candidate ->
                fieldType.isAssignableFrom(candidate.type)
            }
            if (field != null) {
                field.isAccessible = true
                return field
            }
            type = type.superclass
        }
        error("CXR-L field not found for ${fieldType.name}")
    }
}

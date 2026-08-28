package com.jsos.phone.glasses

import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.ICXRLinkCbk
import com.rokid.cxr.link.callbacks.IImageStreamCbk
import com.rokid.sprite.aiapp.externalapp.IAiEventCallback
import com.rokid.sprite.aiapp.externalapp.IMediaStreamService
import org.junit.Assert.assertTrue
import org.junit.Test

class CxrLinkAiEventGuardContractTest {
    @Test
    fun `rokid client l exposes every field required by the guard`() {
        assertTrue(hasAssignableField(CXRLink::class.java, IMediaStreamService::class.java))
        assertTrue(hasAssignableField(CXRLink::class.java, IAiEventCallback::class.java))
        assertTrue(hasAssignableField(CXRLink::class.java, ICXRLinkCbk::class.java))
    }

    @Test
    fun `rokid client l exposes one-shot image capture`() {
        val setCallback = CXRLink::class.java.getMethod(
            "setCXRImageCbk",
            IImageStreamCbk::class.java,
        )
        val takePhoto = CXRLink::class.java.getMethod(
            "takePhoto",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )

        assertTrue(setCallback.returnType == Void.TYPE)
        assertTrue(takePhoto.returnType == Boolean::class.javaPrimitiveType)
    }

    private fun hasAssignableField(startType: Class<*>, fieldType: Class<*>): Boolean {
        var type: Class<*>? = startType
        while (type != null) {
            if (type.declaredFields.any { candidate ->
                    fieldType.isAssignableFrom(candidate.type)
                }
            ) {
                return true
            }
            type = type.superclass
        }
        return false
    }
}

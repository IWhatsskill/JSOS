package com.jsos.phone.ring

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class RingInputAction {
    Tap,
    DoubleTap,
    Forward,
    Backward,
}

object RingInputBus {
    private val _events = MutableSharedFlow<RingInputAction>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    fun emit(action: RingInputAction) {
        _events.tryEmit(action)
    }
}

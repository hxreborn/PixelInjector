package eu.hxreborn.pixelinjector

import io.github.libxposed.service.XposedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BoundService {
    private val _current = MutableStateFlow<XposedService?>(null)
    val current: StateFlow<XposedService?> = _current.asStateFlow()

    fun bind(service: XposedService) {
        _current.value = service
    }

    fun release(died: XposedService) {
        _current.compareAndSet(died, null)
    }
}

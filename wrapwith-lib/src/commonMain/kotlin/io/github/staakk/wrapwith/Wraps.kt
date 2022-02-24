package io.github.staakk.wrapwith

import kotlin.jvm.Synchronized
import kotlin.native.concurrent.ThreadLocal

@Suppress("unused")
@ThreadLocal
object Wraps {

    private val map = mutableMapOf<String, MutableSet<Wrap>>()

    @Synchronized
    fun registerWrap(wrapId: String, wrap: Wrap) {
        if (!map.containsKey(wrapId)) map[wrapId] = mutableSetOf()
        map[wrapId]?.add(wrap)
    }

    @Synchronized
    fun removeWrap(wrapId: String, wrap: Wrap): Boolean {
        return map[wrapId]
            ?.remove(wrap)
            ?: false
    }

    @Synchronized
    fun removeAllWraps() = map.clear()
    
    fun invokeBefore(
        functionInvocation: FunctionInvocation,
        vararg ids: String
    ) = ids.forEach { id ->
        map[id]?.forEach { wrap ->
            wrap.before(functionInvocation)
        }
    }

    fun invokeAfter(
        returnValue: Any?,
        vararg ids: String
    ) = ids.forEach { id ->
        map[id]?.forEach { wrap ->
            wrap.after(returnValue)
        }
    }
}
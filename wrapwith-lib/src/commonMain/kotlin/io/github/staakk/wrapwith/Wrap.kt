package io.github.staakk.wrapwith

@Suppress("unused")
interface Wrap {

    fun before(functionInvocation: FunctionInvocation)

    fun after(returnValue: Any?)
}
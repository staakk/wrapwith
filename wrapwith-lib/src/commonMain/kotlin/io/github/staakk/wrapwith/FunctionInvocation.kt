package io.github.staakk.wrapwith

data class FunctionInvocation(
    val functionName: String,
    val params: Map<String, Any?> = emptyMap(),
) {
    @Suppress("unused")
    constructor(
        functionName: String,
        vararg params: Pair<String, Any?>
    ) : this(functionName, params.toMap())

    fun getValueParameters() = params
        .filter { (key, _) -> key !in arrayOf("\$this", "\$receiver") }

    fun getDispatchReceiverParameter(): Any? = params["\$this"]

    fun getExtensionReceiverParameter(): Any? = params["\$receiver"]
}
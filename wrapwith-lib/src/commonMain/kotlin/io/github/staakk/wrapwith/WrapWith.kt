package io.github.staakk.wrapwith

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
@Suppress("unused")
annotation class WrapWith(
    val id: String,
    vararg val ids: String
)

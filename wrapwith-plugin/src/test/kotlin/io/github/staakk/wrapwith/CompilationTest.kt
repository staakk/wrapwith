package io.github.staakk.wrapwith

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.Assert.assertEquals
import org.junit.Test

class IrPluginTest {

    @Test
    fun `top level, no args, unit return`() = assertCompilationSuccessful(
        """
        import io.github.staakk.wrapwith.WrapWith

        @WrapWith("test")
        fun foo() = Unit
    """
    )

    @Test
    fun `top level, one arg, unit return`() = assertCompilationSuccessful(
        """
        import io.github.staakk.wrapwith.WrapWith

        @WrapWith("test")
        fun foo(arg: String) = Unit
    """
    )

    @Test
    fun `top level, no args, string return`() = assertCompilationSuccessful(
        """
        import io.github.staakk.wrapwith.WrapWith

        @WrapWith("test")
        fun foo() = "test"
    """
    )

    @Test
    fun `top level, one arg, string return`() = assertCompilationSuccessful(
        """
        import io.github.staakk.wrapwith.WrapWith

        @WrapWith("test")
        fun foo(arg: String) = arg
    """
    )

    @Test
    fun `top level, one arg, string return, with receiver`() = assertCompilationSuccessful(
        """
        import io.github.staakk.wrapwith.WrapWith

        @WrapWith("test")
        fun String.foo(arg: String) = arg + this
    """
    )

    @Test
    fun `top level, one arg, int return, multiple returns`() = assertCompilationSuccessful(
        """
        import io.github.staakk.wrapwith.WrapWith

        @WrapWith("test")
        fun foo(arg: String): Int {
            if (arg.startsWith("test")) return 1
            return 0
        }
    """
    )

    @Test
    fun `inside class, no arg, unit return`() = assertCompilationSuccessful(
        """
        import io.github.staakk.wrapwith.WrapWith

        class Bar {
            @WrapWith("test")
            fun foo() = Unit
        }
    """
    )

    @Test
    fun `inside class, no arg, unit return, with receiver`() = assertCompilationSuccessful(
        """
        import io.github.staakk.wrapwith.WrapWith

        class Bar {
            @WrapWith("test")
            fun Int.foo() = this
        }
    """
    )

    @Test
    fun `no wrap id, should not compile`() = assertCompilation(
        contents = """
            import io.github.staakk.wrapwith.WrapWith
    
            @WrapWith
            fun foo() = Unit
        """,
        expectedExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR
    )


    private fun assertCompilationSuccessful(@Language("kotlin") contents: String) =
        assertCompilation(contents, KotlinCompilation.ExitCode.OK)

    private fun assertCompilation(@Language("kotlin") contents: String, expectedExitCode: KotlinCompilation.ExitCode) {
        val result = compile(SourceFile.kotlin("test.kt", contents))
        assertEquals(expectedExitCode, result.exitCode)
    }
}

fun compile(
    sourceFiles: List<SourceFile>,
    plugin: ComponentRegistrar = WrapWithComponentRegistrar(),
): KotlinCompilation.Result {
    return KotlinCompilation().apply {
        sources = sourceFiles
        useIR = true
        compilerPlugins = listOf(plugin)
        inheritClassPath = true
    }.compile()
}

fun compile(
    sourceFile: SourceFile,
    plugin: ComponentRegistrar = WrapWithComponentRegistrar(),
): KotlinCompilation.Result {
    return compile(listOf(sourceFile), plugin)
}

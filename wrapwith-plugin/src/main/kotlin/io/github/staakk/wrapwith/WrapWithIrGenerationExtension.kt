package io.github.staakk.wrapwith

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.dump

class WrapWithIrGenerationExtension(
    private val messageCollector: MessageCollector
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        messageCollector.report(CompilerMessageSeverity.INFO, moduleFragment.dump())

        moduleFragment.transform(WrapWithTransformer(pluginContext, messageCollector), null)
        messageCollector.report(CompilerMessageSeverity.INFO, moduleFragment.dump())
    }
}
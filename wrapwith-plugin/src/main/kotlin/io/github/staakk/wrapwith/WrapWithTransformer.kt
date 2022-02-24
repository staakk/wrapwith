package io.github.staakk.wrapwith

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.allParameters
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irCatch
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.isDispatchReceiver
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTryImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class WrapWithTransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
) : IrElementTransformerVoidWithContext() {

    private val typeUnit = pluginContext.irBuiltIns.unitType
    private val typeString = pluginContext.irBuiltIns.stringType
    private val typeAnyN = pluginContext.irBuiltIns.anyNType
    private val typeThrowable = pluginContext.irBuiltIns.throwableType

    private val fqNameWrapWith = FqName("io.github.staakk.wrapwith.WrapWith")
    private val annotationWrapWith = pluginContext.referenceClass(fqNameWrapWith)!!
    private val classWraps = pluginContext.referenceClass(FqName("io.github.staakk.wrapwith.Wraps"))!!
    private val functionInvokeBefore = pluginContext
        .referenceFunctions(FqName("io.github.staakk.wrapwith.Wraps.invokeBefore"))
        .single()
    private val functionInvokeAfter = pluginContext
        .referenceFunctions(FqName("io.github.staakk.wrapwith.Wraps.invokeAfter"))
        .single()
    private val classFunctionInvocation = pluginContext
        .referenceClass(FqName("io.github.staakk.wrapwith.FunctionInvocation"))!!
    private val ctorFunctionInvocation = classFunctionInvocation
        .constructors
        .single { it.owner.valueParameters[1].isVararg }

    private val ctorPair = pluginContext.referenceConstructors(FqName("kotlin.Pair"))
        .single()
    private val typePairStringAnyN = pluginContext.referenceClass(FqName("kotlin.Pair"))!!
        .typeWith(typeString, typeAnyN)

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        val body = declaration.body
        if (body != null && declaration.hasAnnotation(annotationWrapWith)) {
            val idsCollector = IdsCollector()
            declaration
                .getAnnotation(fqNameWrapWith)!!
                .transform(idsCollector, null)

            declaration.body = wrapFunctionBody(declaration, body, idsCollector.ids)
        }
        return super.visitFunctionNew(declaration)
    }

    private fun wrapFunctionBody(function: IrFunction, body: IrBody, ids: List<String>): IrBlockBody {
        return DeclarationIrBuilder(pluginContext, function.symbol).irBlockBody {
            val functionInvocation = buildFunctionInvocationVariable(function)
            +functionInvocation
            +irInvokeBefore(irGet(functionInvocation), ids)

            val tryBlock = irBlock(resultType = function.returnType) {
                for (statement in body.statements) +statement
                if (function.returnType == typeUnit) +irInvokeAfter(ids, irUnit())
            }.transform(ReturnTransformer(function, ids), null)

            val throwable = buildVariable(
                scope.getLocalDeclarationParent(),
                startOffset,
                endOffset,
                IrDeclarationOrigin.CATCH_PARAMETER,
                Name.identifier("throwable"),
                typeThrowable
            )

            +IrTryImpl(startOffset, endOffset, tryBlock.type).apply {
                tryResult = tryBlock
                catches += irCatch(throwable, irBlock {
                    +irInvokeAfter(ids, irGet(throwable))
                    +irThrow(irGet(throwable))
                })
            }
        }
    }

    private fun IrBuilderWithScope.irInvokeBefore(
        functionInvocation: IrExpression,
        ids: List<String>
    ) = irCall(functionInvokeBefore).apply {
        dispatchReceiver = irGetObject(classWraps)
        putValueArgument(0, functionInvocation)
        putValueArgument(
            index = 1,
            valueArgument = irVararg(
                typeString,
                ids.map { irString(it) }
            )
        )
    }

    private fun IrBuilderWithScope.irInvokeAfter(
        ids: List<String>,
        result: IrExpression? = null,
    ) = irCall(functionInvokeAfter).apply {
        dispatchReceiver = irGetObject(classWraps)
        putValueArgument(
            index = 0,
            valueArgument = result ?: irNull()
        )
        putValueArgument(
            index = 1,
            valueArgument = irVararg(
                typeString,
                ids.map { irString(it) }
            )
        )
    }

    private fun IrBlockBodyBuilder.buildFunctionInvocationVariable(
        function: IrFunction
    ): IrVariable {
        val functionInvocation = buildVariable(
            scope.getLocalDeclarationParent(),
            startOffset,
            endOffset,
            IrDeclarationOrigin.DEFINED,
            Name.identifier("functionInvocation"),
            classFunctionInvocation.defaultType
        )
        return functionInvocation.apply {
            initializer = irCall(ctorFunctionInvocation).apply {
                val parameters = mutableListOf<IrExpression>()
                function.dispatchReceiverParameter?.let {
                    parameters += irCall(ctorPair).apply {
                        putTypeArgument(0, typeString)
                        putTypeArgument(1, typeAnyN)
                        putValueArgument(0, irString("\$this"))
                        putValueArgument(1, irGet(it))
                    }
                }
                function.extensionReceiverParameter?.let {
                    parameters += irCall(ctorPair).apply {
                        putTypeArgument(0, typeString)
                        putTypeArgument(1, typeAnyN)
                        putValueArgument(0, irString("\$receiver"))
                        putValueArgument(1, irGet(it))
                    }
                }
                parameters += function
                    .valueParameters
                    .map { parameter ->
                        irCall(ctorPair).apply {
                            putTypeArgument(0, typeString)
                            putTypeArgument(1, typeAnyN)
                            putValueArgument(0, irString(parameter.name.asString()))
                            putValueArgument(1, irGet(parameter))
                        }
                    }
                putValueArgument(0, irString(function.kotlinFqName.toString()))
                putValueArgument(1, irVararg(typePairStringAnyN, parameters))
            }
        }
    }

    private inner class IdsCollector : IrElementTransformerVoidWithContext() {
        val ids = mutableListOf<String>()

        override fun visitExpression(expression: IrExpression): IrExpression {
            if (expression is IrConstImpl<*>) {
                ids += (expression.value.toString())
            }
            return super.visitExpression(expression)
        }
    }

    private inner class ReturnTransformer(
        private val function: IrFunction,
        private val ids: List<String>,
    ) : IrElementTransformerVoidWithContext() {
        override fun visitReturn(expression: IrReturn): IrExpression {
            if (expression.returnTargetSymbol != function.symbol) return super.visitReturn(expression)

            return DeclarationIrBuilder(pluginContext, function.symbol).irBlock {
                if (expression.value.type == typeUnit) {
                    +irInvokeAfter(ids, irUnit())
                    +expression
                } else {
                    val result = irTemporary(expression.value)
                    +irInvokeAfter(ids, irGet(result))
                    +expression.apply {
                        value = irGet(result)
                    }
                }
            }
        }
    }
}
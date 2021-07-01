/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.interpreter.stack.CallStack
import org.jetbrains.kotlin.ir.interpreter.stack.Variable
import org.jetbrains.kotlin.ir.interpreter.state.State
import org.jetbrains.kotlin.ir.interpreter.state.reflection.KClassState
import org.jetbrains.kotlin.ir.interpreter.state.reflection.KPropertyState
import org.jetbrains.kotlin.ir.interpreter.state.reflection.KTypeState
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

abstract class PartialIrInterpreter(val irBuiltIns: IrBuiltIns) : IrElementTransformerVoid() {
    private val environment = IrInterpreterEnvironment(irBuiltIns)
    internal val callStack: CallStack
        get() = environment.callStack
    private val interpreter = IrInterpreter(environment, emptyMap())

    internal fun evaluate(irExpression: IrExpression, args: List<State> = emptyList()) {
        try {
            //interpreter.interpretWithArgs(irExpression, args).let { callStack.pushState(it) }
        } catch (e: Exception) {
            // nothing
        }
    }

    fun interpret(block: IrReturnableBlock): IrElement {
        callStack.newFrame(block)
        return block.accept(this, null).apply { callStack.dropFrame() }
    }

    override fun visitCall(expression: IrCall): IrExpression {
        // TODO if has defaults create new function
        expression.dispatchReceiver = expression.dispatchReceiver?.transform(this, null)
        // TODO simple instruction on value parameter
        expression.extensionReceiver = expression.extensionReceiver?.transform(this, null)
        (0 until expression.valueArgumentsCount).forEach {
            expression.putValueArgument(it, expression.getValueArgument(it)?.transform(this, null))
        }

        // TODO collect all args; if there is enough of them -> evaluate call
        return expression
    }

//    private fun customVisitCall(expression: IrCall, receiver: State?, args: List<State>?): IrExpression {
//        TODO()
//    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        callStack.newSubFrame(expression)
        expression.transformChildren(this, null)
        callStack.dropSubFrame()
        return expression
    }

    override fun visitReturn(expression: IrReturn): IrExpression {
        expression.value = expression.value.transform(this, null)
        // TODO if value was not calculated -> return
        return expression
    }

//    override fun visitSetField(expression: IrSetField): IrExpression {
//        expression.value = expression.value.transform(this, null)
//        // TODO if value was not calculated -> return
//        return expression
//    }
//
//    override fun visitGetField(expression: IrGetField): IrExpression {
//        // TODO evaluate if possible
//        return expression
//    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        // TODO unfold (!) and evaluate; evaluate can throw exception, how to avoid?
        return expression
    }

    override fun <T> visitConst(expression: IrConst<T>): IrExpression {
        // TODO evaluate
        return expression
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        if (declaration.initializer == null) {
            callStack.addVariable(Variable(declaration.symbol))
        } else {
            declaration.initializer = declaration.initializer?.transform(this, null)
            if (callStack.peekState() != null) callStack.addVariable(Variable(declaration.symbol, callStack.popState()))
        }

        return declaration
    }
}

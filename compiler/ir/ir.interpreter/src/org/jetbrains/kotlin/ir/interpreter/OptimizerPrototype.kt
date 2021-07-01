/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.interpreter.state.asBooleanOrNull
import org.jetbrains.kotlin.ir.interpreter.state.reflection.KPropertyState
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid

class OptimizerPrototype(irBuiltIns: IrBuiltIns) : PartialIrInterpreter(irBuiltIns) {
    override fun visitWhileLoop(loop: IrWhileLoop): IrExpression {
        val newBlock = IrBlockImpl(0, 0, loop.body!!.type)
        while (true) {
            loop.condition.accept(this, null)
            val result = callStack.peekState()?.asBooleanOrNull()
            if (result == true) {
                callStack.popState()
                val newBody = loop.body!!.deepCopyWithSymbols(ParentFinder().apply { loop.accept(this, null) }.parent)
                if (newBody is IrBlock) {
                    newBlock.statements += newBody.statements
                } else {
                    newBlock.statements += newBody
                }
                newBody.transformChildren(this, null)
            } else if (result == false) {
                callStack.popState()
                break
            } else {
                return loop
            }
        }
        return newBlock
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val owner = expression.symbol.owner
        if (owner.name.asString() != "invoke") {
            return super.visitCall(expression)
        }
        expression.dispatchReceiver?.let {
//            if (callStack.containsVariable(owner.dispatchReceiverParameter!!.symbol)) return@let
            it.accept(this, null)
            if (callStack.peekState() == null) return expression
            val state = callStack.popState()
            if (state is KPropertyState) {
                // TODO("return ir get call on getter")
                val property = state.property
                val getterCall = IrCallImpl.fromSymbolOwner(expression.startOffset, expression.endOffset, expression.type, property.getter!!.symbol)
                getterCall.dispatchReceiver = expression.getValueArgument(0)
                return getterCall
            }
        }
        return super.visitCall(expression)
    }

    private class ParentFinder : IrElementVisitorVoid {
        var parent: IrDeclarationParent? = null
        override fun visitElement(element: IrElement) {
            if (element is IrDeclarationBase && parent == null) {
                parent = element.parent
                return
            }
            element.acceptChildren(this, null)
        }
    }
}
/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.lower

import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.inline.FunctionInlining
import org.jetbrains.kotlin.backend.common.phaser.makeIrModulePhase
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturnableBlock
import org.jetbrains.kotlin.ir.interpreter.OptimizerPrototype
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

val partialCompileTimeEvaluationPhase = makeIrModulePhase(
    ::PartialCompileTimeCalculationLowering,
    name = "PartialCompileTimeEvaluation",
    description = "Evaluate and optimize function statements",
)

class PartialCompileTimeCalculationLowering(val context: CommonBackendContext) : FileLoweringPass {
    private val partialInterpreter = OptimizerPrototype(context.ir.irModule.irBuiltins)
    private val inliner = FunctionInlining(context)

    override fun lower(irFile: IrFile) {
        if (!context.configuration.languageVersionSettings.supportsFeature(LanguageFeature.PartialCompileTimeCalculations)) return
        irFile.transformChildren(Transformer(), null)
    }

    private inner class Transformer : IrElementTransformerVoid() {
        override fun visitField(declaration: IrField): IrStatement {
            val declarationWithInlinedCall = declaration.transform(inliner, null) as IrField
            val expression = declarationWithInlinedCall.initializer?.expression
            if (expression !is IrReturnableBlock) return declaration

            declarationWithInlinedCall.initializer?.expression = partialInterpreter.interpret(expression) as IrExpression
            return declarationWithInlinedCall
        }
    }
}

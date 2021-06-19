/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.references

abstract class KtArrayAccessReference(
    expression: KtArrayAccessExpression
) : KtSimpleReference<KtArrayAccessExpression>(expression), MultiRangeReference {
    override val resolvesByNames: Collection<Name>
        get() = NAMES

    override fun getRangeInElement() = element.textRange.shiftRight(-element.textOffset)

    private fun getBracketRange(bracketToken: KtToken) =
        expression.indicesNode.node.findChildByType(bracketToken)?.textRange?.shiftRight(-expression.textOffset)

    override fun getRanges() = listOfNotNull(getBracketRange(KtTokens.LBRACKET), getBracketRange(KtTokens.RBRACKET))

    override fun canRename() = true

    override fun handleElementRename(newElementName: String): PsiElement? {
        val arrayAccessExpression = expression
        if (OperatorNameConventions.INVOKE.asString() == newElementName) {
            val replacement = KtPsiFactory(arrayAccessExpression.project).buildExpression {
                val arrayExpression = arrayAccessExpression.arrayExpression
                if (arrayExpression is KtQualifiedExpression) {
                    appendExpression(arrayExpression.receiverExpression)
                    appendFixedText(arrayExpression.operationSign.value)
                    appendExpression(arrayExpression.selectorExpression)
                } else {
                    appendExpression(arrayExpression)
                }

                appendFixedText("(")
                appendExpressions(arrayAccessExpression.indexExpressions, ",")
                appendFixedText(")")
            }
            val fullCallExpression = arrayAccessExpression.replaced(replacement)
            val callExpression = fullCallExpression.getPossiblyQualifiedCallExpression()
            if (callExpression != null && canMoveLambdaOutsideParentheses(callExpression)) {
                moveFunctionLiteralOutsideParentheses(callExpression)
            }
            return fullCallExpression
        }

        return doRenameImplicitConventionalCall(newElementName)
    }

    protected abstract fun moveFunctionLiteralOutsideParentheses(callExpression: KtCallExpression)
    protected abstract fun canMoveLambdaOutsideParentheses(callExpression: KtCallExpression): Boolean
    protected abstract fun doRenameImplicitConventionalCall(newName: String?): KtExpression

    companion object {
        private val NAMES = Lists.newArrayList(OperatorNameConventions.GET, OperatorNameConventions.SET)
    }
}
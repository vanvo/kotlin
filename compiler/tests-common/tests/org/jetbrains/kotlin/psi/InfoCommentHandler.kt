/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement

object InfoCommentHandler {
    fun renderWithCommentInfos(ktFile: KtFile, infos: Map<KtDeclaration, String>): String {
        val builder = StringBuilder()
        val anchored = infos.mapKeys { (declaration) -> getAnchor(declaration) }
        ktFile.accept(object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                if (element is LeafPsiElement) {
                    builder.append(element.text)
                } else {
                    element.acceptChildren(this)
                }
                anchored[element]?.let { info ->
                    builder.append("/* $info */")
                }
            }

            override fun visitComment(comment: PsiComment) {}
        })
        return builder.toString()

    }

    fun getAnchor(ktDeclaration: KtDeclaration): PsiElement {
        return when (ktDeclaration) {
            is KtClassOrObject -> {
                ktDeclaration.body?.lBrace ?: ktDeclaration
            }
            is KtFunctionLiteral -> {
                ktDeclaration.lBrace
            }
            is KtFunction -> {
                ktDeclaration.bodyBlockExpression?.lBrace ?: ktDeclaration
            }
            is KtVariableDeclaration -> {
                ktDeclaration.initializer ?: ktDeclaration.typeReference ?: ktDeclaration
            }
            is KtTypeAlias -> {
                ktDeclaration.getTypeReference() ?: ktDeclaration
            }
            is KtTypeParameter -> {
                ktDeclaration.nameIdentifier ?: ktDeclaration
            }
            is KtParameter -> {
                ktDeclaration.defaultValue ?: ktDeclaration.typeReference ?: ktDeclaration
            }
            else -> error("Unsupported declaration $ktDeclaration")
        }
    }
}
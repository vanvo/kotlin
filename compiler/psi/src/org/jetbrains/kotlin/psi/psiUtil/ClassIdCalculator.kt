/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi.psiUtil

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*

internal object ClassIdCalculator {
    fun calculateClassId(declaration: KtClassLikeDeclaration): ClassId? {
        var ktFile: KtFile? = null
        var element: PsiElement? = declaration
        val containingClasses = mutableListOf<KtClassLikeDeclaration>()
        while (element != null) {
            when (element) {
                is KtEnumEntry -> {
                    return null
                }
                is KtClassLikeDeclaration -> {
                    containingClasses += element
                }
                is KtFile -> {
                    ktFile = element
                    break
                }
                is KtDeclaration -> {
                    return null
                }
            }
            element = element.parent
        }
        if (ktFile == null) return null
        val relativeClassName = FqName.fromSegments(
            containingClasses.reversed().map { containingClass ->
                containingClass.name ?: SpecialNames.NO_NAME_PROVIDED.asString()
            }
        )
        return ClassId(ktFile.packageFqName, relativeClassName, /*local=*/false)
    }

    /**
     * @see KtNamedDeclaration.hasNonLocalFqName for semantics
     */
    @JvmStatic
    fun hasNonLocalFqName(declaration: KtNamedDeclaration): Boolean {
        return when (declaration) {
            is KtDestructuringDeclarationEntry, is KtFunctionLiteral, is KtTypeParameter -> false
            is KtConstructor<*> -> false
            is KtParameter -> {
                if (declaration.hasValOrVar()) declaration.containingClassOrObject?.getClassId() != null
                else false
            }
            is KtCallableLikeDeclaration -> {
                when (val parent = declaration.parent) {
                    is KtFile -> true
                    is KtClassBody -> (parent.parent as? KtClassOrObject)?.getClassId() != null
                    else -> false
                }
            }
            is KtClassLikeDeclaration -> declaration.getClassId() != null
            is KtScript -> true
            else -> error("Unexpected ${declaration::class.qualifiedName}")
        }
    }
}
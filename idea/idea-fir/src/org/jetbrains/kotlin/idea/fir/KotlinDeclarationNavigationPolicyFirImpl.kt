/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir

import org.jetbrains.kotlin.psi.KotlinDeclarationNavigationPolicy
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement

internal class KotlinDeclarationNavigationPolicyFirImpl : KotlinDeclarationNavigationPolicy {
    override fun getOriginalElement(declaration: KtDeclaration): KtElement {
        return declaration//todo
    }

    override fun getNavigationElement(declaration: KtDeclaration): KtElement {
        return LibraryClassToSource.getLibrarySource(declaration, declaration.containingKtFile) ?: declaration
    }
}
/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.caches.project.getLibrarySourceScope
import org.jetbrains.kotlin.idea.fir.low.level.api.IndexHelper
import org.jetbrains.kotlin.miniStdLib.collections.zippedAll
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.psi.*

object LibraryClassToSource {
    fun getLibrarySource(declaration: KtDeclaration, containingFile: KtFile): KtDeclaration? {
        if (!containingFile.isCompiled) return null
        val virtualFile = containingFile.virtualFile ?: return null
        val librarySourceScope = getLibrarySourceScope(virtualFile, containingFile.project)
        if (librarySourceScope == GlobalSearchScope.EMPTY_SCOPE) return null
        val indexHelper = IndexHelper(containingFile.project, librarySourceScope)

        return when (declaration) {
            is KtClassLikeDeclaration -> findClassLikeDeclaration(declaration, indexHelper)
            is KtProperty -> findPropertyDeclaration(declaration, indexHelper)
            is KtFunction -> findFunctionDeclaration(declaration, indexHelper)
            else -> null
        }
    }

    private fun findPropertyDeclaration(declaration: KtProperty, indexHelper: IndexHelper): KtProperty? {
        val containingDeclaration = declaration.parentOfType<KtAnnotated>() ?: return null
        val candidates = when (containingDeclaration) {
            is KtClassOrObject -> (findClassLikeDeclaration(containingDeclaration, indexHelper) as? KtClassOrObject)?.body?.properties
            is KtFile -> {
                val callableId = CallableId(containingDeclaration.packageFqName, declaration.nameAsSafeName)
                indexHelper.getTopLevelProperties(callableId)
            }
            else -> null
        } ?: return null
        return findMatchingPropertyCandidates(declaration, candidates)
    }

    private fun findFunctionDeclaration(declaration: KtFunction, indexHelper: IndexHelper): KtFunction? {
        val containingDeclaration = declaration.parentOfType<KtAnnotated>() ?: return null
        val candidates = when (containingDeclaration) {
            is KtClassOrObject -> (findClassLikeDeclaration(containingDeclaration, indexHelper) as? KtClassOrObject)?.body?.functions
            is KtFile -> {
                val callableId = CallableId(containingDeclaration.packageFqName, declaration.nameAsSafeName)
                indexHelper.getTopLevelFunctions(callableId)
            }
            else -> null
        } ?: return null
        return findMatchingFunctionCandidates(declaration, candidates)
    }


    private fun findMatchingPropertyCandidates(declaration: KtProperty, candidates: Collection<KtProperty>): KtProperty? {
        for (candidate in candidates) {
            if (!matchesAsCallable(declaration, candidate)) continue

            // TODO compare types
            return candidate
        }
        return null
    }

    private fun findMatchingFunctionCandidates(declaration: KtFunction, candidates: Collection<KtFunction>): KtFunction? {
        for (candidate in candidates) {
            if (!matchesAsCallable(declaration, candidate)) continue
            if (declaration.valueParameters.size != candidate.valueParameters.size) continue
            if (!haveTheSameValueParametersByPsi(declaration, candidate)) continue

            // TODO compare types
            return candidate
        }
        return null
    }

    private fun haveTheSameValueParametersByPsi(
        declaration: KtFunction,
        candidate: KtFunction
    ) = declaration.valueParameters.zippedAll(candidate.valueParameters) { expected, actual ->
        expected.name == actual.name && expected.isVarArg == actual.isVarArg
    }

    private fun matchesAsCallable(declaration: KtCallableDeclaration, candidate: KtCallableDeclaration): Boolean {
        if (declaration.name != candidate.name) return false
        if (declaration.isExtension != candidate.isExtension) return false
        if (declaration.typeParameters.size != candidate.typeParameters.size) return false
        return declaration.typeParameters.zippedAll(candidate.typeParameters) { expected, actual ->
            actual.name == expected.name
        }
    }

    private fun findClassLikeDeclaration(
        declaration: KtClassLikeDeclaration,
        indexHelper: IndexHelper,
    ): KtClassLikeDeclaration? {
        val classId = declaration.getClassId() ?: return null
        return when (declaration) {
            is KtClassOrObject -> indexHelper.classFromIndexByClassId(classId)
            is KtTypeAlias -> indexHelper.typeAliasFromIndexByClassId(classId)
        }
    }
}
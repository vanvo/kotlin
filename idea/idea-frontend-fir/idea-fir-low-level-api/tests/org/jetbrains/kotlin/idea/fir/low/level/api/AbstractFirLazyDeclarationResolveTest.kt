/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.low.level.api

import org.jetbrains.kotlin.fir.FirRenderer
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.idea.fir.low.level.api.api.FirModuleResolveState
import org.jetbrains.kotlin.idea.fir.low.level.api.api.withFirDeclaration
import org.jetbrains.kotlin.idea.fir.low.level.api.compiler.based.FrontendApiSingleTestDataFileTest
import org.jetbrains.kotlin.idea.fir.low.level.api.lazy.resolve.FirLazyDeclarationResolver
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices

/**
 * Test that we do not resolve declarations we do not need & do not build bodies for them
 */
abstract class AbstractFirLazyDeclarationResolveTest : FrontendApiSingleTestDataFileTest() {

    override fun TestConfigurationBuilder.configureTest() {
        defaultDirectives {
            +JvmEnvironmentConfigurationDirectives.WITH_STDLIB
            +JvmEnvironmentConfigurationDirectives.WITH_REFLECT
        }
    }

    override fun doTest(ktFile: KtFile, module: TestModule, resolveState: FirModuleResolveState, testServices: TestServices) {
        val lazyDeclarations = ktFile.collectDescendantsOfType<KtDeclaration> { ktDeclaration ->
            FirLazyDeclarationResolver.declarationCanBeLazilyResolved(ktDeclaration)
        }

        val declarationToResolve = lazyDeclarations.firstOrNull { it.name?.lowercase() == "resolveme" }
            ?: error("declaration with name `resolveMe` was not found")
        check(resolveState is FirModuleResolveStateImpl)
        val rendered = declarationToResolve.withFirDeclaration(
            resolveState,
            FirResolvePhase.BODY_RESOLVE
        ) @Suppress("UNUSED_ANONYMOUS_PARAMETER") { firDeclaration ->
            val firFile = resolveState.getOrBuildFirFile(ktFile)
            firFile.render(FirRenderer.RenderMode.WithResolvePhases)
        }
        KotlinTestUtils.assertEqualsToFile(testDataFileSibling(".txt"), rendered)
    }
}
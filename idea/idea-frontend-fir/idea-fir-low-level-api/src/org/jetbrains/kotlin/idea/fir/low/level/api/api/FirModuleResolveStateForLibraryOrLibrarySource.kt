/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.low.level.api.api

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirPsiDiagnostic
import org.jetbrains.kotlin.fir.builder.RawFirBuilder
import org.jetbrains.kotlin.fir.builder.RawFirBuilderMode
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.scopes.wrapScopeWithJvmMapped
import org.jetbrains.kotlin.fir.resolve.symbolProvider
import org.jetbrains.kotlin.fir.scopes.FirKotlinScopeProvider
import org.jetbrains.kotlin.idea.caches.project.IdeaModuleInfo
import org.jetbrains.kotlin.idea.fir.low.level.api.FirPhaseRunner
import org.jetbrains.kotlin.idea.fir.low.level.api.annotations.InternalForInline
import org.jetbrains.kotlin.idea.fir.low.level.api.element.builder.FirTowerContextProvider
import org.jetbrains.kotlin.idea.fir.low.level.api.element.builder.FirTowerDataContextAllElementsCollector
import org.jetbrains.kotlin.idea.fir.low.level.api.file.builder.ModuleFileCache
import org.jetbrains.kotlin.idea.fir.low.level.api.file.structure.FirElementsRecorder
import org.jetbrains.kotlin.idea.fir.low.level.api.file.structure.KtToFirMapping
import org.jetbrains.kotlin.idea.fir.low.level.api.sessions.FirIdeSessionProvider
import org.jetbrains.kotlin.idea.fir.low.level.api.util.FirDeclarationForCompiledElementSearcher
import org.jetbrains.kotlin.idea.fir.low.level.api.util.checkCanceled
import org.jetbrains.kotlin.idea.util.getElementTextInContext
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import java.util.concurrent.ConcurrentHashMap

class FirModuleResolveStateForLibraryOrLibrarySource(
    override val project: Project,
    override val rootModuleSession: FirSession,
    override val moduleInfo: IdeaModuleInfo,
    private val sessionProvider: FirIdeSessionProvider,
) : FirIdeRootModuleResolveState() {
    private class ResolvedFile(
        val firFile: FirFile,
        val mapping: KtToFirMapping,
        val collector: FirTowerDataContextAllElementsCollector,
    )

    private val kfFileToFirCache = ConcurrentHashMap<KtFile, ResolvedFile>()
    override val kotlinScopeProvider = FirKotlinScopeProvider(::wrapScopeWithJvmMapped)
    private val firPhaseRunner = FirPhaseRunner()

    override fun getSessionFor(moduleInfo: IdeaModuleInfo): FirSession {
        return sessionProvider.getSession(moduleInfo)!!
    }

    override fun getTowerContextProvider(ktFile: KtFile): FirTowerContextProvider {
        return kfFileToFirCache.getValue(ktFile).collector
    }

    override fun getOrBuildFirFor(element: KtElement): FirElement {
        val file = element.containingKtFile
        val resolved = kfFileToFirCache.computeIfAbsent(file) {
            val scopeSession = ScopeSession()
            val firFile = RawFirBuilder(rootModuleSession, kotlinScopeProvider, RawFirBuilderMode.lazyBodies(false)).buildFirFile(file)

            val collector = FirTowerDataContextAllElementsCollector()

            var currentPhase = FirResolvePhase.RAW_FIR
            while (currentPhase < FirResolvePhase.BODY_RESOLVE) {
                checkCanceled()
                currentPhase = currentPhase.next
                if (currentPhase.pluginPhase || currentPhase == FirResolvePhase.SEALED_CLASS_INHERITORS) continue
                firPhaseRunner.runPhase(firFile, currentPhase, scopeSession, collector)
            }
            ResolvedFile(
                firFile,
                KtToFirMapping(firFile, FirElementsRecorder()),
                collector
            )
        }
        return resolved.mapping.getFirOfClosestParent(element, this)
            ?: return resolved.firFile
    }

    override fun getOrBuildFirFile(ktFile: KtFile): FirFile {
        return kfFileToFirCache[ktFile]!!.firFile
    }

    override fun tryGetCachedFirFile(declaration: FirDeclaration, cache: ModuleFileCache): FirFile? {
        return null
    }

    override fun getDiagnostics(element: KtElement, filter: DiagnosticCheckerFilter): List<FirPsiDiagnostic<*>> {
        return emptyList()
    }

    override fun collectDiagnosticsForFile(ktFile: KtFile, filter: DiagnosticCheckerFilter): Collection<FirPsiDiagnostic<*>> {
        return emptyList()
    }

    @InternalForInline
    override fun findSourceFirDeclaration(ktDeclaration: KtDeclaration): FirDeclaration {
        TODO("Not yet implemented")
    }

    @InternalForInline
    override fun findSourceFirDeclaration(ktDeclaration: KtLambdaExpression): FirDeclaration {
        TODO("Not yet implemented")
    }

    override fun <D : FirDeclaration> resolveFirToPhase(declaration: D, toPhase: FirResolvePhase): D {
        return declaration
    }

    @OptIn(InternalForInline::class)
    override fun findSourceFirCompiledDeclaration(ktDeclaration: KtDeclaration): FirDeclaration {
        require(ktDeclaration.containingKtFile.isCompiled) {
            "This method will only work on compiled declarations, but this declaration is not compiled: ${ktDeclaration.getElementTextInContext()}"
        }

        val searcher = FirDeclarationForCompiledElementSearcher(rootModuleSession.symbolProvider)

        return searcher.findForNonLocalDeclaration(ktDeclaration)
    }
}
/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.low.level.api

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.caches.project.IdeaModuleInfo
import org.jetbrains.kotlin.idea.caches.project.LibraryInfo
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo
import org.jetbrains.kotlin.idea.fir.low.level.api.api.FirModuleResolveState
import org.jetbrains.kotlin.idea.fir.low.level.api.api.FirModuleResolveStateForLibrary
import org.jetbrains.kotlin.idea.fir.low.level.api.lazy.resolve.FirLazyDeclarationResolver
import org.jetbrains.kotlin.idea.fir.low.level.api.sessions.FirIdeSession
import org.jetbrains.kotlin.idea.fir.low.level.api.sessions.FirIdeSessionProviderStorage
import org.jetbrains.kotlin.idea.fir.low.level.api.sessions.FirIdeSourcesSession
import org.jetbrains.kotlin.idea.util.cachedValue
import org.jetbrains.kotlin.idea.util.getValue
import org.jetbrains.kotlin.trackers.createProjectWideOutOfBlockModificationTracker
import java.util.concurrent.ConcurrentHashMap

internal class FirIdeResolveStateService(project: Project) {
    private val sessionProviderStorage = FirIdeSessionProviderStorage(project)

    private val stateCache by cachedValue(
        project,
        project.createProjectWideOutOfBlockModificationTracker(),
        ProjectRootModificationTracker.getInstance(project),
    ) {
        ConcurrentHashMap<IdeaModuleInfo, FirModuleResolveState>()
    }

    fun getResolveState(moduleInfo: IdeaModuleInfo): FirModuleResolveState =
        stateCache.computeIfAbsent(moduleInfo) { createResolveStateFor(moduleInfo, sessionProviderStorage) }

    companion object {
        fun getInstance(project: Project): FirIdeResolveStateService = project.service()

        internal fun createResolveStateFor(
            moduleInfo: IdeaModuleInfo,
            sessionProviderStorage: FirIdeSessionProviderStorage,
            configureSession: (FirIdeSession.() -> Unit)? = null,
        ): FirModuleResolveState {
            val sessionProvider = sessionProviderStorage.getSessionProvider(moduleInfo, configureSession)
            return when (moduleInfo) {
                is ModuleSourceInfo -> {
                    val firFileBuilder = (sessionProvider.rootModuleSession as FirIdeSourcesSession).firFileBuilder
                    FirModuleResolveStateImpl(
                        moduleInfo.project,
                        moduleInfo,
                        sessionProvider,
                        firFileBuilder,
                        FirLazyDeclarationResolver(firFileBuilder),
                    )
                }
                is LibraryInfo -> {
                    FirModuleResolveStateForLibrary(
                        moduleInfo.project, sessionProvider.rootModuleSession, moduleInfo, sessionProvider
                    )
                }
                else -> {
                    error("Creating FirModuleResolveState is not yet supported for $moduleInfo")
                }
            }
        }
    }
}

@TestOnly
fun createResolveStateForNoCaching(
    moduleInfo: IdeaModuleInfo,
    configureSession: (FirIdeSession.() -> Unit)? = null,
): FirModuleResolveState =
    FirIdeResolveStateService.createResolveStateFor(
        moduleInfo = moduleInfo,
        sessionProviderStorage = FirIdeSessionProviderStorage(moduleInfo.project!!),
        configureSession = configureSession
    )



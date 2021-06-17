/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.low.level.api.sessions

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.fir.BuiltinTypes
import org.jetbrains.kotlin.fir.FirModuleData
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.session.FirModuleInfoBasedModuleData
import org.jetbrains.kotlin.idea.caches.project.*
import org.jetbrains.kotlin.idea.fir.low.level.api.FirPhaseRunner
import org.jetbrains.kotlin.idea.fir.low.level.api.util.addValueFor
import org.jetbrains.kotlin.idea.fir.low.level.api.util.executeWithoutPCE
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.trackers.createModuleWithoutDependenciesOutOfBlockModificationTracker
import java.util.concurrent.ConcurrentHashMap

internal class FirIdeSessionProviderStorage(private val project: Project) {
    private val sessionsCache = ConcurrentHashMap<IdeaModuleInfo, FromModuleViewSessionCache>()

    private val librariesCache by cachedValue(project, LibraryModificationTracker.getInstance(project)) { LibrariesCache() }

    fun getSessionProvider(
        rootModule: IdeaModuleInfo,
        configureSession: (FirIdeSession.() -> Unit)? = null
    ): FirIdeSessionProvider {
        val firPhaseRunner = FirPhaseRunner()

        val builtinTypes = BuiltinTypes()
        val builtinsAndCloneableSession = FirIdeSessionFactory.createBuiltinsAndCloneableSession(project, builtinTypes)
        val cache = sessionsCache.getOrPut(rootModule) { FromModuleViewSessionCache(rootModule) }
        val (sessions, session) = cache.withMappings { mappings ->
            val sessions = mutableMapOf<IdeaModuleInfo, FirIdeResolvableModuleSession>().apply { putAll(mappings) }
            val session = executeWithoutPCE {
                when (rootModule) {
                    is ModuleSourceInfo -> {
                        FirIdeSessionFactory.createSourcesSession(
                            project,
                            rootModule,
                            builtinsAndCloneableSession,
                            firPhaseRunner,
                            cache.sessionInvalidator,
                            builtinTypes,
                            sessions,
                            isRootModule = true,
                            librariesCache,
                            configureSession = configureSession,
                        )
                    }
                    is LibraryOrLibrarySourceInfo -> FirIdeSessionFactory.createResolvableLibrarySession(
                        rootModule,
                        project,
                        builtinTypes,
                        builtinsAndCloneableSession,
                        sessions,
                        configureSession = configureSession,
                    )
                    else -> error("Unexpected ${rootModule::class.simpleName}")
                }

            }
            sessions to session
        }

        return FirIdeSessionProvider(project, session, sessions)
    }
}

private class FromModuleViewSessionCache(
    val root: IdeaModuleInfo,
) {
    @Volatile
    private var mappings: PersistentMap<IdeaModuleInfo, FirSessionWithModificationTracker> = persistentMapOf()

    val sessionInvalidator: FirSessionInvalidator = FirSessionInvalidator { session ->
        mappings[session.moduleData.moduleSourceInfo]?.invalidate()
    }


    inline fun <R> withMappings(
        action: (Map<IdeaModuleInfo, FirIdeResolvableModuleSession>) -> Pair<Map<IdeaModuleInfo, FirIdeResolvableModuleSession>, R>
    ): Pair<Map<IdeaModuleInfo, FirIdeSession>, R> {
        val (newMappings, result) = action(getSessions().mapValues { it.value })
        mappings = newMappings.mapValues { FirSessionWithModificationTracker(it.value) }.toPersistentMap()
        return newMappings to result
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getSessions(): Map<IdeaModuleInfo, FirIdeResolvableModuleSession> = buildMap {
        val sessions = mappings.values
        val wasSessionInvalidated = sessions.associateWithTo(hashMapOf()) { false }

        val reversedDependencies = sessions.reversedDependencies { session ->
            session.firSession.moduleData.moduleSourceInfo.dependencies().mapNotNull { mappings[it] }
        }

        fun markAsInvalidWithDfs(session: FirSessionWithModificationTracker) {
            if (wasSessionInvalidated.getValue(session)) {
                // we already was in that branch
                return
            }
            wasSessionInvalidated[session] = true
            reversedDependencies[session]?.forEach { dependsOn ->
                markAsInvalidWithDfs(dependsOn)
            }
        }

        for (session in sessions) {
            if (!session.isValid) {
                markAsInvalidWithDfs(session)
            }
        }
        return wasSessionInvalidated.entries
            .mapNotNull { (session, wasInvalidated) -> session.takeUnless { wasInvalidated } }
            .associate { session -> session.firSession.moduleData.moduleSourceInfo to session.firSession }
    }

    private fun <T> Collection<T>.reversedDependencies(getDependencies: (T) -> List<T>): Map<T, List<T>> {
        val result = hashMapOf<T, MutableList<T>>()
        forEach { from ->
            getDependencies(from).forEach { to ->
                result.addValueFor(to, from)
            }
        }
        return result
    }
}

private class FirSessionWithModificationTracker(
    val firSession: FirIdeResolvableModuleSession,
) {
    private val modificationTracker =
        when (val moduleInfo = firSession.moduleData.moduleSourceInfo) {
            is ModuleSourceInfo -> moduleInfo.module.createModuleWithoutDependenciesOutOfBlockModificationTracker()
            else -> ModificationTracker.NEVER_CHANGED
        }


    private val timeStamp = modificationTracker.modificationCount

    @Volatile
    private var isInvalidated = false

    fun invalidate() {
        isInvalidated = true
    }

    val isValid: Boolean get() = !isInvalidated && modificationTracker.modificationCount == timeStamp
}

val FirModuleData.moduleSourceInfo: IdeaModuleInfo
    get() = moduleInfoUnsafe()

inline fun <reified T : ModuleInfo> FirModuleData.moduleInfoUnsafe(): T = (this as FirModuleInfoBasedModuleData).moduleInfo as T
inline fun <reified T : ModuleInfo> FirModuleData.moduleInfoSafe(): T? = (this as FirModuleInfoBasedModuleData).moduleInfo as? T

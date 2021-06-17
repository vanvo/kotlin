/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.platform.isCommon

@OptIn(ExperimentalStdlibApi::class)
fun getLibrarySourceScope(virtualFileFromLibrary: VirtualFile, project: Project): GlobalSearchScope {
    val binaryModuleInfos = getBinaryLibrariesModuleInfos(project, virtualFileFromLibrary)
    val scopes =  buildList {
        binaryModuleInfos
            .mapNotNullTo(this) { it.sourcesModuleInfo?.sourceScope() }
        binaryModuleInfos
            .flatMap { it.associatedCommonLibraries() }
            .mapNotNullTo(this) { it.sourcesModuleInfo?.sourceScope() }
    }
    if (scopes.isEmpty()) {
        return GlobalSearchScope.EMPTY_SCOPE
    }
    return GlobalSearchScope.union(scopes)
}

private fun BinaryModuleInfo.associatedCommonLibraries(): List<BinaryModuleInfo> {
    val platform = platform
    if (platform.isCommon()) return emptyList()

    return dependencies().filterIsInstance<BinaryModuleInfo>().filter {
        it.platform.isCommon()
    }
}
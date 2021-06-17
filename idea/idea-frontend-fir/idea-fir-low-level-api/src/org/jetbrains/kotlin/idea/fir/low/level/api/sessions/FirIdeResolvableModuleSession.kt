/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.low.level.api.sessions

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.fir.BuiltinTypes
import org.jetbrains.kotlin.idea.caches.project.LibraryOrLibrarySourceInfo

abstract class FirIdeResolvableModuleSession(
    builtinTypes: BuiltinTypes,
) : FirIdeModuleSession(builtinTypes)


class FirIdeLibraryOrLibrarySourceResolvableModuleSession(
    val moduleInfo: LibraryOrLibrarySourceInfo,
    override val project: Project,
    override val scope: GlobalSearchScope,
    builtinTypes: BuiltinTypes,
) : FirIdeResolvableModuleSession(builtinTypes)
/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization

import org.jetbrains.kotlin.backend.common.serialization.KotlinIrLinkerIssue.UserVisibleIrModuleId.Companion.KOTLIN_LIBRARY_PREFIX
import org.jetbrains.kotlin.backend.common.serialization.KotlinIrLinkerIssue.UserVisibleIrModuleId.Companion.KOTLIN_NATIVE_PLATFORM_LIBRARY_PREFIX
import org.jetbrains.kotlin.backend.common.serialization.KotlinIrLinkerIssue.UserVisibleIrModuleKind.KOTLIN_NATIVE_PLATFORM_LIBRARY
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.konan.CurrentKlibModuleOrigin
import org.jetbrains.kotlin.descriptors.konan.DeserializedKlibModuleOrigin
import org.jetbrains.kotlin.descriptors.konan.KlibModuleOrigin
import org.jetbrains.kotlin.descriptors.konan.SyntheticModulesOrigin
import org.jetbrains.kotlin.ir.linkage.KotlinIrLinkerInternalException
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.IrMessageLogger
import org.jetbrains.kotlin.library.KOTLIN_STDLIB_NAME
import org.jetbrains.kotlin.library.uniqueName
import org.jetbrains.kotlin.library.unresolvedDependencies
import org.jetbrains.kotlin.name.Name

abstract class KotlinIrLinkerIssue {
    protected abstract val message: String

    fun raiseIssue(messageLogger: IrMessageLogger): KotlinIrLinkerInternalException {
        messageLogger.report(IrMessageLogger.Severity.ERROR, message, null)
        throw KotlinIrLinkerInternalException
    }

    // Known kinds of IR modules (libraries) to be displayed in error output.
    protected enum class UserVisibleIrModuleKind(val isKotlinLibrary: Boolean) {
        KOTLIN_STANDARD_LIBRARY(true),
        KOTLIN_NATIVE_PLATFORM_LIBRARY(true),
        KOTLIN_OTHER_LIBRARY(true),
        THIRD_PARTY_LIBRARY(false);

        companion object {
            fun getByUniqueName(uniqueName: String): UserVisibleIrModuleKind = when {
                uniqueName == KOTLIN_STDLIB_NAME -> KOTLIN_STANDARD_LIBRARY
                uniqueName.startsWith(KOTLIN_NATIVE_PLATFORM_LIBRARY_PREFIX) -> KOTLIN_NATIVE_PLATFORM_LIBRARY
                uniqueName.startsWith(KOTLIN_LIBRARY_PREFIX) -> KOTLIN_OTHER_LIBRARY
                else -> THIRD_PARTY_LIBRARY
            }
        }
    }

    // Identifier of IR module (library) to be displayed in error output.
    protected data class UserVisibleIrModuleId(val uniqueName: String, val kind: UserVisibleIrModuleKind) {
        constructor(uniqueName: String) : this(uniqueName, UserVisibleIrModuleKind.getByUniqueName(uniqueName))

        val hasSimpleName = uniqueName.none { it == '.' || it == ':' }
        override fun toString() = uniqueName

        companion object {
            const val KOTLIN_NATIVE_PLATFORM_LIBRARY_PREFIX = "org.jetbrains.kotlin.native.platform."
            const val KOTLIN_LIBRARY_PREFIX = "org.jetbrains.kotlin"
        }
    }

    // Identifier of concrete IR module (library) version to be displayed in error output.
    protected data class UserVisibleIrModuleIdAndVersion(val id: UserVisibleIrModuleId, val version: String?) {
        constructor(uniqueName: String, version: String?) : this(UserVisibleIrModuleId(uniqueName), version)

        override fun toString() = if (version != null) "$id:$version" else id.toString()

        fun getDifference(other: UserVisibleIrModuleIdAndVersion): String? {
            check(id == other.id) { "Can't compute the difference between two different modules: $this vs $other" }
            return if (version == other.version) null else "$id:${version ?: UNKNOWN_VERSION} -> ${other.version ?: UNKNOWN_VERSION}"
        }

        companion object {
            const val UNKNOWN_VERSION = "unknown"
        }
    }

    // IR module (library) with the set of dependencies.
    protected class UserVisibleIrModuleInfo(
        val idAndVersion: UserVisibleIrModuleIdAndVersion,
        val dependencies: Set<UserVisibleIrModuleIdAndVersion>
    ) {
        override fun toString() = idAndVersion.id.toString()
    }

    // returns null if this module should not be displayed to used in IR linker error message
    protected val ModuleDescriptor.userVisibleIrModuleInfo: UserVisibleIrModuleInfo?
        get() = when (val moduleOrigin = getCapability(KlibModuleOrigin.CAPABILITY)) {
            is CurrentKlibModuleOrigin -> {
                // The main module might leak to dependencies of other modules via built-ins in Kotlin/Native,
                // however we should not show it to the user.
                null
            }
            is SyntheticModulesOrigin -> null // Exclude forward declarations module in Kotlin/Native.
            is DeserializedKlibModuleOrigin -> {
                val library = moduleOrigin.library
                UserVisibleIrModuleInfo(
                    idAndVersion = UserVisibleIrModuleIdAndVersion(library.uniqueName, library.versions.libraryVersion),
                    dependencies = library.unresolvedDependencies.mapTo(mutableSetOf()) { unresolvedLibrary ->
                        UserVisibleIrModuleIdAndVersion(unresolvedLibrary.path, unresolvedLibrary.libraryVersion)
                    }
                )
            }
            null -> {
                // A fallback for non-Native backends.
                UserVisibleIrModuleInfo(
                    idAndVersion = temporaryFallbackIdAndVersion,
                    dependencies = allDependencyModules.filter { module ->
                        module != this // Don't show the module itself in the list of dependencies.
                    }.mapTo(mutableSetOf()) { it.temporaryFallbackIdAndVersion }
                )
            }
        }

    // TODO: support extracting all the necessary details for non-Native libs
    protected val ModuleDescriptor.temporaryFallbackIdAndVersion: UserVisibleIrModuleIdAndVersion
        get() = UserVisibleIrModuleIdAndVersion(name.asStringStripSpecialMarkers(), version = null)

    protected fun StringBuilder.appendIrModules(
        message: String,
        allModuleInfos: Collection<UserVisibleIrModuleInfo>,
        starredModuleId: UserVisibleIrModuleId? = null
    ) {
        append(message).append(':')
        if (allModuleInfos.isEmpty()) {
            append(" <empty>")
            return
        }

        val irModulesForRendering = computeIrModulesForRendering(
            allModuleInfos,
            starredModuleId,
            isModuleToCompress = { kind == KOTLIN_NATIVE_PLATFORM_LIBRARY }, // TODO: maybe move K/N-specific logic to KonanIrLinker?
            compressedModuleName = { "$KOTLIN_NATIVE_PLATFORM_LIBRARY_PREFIX* (${it.size} libraries)" }
        )

        fun appendModuleRowStart(isLastModuleRow: Boolean) {
            append('\n')
            append(if (isLastModuleRow) "\\--- " else "+--- ")
        }

        fun appendDependencyRowStart(isLastModuleRow: Boolean, isLastDependencyRow: Boolean) {
            append('\n')
            append(if (isLastModuleRow) "     " else "|    ")
            append(if (isLastDependencyRow) "\\--- " else "+--- ")
        }

        fun appendStarIfNeeded(id: UserVisibleIrModuleId) {
            if (id == starredModuleId) append("(*) ")
        }

        irModulesForRendering.sortedWith(MODULE_ID_COMPARATOR).entries
            .forEachIndexed { moduleIndex, (moduleId: UserVisibleIrModuleId, moduleInfo: UserVisibleIrModuleInfo) ->
                val isLastModuleRow = moduleIndex == irModulesForRendering.size - 1
                appendModuleRowStart(isLastModuleRow)

                appendStarIfNeeded(moduleId)
                append(moduleInfo.idAndVersion)

                moduleInfo.dependencies.sortedWith(MODULE_ID_AND_VERSION_COMPARATOR)
                    .forEachIndexed { dependencyIndex, dependencyIdAndVersion: UserVisibleIrModuleIdAndVersion ->
                        val isLastDependencyRow = dependencyIndex == moduleInfo.dependencies.size - 1
                        appendDependencyRowStart(isLastModuleRow, isLastDependencyRow)

                        val existingDependencyInfo: UserVisibleIrModuleInfo? = irModulesForRendering[dependencyIdAndVersion.id]
                        if (existingDependencyInfo != null) {
                            appendStarIfNeeded(dependencyIdAndVersion.id)
                            append(dependencyIdAndVersion.getDifference(existingDependencyInfo.idAndVersion) ?: dependencyIdAndVersion)
                        } else {
                            append("[ERROR: Missing module] $dependencyIdAndVersion")
                        }
                    }
            }
    }

    private class UserVisibleIrModulesForRendering(
        private val factualModuleInfos: Map<UserVisibleIrModuleId, UserVisibleIrModuleInfo>,
        private val visibleModuleInfos: Map<UserVisibleIrModuleId, UserVisibleIrModuleInfo>
    ) {
        constructor(factualModuleInfos: Map<UserVisibleIrModuleId, UserVisibleIrModuleInfo>) : this(factualModuleInfos, factualModuleInfos)

        operator fun get(moduleId: UserVisibleIrModuleId): UserVisibleIrModuleInfo? = factualModuleInfos[moduleId]

        val size: Int = visibleModuleInfos.size
        fun sortedWith(comparator: Comparator<UserVisibleIrModuleId>): Map<UserVisibleIrModuleId, UserVisibleIrModuleInfo> =
            visibleModuleInfos.toSortedMap(comparator)
    }

    private fun computeIrModulesForRendering(
        allModuleInfos: Collection<UserVisibleIrModuleInfo>,
        starredModuleId: UserVisibleIrModuleId?,
        isModuleToCompress: UserVisibleIrModuleId.() -> Boolean,
        compressedModuleName: (Map<UserVisibleIrModuleId, UserVisibleIrModuleInfo>) -> String
    ): UserVisibleIrModulesForRendering {
        val factualModuleInfos: Map<UserVisibleIrModuleId, UserVisibleIrModuleInfo> = allModuleInfos.associateBy { it.idAndVersion.id }

        // Compress a subgraph of dependencies to a single node to avoid unnecessary "noise" in error message:
        if (starredModuleId?.isModuleToCompress() != true) { // Starred module is not a module in compressed subgraph.
            val modulesToCompress = factualModuleInfos.filterKeys { it.isModuleToCompress() }
            if (modulesToCompress.size >= 2) { // There are at least two nodes in the subgraph.
                val arbitraryModuleVersion = modulesToCompress.values.first().idAndVersion.version
                if (modulesToCompress.values.all { it.idAndVersion.version == arbitraryModuleVersion }) {
                    // All nodes in the subgraph have the same version.

                    // Graph - subgraph:
                    val factualModuleInfosMinusModulesToCompress = factualModuleInfos - modulesToCompress.keys

                    // Check the arcs leading from outside of the subgraph into the subgraph.
                    val modulesToCompressInDependencies: Set<UserVisibleIrModuleIdAndVersion> =
                        factualModuleInfosMinusModulesToCompress.values.flatMapTo(mutableSetOf()) { moduleInfo ->
                            moduleInfo.dependencies.filter { it.id.isModuleToCompress() }
                        }
                    if (modulesToCompressInDependencies.all { it.id in factualModuleInfos && it.version == arbitraryModuleVersion }) {
                        // There are no missing nodes in the subgraph. Also all arcs lead to nodes of the same version.

                        val commonDependencies = modulesToCompress.values.map { moduleToCompress ->
                            moduleToCompress.dependencies.filter { !it.id.isModuleToCompress() }.toSet()
                        }.reduce { a, b -> a intersect b }

                        val compressedModuleInfo = UserVisibleIrModuleInfo(
                            idAndVersion = UserVisibleIrModuleIdAndVersion(
                                uniqueName = compressedModuleName(modulesToCompress),
                                version = arbitraryModuleVersion
                            ),
                            dependencies = commonDependencies
                        )

                        return UserVisibleIrModulesForRendering(
                            factualModuleInfos = factualModuleInfos,
                            visibleModuleInfos = factualModuleInfosMinusModulesToCompress + (compressedModuleInfo.idAndVersion.id to compressedModuleInfo)
                        )
                    }
                }
            }
        }

        return UserVisibleIrModulesForRendering(factualModuleInfos)
    }

    companion object {
        private val MODULE_ID_COMPARATOR = Comparator<UserVisibleIrModuleId> { left: UserVisibleIrModuleId, right: UserVisibleIrModuleId ->
            when {
                left == right -> 0
                // Kotlin libs go lower.
                left.kind.isKotlinLibrary && !right.kind.isKotlinLibrary -> 1
                !left.kind.isKotlinLibrary && right.kind.isKotlinLibrary -> -1
                // Modules with simple names go upper as they are most likely user-made libs.
                left.hasSimpleName && !right.hasSimpleName -> -1
                !left.hasSimpleName && right.hasSimpleName -> 1
                // Else: just compare by name.
                else -> left.uniqueName.compareTo(right.uniqueName)
            }
        }

        private val MODULE_ID_AND_VERSION_COMPARATOR =
            Comparator<UserVisibleIrModuleIdAndVersion> { left: UserVisibleIrModuleIdAndVersion, right: UserVisibleIrModuleIdAndVersion ->
                val moduleIdDiff = MODULE_ID_COMPARATOR.compare(left.id, right.id)
                when {
                    moduleIdDiff != 0 -> moduleIdDiff
                    left.version == right.version -> 0
                    left.version == null -> -1
                    right.version == null -> 1
                    else -> left.version.compareTo(right.version)
                }
            }
    }
}

class SignatureIdNotFoundInModuleWithDependencies(
    idSignature: IdSignature,
    currentModule: ModuleDescriptor,
    allModules: Collection<ModuleDescriptor>
) : KotlinIrLinkerIssue() {
    override val message = buildString {
        val currentModuleId = currentModule.userVisibleIrModuleInfo?.idAndVersion?.id
            ?: currentModule.temporaryFallbackIdAndVersion.id
        val allModuleInfos = allModules.mapNotNull { it.userVisibleIrModuleInfo }

        // cause:
        append("Module $currentModuleId has a reference to symbol ${idSignature.render()}.")
        append(" Neither the module itself nor its dependencies contain such declaration.")

        // explanation:
        append("\n\nThis could happen if the required dependency is missing in the project.")
        append(" Or if there are two (or more) dependency libraries, where one library ($currentModuleId)")
        append(" was compiled against the different version of the other library")
        append(" than the one currently used in the project.")

        // action items:
        append(" Please check that the project configuration is correct and has consistent versions of all required dependencies.")

        // the tree of dependencies:
        appendIrModules(
            message = "\n\nProject dependencies, module $currentModuleId is marked with '*'",
            allModuleInfos = allModuleInfos,
            starredModuleId = currentModuleId
        )
    }
}

class NoDeserializerForModule(moduleName: Name, idSignature: IdSignature?) : KotlinIrLinkerIssue() {
    override val message = buildString {
        append("Could not load module ${moduleName.asString()}")
        if (idSignature != null) append(" in an attempt to find deserializer for symbol ${idSignature.render()}.")
    }
}

class SymbolTypeMismatch(
    cause: IrSymbolTypeMismatchException,
    allModules: Collection<ModuleDescriptor>
) : KotlinIrLinkerIssue() {
    override val message: String = buildString {
        // cause:
        append(cause.message)

        // explanation:
        append("\n\nThis could happen if there are two (or more) dependency libraries,")
        append(" where one library was compiled against the different version of the other library")
        append(" than the one currently used in the project.")

        // action items:
        append(" Please check that the project configuration is correct and has consistent versions of dependencies.")

        // the tree of dependencies:
        appendIrModules(
            message = "\n\nProject dependencies",
            allModuleInfos = allModules.mapNotNull { it.userVisibleIrModuleInfo }
        )
    }
}

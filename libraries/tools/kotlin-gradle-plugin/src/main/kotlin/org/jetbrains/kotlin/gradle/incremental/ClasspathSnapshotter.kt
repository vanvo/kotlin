/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.incremental

import org.jetbrains.kotlin.gradle.incremental.ClasspathEntryContentsReader.Companion.DEFAULT_CLASS_FILTER
import java.io.File
import java.util.zip.ZipInputStream

@Suppress("SpellCheckingInspection")
object ClasspathEntrySnapshotter {

    fun snapshot(classpathEntry: File): ClasspathEntrySnapshot {
        val classpathEntryContents = ClasspathEntryContentsReader.create(classpathEntry).readContents(DEFAULT_CLASS_FILTER)

        val classSnapshots = LinkedHashMap<String, ClassSnapshot>()
        classpathEntryContents.mapValuesTo(classSnapshots) { (invariantSeparatorsRelativePath, classContents) ->
            ClassSnapshotter.snapshot(invariantSeparatorsRelativePath, classContents)
        }

        return ClasspathEntrySnapshot(classSnapshots)
    }
}

@Suppress("SpellCheckingInspection")
object ClassSnapshotter {

    fun snapshot(invariantSeparatorsRelativePath: String, classContents: ByteArray): ClassSnapshot {
        // TODO WORK-IN-PROGRESS
        return ClassSnapshot()
    }
}

sealed class ClasspathEntryContentsReader {

    companion object {

        val DEFAULT_CLASS_FILTER = { invariantSeparatorsRelativePath: String, isDirectory: Boolean ->
            !isDirectory
                    && invariantSeparatorsRelativePath.endsWith(".class", ignoreCase = true)
                    && !invariantSeparatorsRelativePath.endsWith("module-info.class", ignoreCase = true)
                    && !invariantSeparatorsRelativePath.startsWith("meta-inf", ignoreCase = true)
        }

        fun create(classpathEntry: File): ClasspathEntryContentsReader {
            return if (classpathEntry.isDirectory) {
                DirectoryContentsReader(classpathEntry)
            } else {
                JarContentsReader(classpathEntry)
            }
        }
    }

    /**
     * Returns a map from (Unix-like) relative paths of classes to their contents. The paths are relative to the classpath entry (directory
     * or jar).
     *
     * The map entries need to satisfy the given filter.
     *
     * The map entries are sorted based on their (Unix-like) relative paths (to ensure deterministic results across filesystems).
     *
     * Note: If a jar has duplicate entries, only the first one will be considered.
     */
    abstract fun readContents(filter: ((invariantSeparatorsRelativePath: String, isDirectory: Boolean) -> Boolean)? = null):
            LinkedHashMap<String, ByteArray>
}

class DirectoryContentsReader(private val directory: File) : ClasspathEntryContentsReader() {

    init {
        check(directory.isDirectory)
    }

    override fun readContents(
        filter: ((invariantSeparatorsRelativePath: String, isDirectory: Boolean) -> Boolean)?
    ): LinkedHashMap<String, ByteArray> {
        val relativePathsToContents: MutableList<Pair<String, ByteArray>> = mutableListOf()
        directory.walk().forEach {
            val invariantSeparatorsRelativePath = it.relativeTo(directory).invariantSeparatorsPath
            if (filter == null || filter(invariantSeparatorsRelativePath, it.isDirectory)) {
                relativePathsToContents.add(invariantSeparatorsRelativePath to it.readBytes())
            }
        }
        return relativePathsToContents.sortedBy { it.first }.toMap(LinkedHashMap())
    }
}

class JarContentsReader(private val jarFile: File) : ClasspathEntryContentsReader() {

    init {
        check(jarFile.path.endsWith(".jar", ignoreCase = true))
    }

    override fun readContents(
        filter: ((invariantSeparatorsRelativePath: String, isDirectory: Boolean) -> Boolean)?
    ): LinkedHashMap<String, ByteArray> {
        val relativePathsToContents: MutableList<Pair<String, ByteArray>> = mutableListOf()
        ZipInputStream(jarFile.inputStream().buffered()).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                if (filter == null || filter(entry.name, entry.isDirectory)) {
                    relativePathsToContents.add(entry.name to zipInputStream.readBytes())
                }
            }
        }
        return relativePathsToContents.sortedBy { it.first }.toMap(LinkedHashMap())
    }
}

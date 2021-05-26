/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.incremental

import java.io.*

class ClasspathSnapshot(val classpathEntrySnapshots: List<ClasspathEntrySnapshot>)

class ClasspathEntrySnapshot(

    /**
     * Maps (Unix-like) relative paths of classes to their snapshots. The paths are relative to the classpath entry (directory or jar).
     */
    val classSnapshots: LinkedHashMap<String, ClassSnapshot>
) : Serializable {

    companion object {
        private const val serialVersionUID = 0L
    }
}

// TODO WORK-IN-PROGRESS
class ClassSnapshot : Serializable {

    companion object {
        private const val serialVersionUID = 0L
    }
}

object ClasspathSnapshotSerializer {

    fun readFromFiles(classpathEntrySnapshotFiles: List<File>): ClasspathSnapshot {
        return ClasspathSnapshot(classpathEntrySnapshotFiles.map { ClasspathEntrySnapshotSerializer.readFromFile(it) })
    }
}

object ClasspathEntrySnapshotSerializer {

    fun readFromFile(classpathEntrySnapshotFile: File): ClasspathEntrySnapshot {
        check(classpathEntrySnapshotFile.isFile) { "`${classpathEntrySnapshotFile.path}` does not exist (or is a directory)." }
        return ObjectInputStream(FileInputStream(classpathEntrySnapshotFile).buffered()).use {
            it.readObject() as ClasspathEntrySnapshot
        }
    }

    fun writeToFile(classpathEntrySnapshot: ClasspathEntrySnapshot, classpathEntrySnapshotFile: File) {
        check(classpathEntrySnapshotFile.parentFile.exists()) { "Parent dir of `${classpathEntrySnapshotFile.path}` does not exist." }
        ObjectOutputStream(FileOutputStream(classpathEntrySnapshotFile).buffered()).use {
            it.writeObject(classpathEntrySnapshot)
        }
    }
}

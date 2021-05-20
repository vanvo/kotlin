/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.incremental

import org.jetbrains.kotlin.name.FqName
import java.io.Serializable

sealed class ClasspathChanges : Serializable {

    sealed class JVM : ClasspathChanges() {

        sealed class ClasspathSnapshotEnabled : JVM() {

            sealed class IncrementalRun : ClasspathSnapshotEnabled() {

                class Available(val lookupSymbols: Collection<LookupSymbol>, val fqNames: Collection<FqName>) : IncrementalRun() {
                    companion object {
                        private const val serialVersionUID = 0L
                    }
                }

                object UnableToCompute : IncrementalRun()
            }

            object NotAvailableForNonIncrementalRun : ClasspathSnapshotEnabled()
        }

        object NotAvailableClasspathSnapshotIsDisabled : JVM()
        object NotAvailableReservedForTestsOnly : JVM()
    }

    object NotAvailableForJS : ClasspathChanges()
}


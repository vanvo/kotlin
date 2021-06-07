/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.incremental

import org.jetbrains.kotlin.name.FqName
import java.io.Serializable

sealed class ClasspathChanges : Serializable {

    class Available(val lookupSymbols: Collection<LookupSymbol>, val fqNames: Collection<FqName>) : ClasspathChanges() {
        companion object {
            private const val serialVersionUID = 0L
        }
    }

    sealed class NotAvailable : ClasspathChanges() {
        object UnableToCompute : NotAvailable()
        object ForNonIncrementalRun : NotAvailable()
        object ClasspathSnapshotIsDisabled : NotAvailable()
        object ReservedForTestsOnly : NotAvailable()
        object ForJSCompiler : NotAvailable()
    }
}

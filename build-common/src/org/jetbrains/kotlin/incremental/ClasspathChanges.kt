/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.incremental

import org.jetbrains.kotlin.name.FqName
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

sealed class ClasspathChanges : Serializable {

    class Available() : ClasspathChanges() {

        lateinit var lookupSymbols: List<LookupSymbol>
            private set

        lateinit var fqNames: List<FqName>
            private set

        constructor(lookupSymbols: List<LookupSymbol>, fqNames: List<FqName>) : this() {
            this.lookupSymbols = lookupSymbols
            this.fqNames = fqNames
        }

        companion object {
            private const val serialVersionUID = 0L
        }

        private fun writeObject(out: ObjectOutputStream) {
            out.writeInt(lookupSymbols.size)
            lookupSymbols.forEach {
                out.writeUTF(it.name)
                out.writeUTF(it.scope)
            }

            out.writeInt(fqNames.size)
            fqNames.forEach {
                out.writeUTF(it.asString())
            }
        }

        private fun readObject(ois: ObjectInputStream) {
            val lookupSymbols = mutableListOf<LookupSymbol>()
            repeat(ois.readInt()) {
                val name = ois.readUTF()
                val scope = ois.readUTF()
                lookupSymbols.add(LookupSymbol(name, scope))
            }
            this.lookupSymbols = lookupSymbols

            val fqNames = mutableListOf<FqName>()
            repeat(ois.readInt()) {
                val fqNameString = ois.readUTF()
                fqNames.add(FqName(fqNameString))
            }
            this.fqNames = fqNames
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

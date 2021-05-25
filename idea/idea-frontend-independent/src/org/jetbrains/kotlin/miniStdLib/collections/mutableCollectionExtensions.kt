/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.miniStdLib.collections

fun <E : Any> MutableCollection<E>.addIfNotNull(element: E?) {
    if (element != null) {
        add(element)
    }
}
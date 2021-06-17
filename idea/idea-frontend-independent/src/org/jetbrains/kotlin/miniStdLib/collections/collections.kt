/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.miniStdLib.collections

inline fun <T, R, V> Iterable<T>.zipFold(other: Iterable<R>, initial: V, fold: (acc: V, a: T, b: R) -> V): V {
    val first = iterator()
    val second = other.iterator()
    var result: V = initial
    while (first.hasNext() && second.hasNext()) {
        result = fold(result, first.next(), second.next())
    }
    return result
}


inline fun <T, R> Iterable<T>.zippedAll(other: Iterable<R>, matcher: (a: T, b: R) -> Boolean): Boolean =
    zipFold(other, true) { result, a, b -> result && matcher(a, b) }

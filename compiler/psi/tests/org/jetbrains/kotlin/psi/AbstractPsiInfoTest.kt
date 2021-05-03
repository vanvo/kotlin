/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi

import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.testFramework.KtParsingTestCase
import org.jetbrains.kotlin.test.util.KtTestUtil
import java.io.File
import kotlin.reflect.KClass

abstract class AbstractPsiInfoTest : KtParsingTestCase(
    "",
    "kt",
    KotlinParserDefinition()
) {
    override fun doTest(path: String) {
        val testDataFile = File(path)
        val text = KtTestUtil.doLoadFile(testDataFile)
        val ktFile = createFile("$name.kt", text) as KtFile
        val ktInfos = ktFile.collectDescendantsOfType<KtDeclaration>()
            .mapNotNull { ktDeclaration ->
                val declarationInfos = renderInfos(ktDeclaration, infos)
                declarationInfos?.let { ktDeclaration to declarationInfos }
            }.toMap()
        val withInfos = InfoCommentHandler.renderWithCommentInfos(ktFile, ktInfos)
        KotlinTestUtils.assertEqualsToFile(testDataFile, withInfos)
    }

    private val infos: List<KtDeclarationInfo> = listOf(
        info<KtClassLikeDeclaration>("classId") { it.getClassId().toString() },
        info<KtNamedDeclaration>("hasNonLocalFqName") { it.hasNonLocalFqName().toString() },
    )
}

private data class KtDeclarationInfo(
    val kClass: KClass<out KtDeclaration>,
    val name: String,
    val retriever: (KtDeclaration) -> String,
)

@Suppress("UNCHECKED_CAST")
private inline fun <reified D : KtDeclaration> info(name: String, noinline retriever: (D) -> String) =
    KtDeclarationInfo(D::class, name, retriever as (KtDeclaration) -> String)

private fun renderInfos(declaration: KtDeclaration, infos: List<KtDeclarationInfo>): String? {
    val allInfos = infos.filter { info -> info.kClass.isInstance(declaration) }
    if (allInfos.isEmpty()) return null
    val renderedInfos = allInfos.joinToString { info ->
        "${info.name}=${info.retriever(declaration)}"
    }
    return "${declaration::class.simpleName}[$renderedInfos]"
}

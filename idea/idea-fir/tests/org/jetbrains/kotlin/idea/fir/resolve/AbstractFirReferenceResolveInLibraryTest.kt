/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir.resolve

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.io.delete
import com.intellij.util.io.write
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.caches.project.LibraryInfo
import org.jetbrains.kotlin.idea.caches.project.getModuleInfo
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.decompiler.KotlinDecompiledFileViewProvider
import org.jetbrains.kotlin.idea.decompiler.classFile.KtClsFile
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveTest
import org.jetbrains.kotlin.idea.search.getKotlinFqName
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.idea.test.SdkAndMockLibraryProjectDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.utils.IgnoreTests
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

abstract class AbstractFirReferenceResolveInLibraryTest(private val inSources: Boolean) : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    private var tempLibraryDir: Path? = null


    override fun getProjectDescriptor(): KotlinLightProjectDescriptor {
        if (PluginTestCaseBase.isAllFilesPresentTest(getTestName(true))) {
            return KotlinLightProjectDescriptor.INSTANCE
        }
        return SdkAndMockLibraryProjectDescriptor(tempLibraryDir!!.toString(), inSources, true, false, false)
    }

    override fun setUp() {
        if (!PluginTestCaseBase.isAllFilesPresentTest(getTestName(true))) {
            tempLibraryDir = Files.createTempDirectory(fileName())
            val fileText = Paths.get(testPath()).readText()
            (tempLibraryDir!! / fileName()).createFile().write(fileText)
        }
        super.setUp()
    }

    override fun tearDown() {
        if (!PluginTestCaseBase.isAllFilesPresentTest(getTestName(true))) {
            project.invalidateCaches(myFixture.file as? KtFile)
            tempLibraryDir?.delete(recursively = true)
            tempLibraryDir = null
        }
        super.tearDown()
    }

    fun doTest(path: String) {
        val file = myFixture.configureByText("fake.kt", "val x = 1") as KtFile
        val library: Library = file.getModuleInfo().dependencies().filterIsInstance<LibraryInfo>().first().library
        doTest(library, path)
    }

    protected abstract fun doTest(library: Library, path: String)

    protected fun KtFile.getTextWithReferences(): String = buildString {
        accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is LeafPsiElement) {
                    append(element.text)
                } else {
                    element.acceptChildren(this)
                }
                if (element is KtReferenceExpression) {
                    val fqName = element.mainReference.resolve()?.getKotlinFqName()
                    fqName?.let { append("/*$it*/") }
                }
            }
        })
    }

}

abstract class AbstractFirReferenceResolveInLibraryClassesTest : AbstractFirReferenceResolveInLibraryTest(inSources = false) {
    override fun doTest(library: Library, path: String) {
        val jar = library.getFiles(OrderRootType.CLASSES).first { it.name == "myKotlinLib.jar" }

        val classFiles = getClassFiles(jar)
        val allText = classFiles.joinToString(separator = "\n\n") { classFile ->
            "// FILE: ${classFile.name}\n${classFile.getTextWithReferences()}"
        }
        val testFilePath = Paths.get(path)
        val resultFile = testFilePath.resolveSibling(testFilePath.nameWithoutExtension + ".decompiled.kt")
        KotlinTestUtils.assertEqualsToFile(resultFile.toFile(), allText)
    }

    private fun getClassFiles(jar: VirtualFile): List<KtClsFile> {
        return jar.children.filter { it.extension == "class" }.map { classFile ->
            KotlinDecompiledFileViewProvider(
                PsiManager.getInstance(project),
                classFile,
                true,
                ::KtClsFile
            ).getPsi(KotlinLanguage.INSTANCE) as KtClsFile
        }
    }
}

abstract class AbstractFirReferenceResolveInLibrarySourcesTest : AbstractFirReferenceResolveInLibraryTest(inSources = true) {
    override fun doTest(library: Library, path: String) {
        val jar = library.getFiles(OrderRootType.SOURCES).first { it.name == "src" }

        val ktFiles = jar.children.mapNotNull { it.toPsiFile(project) as? KtFile}
        val allText = ktFiles.joinToString(separator = "\n\n") { classFile ->
            "// FILE: ${classFile.name}\n${classFile.getTextWithReferences()}"
        }
        val testFilePath = Paths.get(path)
        val resultFile = testFilePath.resolveSibling(testFilePath.nameWithoutExtension + ".source.kt")
        KotlinTestUtils.assertEqualsToFile(resultFile.toFile(), allText)
    }
}
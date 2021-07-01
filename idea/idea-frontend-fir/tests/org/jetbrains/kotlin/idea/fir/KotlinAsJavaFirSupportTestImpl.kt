/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.KtFakeLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.asJava.KtFirBasedFakeLightClass
import org.jetbrains.kotlin.idea.asJava.classes.getOrCreateFirLightClass
import org.jetbrains.kotlin.idea.asJava.classes.getOrCreateFirLightFacade
import org.jetbrains.kotlin.idea.fir.low.level.api.createDeclarationProvider
import org.jetbrains.kotlin.idea.fir.low.level.api.createPackageProvider
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.isPlain
import org.jetbrains.kotlin.psi.psiUtil.plainContent

@Suppress("unused") //Used by reflection
class KotlinAsJavaFirSupportTestImpl(private val project: Project) : KotlinAsJavaSupport() {

    override fun findClassOrObjectDeclarationsInPackage(
        packageFqName: FqName,
        searchScope: GlobalSearchScope
    ): Collection<KtClassOrObject> {
        val provider = project.createDeclarationProvider(searchScope)
        return provider.getClassNamesInPackage(packageFqName).flatMap {
            provider.getClassesByClassId(ClassId.topLevel(packageFqName.child(it)))
        }
    }

    override fun findFilesForPackage(fqName: FqName, searchScope: GlobalSearchScope): Collection<KtFile> {
        val mergedResult = mutableSetOf<KtFile>()
        mergedResult.addAll(getFacadeFilesInPackage(fqName, searchScope))
        findClassOrObjectDeclarationsInPackage(fqName, searchScope).mapTo(mergedResult) {
            it.containingKtFile
        }
        return mergedResult
    }

    override fun findClassOrObjectDeclarations(fqName: FqName, searchScope: GlobalSearchScope): Collection<KtClassOrObject> =
        project.createDeclarationProvider(searchScope).getClassesByClassId(ClassId.topLevel(fqName))

    override fun packageExists(fqName: FqName, scope: GlobalSearchScope): Boolean =
        project.createPackageProvider(scope).isPackageExists(fqName)

    override fun getSubPackages(fqn: FqName, scope: GlobalSearchScope): Collection<FqName> = emptyList()
//        project.createPackageProvider(scope)
//            .getJavaAndKotlinSubPackageFqNames(fqn, JvmPlatforms.unspecifiedJvmPlatform)
//            .map { fqn.child(it) }

    override fun getLightClass(classOrObject: KtClassOrObject): KtLightClass? =
        getOrCreateFirLightClass(classOrObject)

    override fun getLightClassForScript(script: KtScript): KtLightClass =
        error("Should not be called")

    override fun getFacadeClasses(facadeFqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> {
        //TODO Multi-module facades support
        val filesByModule = findFilesForFacade(facadeFqName, scope)
        return listOfNotNull(getOrCreateFirLightFacade(filesByModule.toList(), facadeFqName))
    }

    override fun getScriptClasses(scriptFqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> =
        emptyList()

    override fun getKotlinInternalClasses(fqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> =
        emptyList()

    override fun getFacadeClassesInPackage(packageFqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> {
        return getFacadeFilesInPackage(packageFqName, scope).mapNotNull {
            val name = packageFqName.child(Name.identifier(it.facadeName))
            getOrCreateFirLightFacade(listOf(it), name)
        }
    }

    //TODO: Make correct support for JvmName
    private val KtFile.facadeName: String
        get() {
            val jvmNameArgument = annotationEntries
                .firstOrNull { it.shortName?.asString() == "JvmName" }
                ?.valueArguments
                ?.firstOrNull()
                ?.getArgumentExpression()
            return if (jvmNameArgument is KtStringTemplateExpression && jvmNameArgument.isPlain()) jvmNameArgument.plainContent else virtualFile.nameWithoutExtension.capitalize() + "Kt"
        }

    private fun getFacadeFilesInPackage(packageFqName: FqName, scope: GlobalSearchScope): Collection<KtFile> {
        val mergedResults = mutableSetOf<KtFile>()
        with(project.createDeclarationProvider(scope)) {
            val functions = getFunctionsNamesInPackage(packageFqName)
                .flatMap { getTopLevelFunctions(CallableId(packageFqName, it)) }
            val properties = getPropertyNamesInPackage(packageFqName)
                .flatMap { getTopLevelProperties(CallableId(packageFqName, it)) }

            functions.mapTo(mergedResults) { it.containingKtFile }
            properties.mapTo(mergedResults) { it.containingKtFile }
        }
        return mergedResults
    }

    override fun getFacadeNames(packageFqName: FqName, scope: GlobalSearchScope): Collection<String> =
        getFacadeFilesInPackage(packageFqName, scope).map { it.facadeName }

    override fun findFilesForFacade(facadeFqName: FqName, scope: GlobalSearchScope): Collection<KtFile> {
        val shortName = facadeFqName.shortNameOrSpecial()
        if (shortName.isSpecial) return emptyList()
        return getFacadeFilesInPackage(facadeFqName.parent(), scope).filter {
            it.facadeName == shortName.asString()
        } //TODO Here was the sort by platformSourcesFirst() fix
    }

    override fun getFakeLightClass(classOrObject: KtClassOrObject): KtFakeLightClass =
        KtFirBasedFakeLightClass(classOrObject)
}
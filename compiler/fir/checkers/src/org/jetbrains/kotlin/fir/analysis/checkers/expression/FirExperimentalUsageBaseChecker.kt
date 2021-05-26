/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.*
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.FirConstExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toFirRegularClass
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.scopes.processDirectlyOverriddenFunctions
import org.jetbrains.kotlin.fir.scopes.processDirectlyOverriddenProperties
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.coneTypeSafe
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.checkers.Experimentality
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.kotlin.utils.addIfNotNull

object FirExperimentalUsageBaseChecker {
    internal fun FirAnnotatedDeclaration.loadExperimentalities(
        context: CheckerContext,
        visitedClasses: MutableSet<FirClass<*>> = mutableSetOf()
    ): Set<Experimentality> {
        if (this in visitedClasses) return emptySet()
        if (this is FirClass<*>) {
            visitedClasses += this
        }
        val result = SmartSet.create<Experimentality>()
        val session = context.session
        if (this is FirCallableMemberDeclaration<*>) {
            val parentClass = containingClass()?.toFirRegularClass(session)
            if (this.isSubstitutionOrIntersectionOverride) {
                val parentClassScope = parentClass?.unsubstitutedScope(context)
                if (this is FirSimpleFunction) {
                    parentClassScope?.processDirectlyOverriddenFunctions(symbol) {
                        result.addAll(it.fir.loadExperimentalities(context, visitedClasses))
                        ProcessorAction.NEXT
                    }
                } else if (this is FirProperty) {
                    parentClassScope?.processDirectlyOverriddenProperties(symbol) {
                        result.addAll(it.fir.loadExperimentalities(context, visitedClasses))
                        ProcessorAction.NEXT
                    }
                }
            }
            if (this !is FirConstructor) {
                result.addAll(returnTypeRef.coneType.loadExperimentalities(context, visitedClasses))
                result.addAll(receiverTypeRef?.coneType.loadExperimentalities(context, visitedClasses))
                if (this is FirSimpleFunction) {
                    valueParameters.forEach {
                        result.addAll(it.returnTypeRef.coneType.loadExperimentalities(context, visitedClasses))
                    }
                }
            }
            if (parentClass != null) {
                result.addAll(parentClass.loadExperimentalities(context, visitedClasses))
            }
        } else if (this is FirRegularClass && !this.isLocal) {
            val parentClass = this.outerClass(context)
            if (parentClass != null) {
                result.addAll(parentClass.loadExperimentalities(context, visitedClasses))
            }
        }

        for (annotation in annotations) {
            val annotationType = annotation.annotationTypeRef.coneTypeSafe<ConeClassLikeType>()
            result.addIfNotNull(
                annotationType?.fullyExpandedType(session)?.lookupTag?.toFirRegularClass(
                    session
                )?.loadExperimentalityForMarkerAnnotation()
            )
        }

        if (this is FirTypeAlias) {
            result.addAll(expandedTypeRef.coneType.loadExperimentalities(context, visitedClasses))
        }

        if (getAnnotationByFqName(OptInNames.WAS_EXPERIMENTAL_FQ_NAME) != null) {
            val accessibility = checkSinceKotlinVersionAccessibility(context)
            if (accessibility is FirSinceKotlinAccessibility.NotAccessibleButWasExperimental) {
                result.addAll(accessibility.markerClasses.mapNotNull { it.fir.loadExperimentalityForMarkerAnnotation() })
            }
        }

        // TODO: getAnnotationsOnContainingModule

        return result
    }

    private fun ConeKotlinType?.loadExperimentalities(
        context: CheckerContext,
        visitedClasses: MutableSet<FirClass<*>>
    ): Set<Experimentality> =
        if (this !is ConeClassLikeType) emptySet()
        else fullyExpandedType(context.session).lookupTag.toFirRegularClass(context.session)?.loadExperimentalities(
            context, visitedClasses
        ).orEmpty()

    private fun FirRegularClass.loadExperimentalityForMarkerAnnotation(): Experimentality? {
        val experimental = getAnnotationByFqName(OptInNames.REQUIRES_OPT_IN_FQ_NAME)
            ?: getAnnotationByFqName(OptInNames.OLD_EXPERIMENTAL_FQ_NAME)
            ?: return null

        val levelArgument = experimental.findSingleArgumentByName(LEVEL) as? FirQualifiedAccessExpression
        val levelName = (levelArgument?.calleeReference as? FirResolvedNamedReference)?.name?.asString()
        val level = OptInLevel.values().firstOrNull { it.name == levelName } ?: OptInLevel.DEFAULT
        val message = (experimental.findSingleArgumentByName(MESSAGE) as? FirConstExpression<*>)?.value as? String
        return Experimentality(symbol.classId.asSingleFqName(), level.severity, message)
    }

    internal fun reportNotAcceptedExperimentalities(
        experimentalities: Collection<Experimentality>,
        element: FirElement,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        for ((annotationFqName, severity, message) in experimentalities) {
            if (!isExperimentalityAcceptableInContext(annotationFqName, element, context)) {
                val diagnostic = when (severity) {
                    Experimentality.Severity.WARNING -> FirErrors.EXPERIMENTAL_API_USAGE
                    Experimentality.Severity.ERROR -> FirErrors.EXPERIMENTAL_API_USAGE_ERROR
                }
                reporter.reportOn(element.source, diagnostic, annotationFqName, message ?: "", context)
            }
        }
    }

    private fun isExperimentalityAcceptableInContext(
        annotationFqName: FqName,
        element: FirElement,
        context: CheckerContext
    ): Boolean {
        val languageVersionSettings = context.session.languageVersionSettings
        val fqNameAsString = annotationFqName.asString()
        if (fqNameAsString in languageVersionSettings.getFlag(AnalysisFlags.experimental) ||
            fqNameAsString in languageVersionSettings.getFlag(AnalysisFlags.useExperimental)
        ) {
            return true
        }
        for (declaration in context.containingDeclarations) {
            if (declaration !is FirAnnotatedDeclaration) continue
            if (declaration.isExperimentalityAcceptable(annotationFqName)) {
                return true
            }
        }
        for (accessOrAnnotation in context.qualifiedAccessOrAnnotationCalls) {
            if (accessOrAnnotation.isExperimentalityAcceptable(annotationFqName)) {
                return true
            }
        }
        if (element !is FirAnnotationContainer) return false
        return element.isExperimentalityAcceptable(annotationFqName)
    }

    private fun FirAnnotationContainer.isExperimentalityAcceptable(annotationFqName: FqName): Boolean {
        return getAnnotationByFqName(annotationFqName) != null || isAnnotatedWithUseExperimentalOf(annotationFqName)
    }

    private fun FirAnnotationContainer.isAnnotatedWithUseExperimentalOf(annotationFqName: FqName): Boolean {
        for (annotation in annotations) {
            val coneType = annotation.annotationTypeRef.coneType as? ConeClassLikeType
            if (coneType?.lookupTag?.classId?.asSingleFqName() !in OptInNames.USE_EXPERIMENTAL_FQ_NAMES) {
                continue
            }
            val annotationClasses = annotation.findSingleArgumentByName(OptInNames.USE_EXPERIMENTAL_ANNOTATION_CLASS) ?: continue
            if (annotationClasses.extractClassesFromArgument().any {
                    it.classId.asSingleFqName() == annotationFqName
                }
            ) {
                return true
            }
        }
        return false
    }

    private val LEVEL = Name.identifier("level")
    private val MESSAGE = Name.identifier("message")

    private enum class OptInLevel(val severity: Experimentality.Severity) {
        WARNING(Experimentality.Severity.WARNING),
        ERROR(Experimentality.Severity.ERROR),
        DEFAULT(Experimentality.DEFAULT_SEVERITY)
    }
}
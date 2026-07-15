/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2020-Present Datadog, Inc.
 */

package com.openobserve.gradle.plugin.kcp

import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language

object KotlinCompilerTestSources {

    const val TEST_CALLBACK_CONTAINER_PATH = "com.openobserve.gradle.plugin.kcp.TestCallbackContainer"
    const val NAV_GRAPH_BUILDER_PATH = "androidx.navigation.compose.NavGraphBuilder"
    const val NAV_HOST_BUILDER_PATH = "androidx.navigation.compose.NavHost"
    const val NAV_HOST_CONTROLLER_PATH = "androidx.navigation.NavHostController"
    const val TRACKING_EFFECT_FILE_NAME = "Navigation.kt"
    const val OO_MODIFIER_CLASS_FILE_NAME = "OpenObserveModifier.kt"
    const val COMPOSE_INSTRUMENTATION_ANNOTATION_FILE_NAME = "ComposeInstrumentation.kt"
    const val SDK_CORE_FILE_NAME = "SdkCore.kt"
    const val COMPONENT_PREDICATE_FILE_NAME = "ComponentPredicate.kt"
    const val ACCEPT_ALL_NAV_DESTINATIONS_FILE_NAME = "AcceptAllNavDestinations.kt"
    const val OPENOBSERVE_FILE_NAME = "OpenObserve.kt"

    @Language("kotlin")
    val OO_MODIFIER_SOURCE_FILE_CONTENT =
        """
        package com.openobserve.android.compose

        import androidx.compose.ui.Modifier
        import androidx.compose.ui.semantics.SemanticsPropertyKey
        import androidx.compose.ui.semantics.SemanticsPropertyReceiver
        import androidx.compose.ui.semantics.semantics
        import com.openobserve.gradle.plugin.kcp.TestCallbackContainer

        fun Modifier.instrumentedOpenObserve(name: String, isImageRole: Boolean = false): Modifier {
            TestCallbackContainer.invokeCallback(isImageRole)
            return this.semantics {
                this.datadog = name
            }
        }

        internal val OpenObserveSemanticsPropertyKey: SemanticsPropertyKey<String> = SemanticsPropertyKey(
            name = "_dd_semantics",
            mergePolicy = { parentValue, childValue ->
                parentValue
            }
        )

        private var SemanticsPropertyReceiver.datadog by OpenObserveSemanticsPropertyKey
        """.trimIndent()

    @Language("kotlin")
    val TRACKING_EFFECT_SOURCE_FILE_CONTENT =
        """
        package com.openobserve.android.compose

        import androidx.compose.runtime.Composable
        import androidx.navigation.NavController
        import androidx.navigation.NavDestination
        import com.openobserve.android.api.SdkCore
        import com.openobserve.android.OpenObserve
        import com.openobserve.android.rum.tracking.AcceptAllNavDestinations
        import com.openobserve.android.rum.tracking.ComponentPredicate
        import com.openobserve.gradle.plugin.kcp.TestCallbackContainer

        @Composable
        fun InstrumentedNavigationViewTrackingEffect(
            navController: NavController,
            trackArguments: Boolean = true,
            destinationPredicate: ComponentPredicate<NavDestination> = AcceptAllNavDestinations(),
            sdkCore: SdkCore = OpenObserve.getInstance()
        ) {
            TestCallbackContainer.invokeCallback()
        }
        """.trimIndent()

    @Language("kotlin")
    val SDK_CORE_SOURCE_FILE_CONTENT =
        """
        package com.openobserve.android.api

        open class SdkCore
        """.trimIndent()

    @Language("kotlin")
    val COMPONENT_PREDICATE_SOURCE_FILE_CONTENT =
        """
        package com.openobserve.android.rum.tracking

        interface ComponentPredicate<T>
        """.trimIndent()

    @Language("kotlin")
    val ACCEPT_ALL_NAV_DESTINATIONS_SOURCE_FILE_CONTENT =
        """
        package com.openobserve.android.rum.tracking

        import androidx.navigation.NavDestination

        class AcceptAllNavDestinations : ComponentPredicate<NavDestination>
        """.trimIndent()

    @Language("kotlin")
    val OPENOBSERVE_SOURCE_FILE_CONTENT =
        """
        package com.openobserve.android

        import com.openobserve.android.api.SdkCore

        object OpenObserve {
            fun getInstance(instanceName: String? = null): SdkCore = SdkCore()
        }
        """.trimIndent()

    @Language("kotlin")
    val COMPOSE_INSTRUMENTATION_ANNOTATION_SOURCE_FILE_CONTENT =
        """
        package com.openobserve.android.compose

        @Retention(AnnotationRetention.BINARY)
        annotation class ComposeInstrumentation

        """.trimIndent()

    val trackingEffectSourceFile = SourceFile.kotlin(
        TRACKING_EFFECT_FILE_NAME,
        TRACKING_EFFECT_SOURCE_FILE_CONTENT
    )

    val ooModifierSourceFile = SourceFile.kotlin(
        OO_MODIFIER_CLASS_FILE_NAME,
        OO_MODIFIER_SOURCE_FILE_CONTENT
    )

    val composeInstrumentationAnnotationSourceFile = SourceFile.kotlin(
        COMPOSE_INSTRUMENTATION_ANNOTATION_FILE_NAME,
        COMPOSE_INSTRUMENTATION_ANNOTATION_SOURCE_FILE_CONTENT
    )

    val sdkCoreSourceFile = SourceFile.kotlin(SDK_CORE_FILE_NAME, SDK_CORE_SOURCE_FILE_CONTENT)

    val componentPredicateSourceFile = SourceFile.kotlin(
        COMPONENT_PREDICATE_FILE_NAME,
        COMPONENT_PREDICATE_SOURCE_FILE_CONTENT
    )

    val acceptAllNavDestinationsSourceFile = SourceFile.kotlin(
        ACCEPT_ALL_NAV_DESTINATIONS_FILE_NAME,
        ACCEPT_ALL_NAV_DESTINATIONS_SOURCE_FILE_CONTENT
    )

    val ooSourceFile = SourceFile.kotlin(OPENOBSERVE_FILE_NAME, OPENOBSERVE_SOURCE_FILE_CONTENT)

    // TODO RUM-8950: Dependency file should be separated in each file after the DSL configuration is introduced.
    val dependencyFiles = listOf(
        sdkCoreSourceFile,
        componentPredicateSourceFile,
        acceptAllNavDestinationsSourceFile,
        ooSourceFile,
        trackingEffectSourceFile,
        ooModifierSourceFile,
        composeInstrumentationAnnotationSourceFile
    )
}

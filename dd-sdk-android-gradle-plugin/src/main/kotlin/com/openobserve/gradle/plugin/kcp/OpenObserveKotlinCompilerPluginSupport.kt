/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin.kcp

import com.openobserve.gradle.plugin.DdExtension
import com.openobserve.gradle.plugin.InstrumentationMode
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/**
 * Support class for configuring the OpenObserve Kotlin Compiler Plugin based on the Kotlin version.
 * This class implements [KotlinCompilerPluginSupportPlugin] to properly integrate with the
 * Kotlin Gradle Plugin and target the correct version-specific sub-plugin.
 *
 * The plugin points to different sub-plugin based on kotlin compiler version (kotlin20, kotlin21, kotlin22, kotlin24).
 *
 */
class OpenObserveKotlinCompilerPluginSupport : KotlinCompilerPluginSupportPlugin {

    private lateinit var kgpVersion: String
    private lateinit var kotlinVersion: KotlinVersion

    @Suppress("ReturnCount")
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val kotlinTarget = kotlinCompilation.target
        if (kotlinTarget.platformType != KotlinPlatformType.androidJvm) {
            // TODO RUM-16076 Explore .common type support
            LOGGER.info(
                "OpenObserve Jetpack Compose instrumentation cannot be" +
                    " applied to the Kotlin Compilation of platform type = ${kotlinTarget.platformType}"
            )
            return false
        }
        val project = kotlinTarget.project
        kgpVersion = project.getKotlinPluginVersion()
        kotlinVersion = KotlinVersion.from(kgpVersion)
        if (kotlinVersion == KotlinVersion.UNSUPPORTED) {
            LOGGER.info("Unsupported Kotlin version for the OpenObserve Jetpack Compose instrumentation: \$kgpVersion\"")
            return false
        }
        val ddExtension = project.extensions.findByType(DdExtension::class.java) ?: return false

        if (ddExtension.composeInstrumentation == InstrumentationMode.DISABLE) {
            LOGGER.info("OpenObserve Jetpack Compose instrumentation is disabled")
            return false
        }

        return true
    }

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val ddExtension = project.extensions.getByType(DdExtension::class.java)
        val composeInstrumentation = ddExtension.composeInstrumentation

        // For Kotlin 2.4.0+, CompilerPluginRegistrar does not support LoadingOrder.FIRST,
        // so we use -Xcompiler-plugin-order to guarantee OpenObserve runs before Compose.
        // Use compileTaskProvider (KGP 2.0+ stable API) instead of the deprecated
        // kotlinOptions.freeCompilerArgs which does not propagate reliably in KGP 2.4.0.
        if (kotlinVersion == KotlinVersion.KOTLIN24) {
            kotlinCompilation.compileTaskProvider.configure { task ->
                task.compilerOptions.freeCompilerArgs.add(
                    "-Xcompiler-plugin-order=$PLUGIN_ID>$COMPOSE_PLUGIN_ID"
                )
            }
        }

        return project.provider {
            listOf(
                SubpluginOption(
                    key = INSTRUMENTATION_MODE,
                    value = composeInstrumentation.name
                )
            )
        }
    }

    override fun getCompilerPluginId(): String = PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact {
        val artifactId = when (kotlinVersion) {
            KotlinVersion.KOTLIN19, KotlinVersion.KOTLIN20 -> "$PLUGIN_ARTIFACT_ID_PREFIX-kotlin20"
            KotlinVersion.KOTLIN21 -> "$PLUGIN_ARTIFACT_ID_PREFIX-kotlin21"
            KotlinVersion.KOTLIN22 -> "$PLUGIN_ARTIFACT_ID_PREFIX-kotlin22"
            KotlinVersion.KOTLIN24 -> "$PLUGIN_ARTIFACT_ID_PREFIX-kotlin24"
            KotlinVersion.UNSUPPORTED -> error("Unsupported Kotlin version: $kgpVersion")
        }
        return SubpluginArtifact(
            groupId = GROUP_ID,
            artifactId = artifactId,
            version = BuildConfig.OO_PLUGIN_VERSION
        )
    }

    private companion object {
        private const val PLUGIN_ID = "io.openobserve.kotlin.compiler"
        private const val COMPOSE_PLUGIN_ID = "androidx.compose.compiler.plugins.kotlin"
        private const val GROUP_ID = "io.openobserve"
        private const val PLUGIN_ARTIFACT_ID_PREFIX = "openobserve-sdk-android-gradle-plugin-kcp"
        private const val INSTRUMENTATION_MODE = "INSTRUMENTATION_MODE"
        private val LOGGER = Logging.getLogger("OpenObserveKotlinCompilerPluginSupport")
    }
}

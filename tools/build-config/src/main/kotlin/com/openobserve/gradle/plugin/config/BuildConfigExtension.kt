/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin.config

import com.openobserve.gradle.config.dependencyUpdateConfig
import com.openobserve.gradle.config.jacocoConfig
import com.openobserve.gradle.config.javadocConfig
import com.openobserve.gradle.config.junitConfig
import com.openobserve.gradle.config.kotlinConfig
import com.openobserve.gradle.config.publishingConfig
import org.gradle.api.Project
import javax.inject.Inject

abstract class BuildConfigExtension @Inject constructor(
    private val project: Project
) {

    fun applyKotlinConfig() {
        project.kotlinConfig()
    }

    fun applyJunitConfig() {
        project.junitConfig()
    }

    fun applyJacocoConfig() {
        project.jacocoConfig()
    }

    fun applyJavadocConfig() {
        project.javadocConfig()
    }

    fun applyDependencyUpdateConfig() {
        project.dependencyUpdateConfig()
    }

    fun applyPublishingConfig(description: String) {
        project.publishingConfig(description)
    }
}

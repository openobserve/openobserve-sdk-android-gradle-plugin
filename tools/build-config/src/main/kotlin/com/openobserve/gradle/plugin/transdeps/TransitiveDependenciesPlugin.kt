/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin.transdeps

import com.openobserve.gradle.config.taskConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class TransitiveDependenciesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val listTransitiveDependenciesTask =
            target.tasks.register<TransitiveDependenciesTask>(TASK_GEN_TRANSITIVE_DEPS) {
                humanReadableSize.value(true)
                sortByName.value(true)
                outputFile.set(target.layout.projectDirectory.file(FILE_NAME))
            }

        target.tasks.register<CheckTransitiveDependenciesTask>(TASK_CHECK_TRANSITIVE_DEPS) {
            dependenciesFile.set(listTransitiveDependenciesTask.flatMap { it.outputFile })
        }

        target.taskConfig<KotlinCompile> {
            finalizedBy(listTransitiveDependenciesTask)
        }
    }

    companion object {

        const val TASK_GEN_TRANSITIVE_DEPS = "listTransitiveDependencies"
        const val TASK_CHECK_TRANSITIVE_DEPS = "checkTransitiveDependenciesList"
        const val FILE_NAME = "transitiveDependencies"
    }
}

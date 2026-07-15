/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2020-Present Datadog, Inc.
 */

package com.openobserve.gradle.plugin

import com.openobserve.gradle.plugin.internal.ApiKey
import com.openobserve.gradle.plugin.internal.ApiKeySource
import fr.xgouchet.elmyr.Forge
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.io.File

/**
 * Tests for DdAndroidGradlePlugin.resolveApiKey() method.
 */
internal class DdAndroidGradlePluginResolveApiKeyTest : DdAndroidGradlePluginTestBase() {

    @Test
    fun `M resolve API KEY from project properties W resolveApiKey() { as OO_API_KEY }`() {
        // Given
        fakeProject = spy(ProjectBuilder.builder().build())
        val emptyProvider: Provider<String> = fakeProject.provider { null }
        val apiKeyProvider: Provider<String> = fakeProject.provider { fakeApiKey.value }
        val fakeProviderFactory = spy(fakeProject.providers)

        whenever(fakeProject.providers) doReturn fakeProviderFactory
        with(fakeProviderFactory) {
            whenever(environmentVariable(DdAndroidGradlePlugin.OPENOBSERVE_API_KEY)) doReturn emptyProvider
            whenever(environmentVariable(DdAndroidGradlePlugin.OO_API_KEY)) doReturn emptyProvider
            whenever(gradleProperty(DdAndroidGradlePlugin.OPENOBSERVE_API_KEY)) doReturn emptyProvider
            whenever(gradleProperty(DdAndroidGradlePlugin.OO_API_KEY)) doReturn apiKeyProvider
        }

        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey.value).isEqualTo(fakeApiKey.value)
        assertThat(apiKey.source).isEqualTo(ApiKeySource.GRADLE_PROPERTY)
    }

    @Test
    fun `M resolve API KEY from environment variable W resolveApiKey() { as OO_API_KEY }`() {
        // Given
        fakeProject = spy(ProjectBuilder.builder().build())
        val emptyProvider: Provider<String> = fakeProject.provider { null }
        val apiKeyProvider: Provider<String> = fakeProject.provider { fakeApiKey.value }
        val fakeProviderFactory = spy(fakeProject.providers)

        whenever(fakeProject.providers) doReturn fakeProviderFactory
        with(fakeProviderFactory) {
            whenever(environmentVariable(DdAndroidGradlePlugin.OPENOBSERVE_API_KEY)) doReturn emptyProvider
            whenever(environmentVariable(DdAndroidGradlePlugin.OO_API_KEY)) doReturn apiKeyProvider
            whenever(gradleProperty(DdAndroidGradlePlugin.OPENOBSERVE_API_KEY)) doReturn emptyProvider
            whenever(gradleProperty(DdAndroidGradlePlugin.OO_API_KEY)) doReturn emptyProvider
        }

        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey.value).isEqualTo(fakeApiKey.value)
        assertThat(apiKey.source).isEqualTo(ApiKeySource.ENVIRONMENT)
    }

    @Test
    fun `M resolve API KEY from project properties W resolveApiKey() { as OPENOBSERVE_API_KEY }`() {
        // Given
        fakeProject = spy(ProjectBuilder.builder().build())
        val emptyProvider: Provider<String> = fakeProject.provider { null }
        val apiKeyProvider: Provider<String> = fakeProject.provider { fakeApiKey.value }
        val fakeProviderFactory = spy(fakeProject.providers)

        whenever(fakeProject.providers) doReturn fakeProviderFactory
        with(fakeProviderFactory) {
            whenever(environmentVariable(DdAndroidGradlePlugin.OPENOBSERVE_API_KEY)) doReturn emptyProvider
            whenever(environmentVariable(DdAndroidGradlePlugin.OO_API_KEY)) doReturn emptyProvider
            whenever(gradleProperty(DdAndroidGradlePlugin.OPENOBSERVE_API_KEY)) doReturn apiKeyProvider
            whenever(gradleProperty(DdAndroidGradlePlugin.OO_API_KEY)) doReturn emptyProvider
        }

        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey.value).isEqualTo(fakeApiKey.value)
        assertThat(apiKey.source).isEqualTo(ApiKeySource.GRADLE_PROPERTY)
    }

    @Test
    fun `M resolve API KEY from environment variable W resolveApiKey() { as OPENOBSERVE_API_KEY }`() {
        // Given
        fakeProject = spy(ProjectBuilder.builder().build())
        val emptyProvider: Provider<String> = fakeProject.provider { null }
        val apiKeyProvider: Provider<String> = fakeProject.provider { fakeApiKey.value }
        val fakeProviderFactory = spy(fakeProject.providers)

        whenever(fakeProject.providers) doReturn fakeProviderFactory
        with(fakeProviderFactory) {
            whenever(environmentVariable(DdAndroidGradlePlugin.OPENOBSERVE_API_KEY)) doReturn apiKeyProvider
            whenever(environmentVariable(DdAndroidGradlePlugin.OO_API_KEY)) doReturn emptyProvider
            whenever(gradleProperty(DdAndroidGradlePlugin.OPENOBSERVE_API_KEY)) doReturn emptyProvider
            whenever(gradleProperty(DdAndroidGradlePlugin.OO_API_KEY)) doReturn emptyProvider
        }

        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey.value).isEqualTo(fakeApiKey.value)
        assertThat(apiKey.source).isEqualTo(ApiKeySource.ENVIRONMENT)
    }

    @Test
    fun `M returns empty String W resolveApiKey() {key not defined anywhere}`() {
        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey).isEqualTo(ApiKey.NONE)
    }

    @Test
    fun `M resolve API KEY from datadog-ci file W resolveApiKey() { valid config file }`(
        forge: Forge
    ) {
        // Given
        val apiKeyValue = forge.anHexadecimalString()
        val ooCiFile = File(fakeProject.projectDir, "datadog-ci.json")
        ooCiFile.writeText("""{"apiKey": "$apiKeyValue"}""")

        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey.value).isEqualTo(apiKeyValue)
        assertThat(apiKey.source).isEqualTo(ApiKeySource.OPENOBSERVE_CI_CONFIG_FILE)

        // Cleanup
        ooCiFile.delete()
    }

    @Test
    fun `M resolve API KEY from datadog-ci file W resolveApiKey() { file in parent directory }`(
        forge: Forge
    ) {
        // Given
        val apiKeyValue = forge.anHexadecimalString()
        val subProject = File(fakeProject.projectDir, "submodule")
        subProject.mkdirs()
        val ooCiFile = File(fakeProject.projectDir, "datadog-ci.json")
        ooCiFile.writeText("""{"apiKey": "$apiKeyValue"}""")

        fakeProject = ProjectBuilder.builder()
            .withProjectDir(subProject)
            .build()
        testedPlugin = DdAndroidGradlePlugin(execOps = mock())

        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey.value).isEqualTo(apiKeyValue)
        assertThat(apiKey.source).isEqualTo(ApiKeySource.OPENOBSERVE_CI_CONFIG_FILE)

        // Cleanup
        ooCiFile.delete()
        subProject.deleteRecursively()
    }

    @Test
    fun `M return NONE W resolveApiKey() { datadog-ci file with empty apiKey }`() {
        // Given
        val ooCiFile = File(fakeProject.projectDir, "datadog-ci.json")
        ooCiFile.writeText("""{"apiKey": ""}""")

        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey).isEqualTo(ApiKey.NONE)

        // Cleanup
        ooCiFile.delete()
    }

    @Test
    fun `M return NONE W resolveApiKey() { datadog-ci file with null apiKey }`() {
        // Given
        val ooCiFile = File(fakeProject.projectDir, "datadog-ci.json")
        ooCiFile.writeText("""{"apiKey": null}""")

        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey).isEqualTo(ApiKey.NONE)

        // Cleanup
        ooCiFile.delete()
    }

    @Test
    fun `M return NONE W resolveApiKey() { datadog-ci file without apiKey field }`() {
        // Given
        val ooCiFile = File(fakeProject.projectDir, "datadog-ci.json")
        ooCiFile.writeText("""{"someOtherField": "value"}""")

        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey).isEqualTo(ApiKey.NONE)

        // Cleanup
        ooCiFile.delete()
    }

    @Test
    fun `M return NONE and log warning W resolveApiKey() { datadog-ci file with invalid JSON }`() {
        // Given
        val ooCiFile = File(fakeProject.projectDir, "datadog-ci.json")
        ooCiFile.writeText("""{"apiKey": "incomplete json"""")

        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey).isEqualTo(ApiKey.NONE)

        // Cleanup
        ooCiFile.delete()
    }

    @Test
    fun `M prefer gradle property over config file W resolveApiKey() { both present }`(
        forge: Forge
    ) {
        // Given
        val gradleApiKey = forge.anHexadecimalString()
        val fileApiKey = forge.anHexadecimalString()

        fakeProject = spy(ProjectBuilder.builder().build())
        val emptyProvider: Provider<String> = fakeProject.provider { null }
        val apiKeyProvider: Provider<String> = fakeProject.provider { gradleApiKey }
        val fakeProviderFactory = spy(fakeProject.providers)

        whenever(fakeProject.providers) doReturn fakeProviderFactory
        with(fakeProviderFactory) {
            whenever(environmentVariable(DdAndroidGradlePlugin.OPENOBSERVE_API_KEY)) doReturn emptyProvider
            whenever(environmentVariable(DdAndroidGradlePlugin.OO_API_KEY)) doReturn emptyProvider
            whenever(gradleProperty(DdAndroidGradlePlugin.OPENOBSERVE_API_KEY)) doReturn emptyProvider
            whenever(gradleProperty(DdAndroidGradlePlugin.OO_API_KEY)) doReturn apiKeyProvider
        }

        val ooCiFile = File(fakeProject.projectDir, "datadog-ci.json")
        ooCiFile.writeText("""{"apiKey": "$fileApiKey"}""")

        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey.value).isEqualTo(gradleApiKey)
        assertThat(apiKey.source).isEqualTo(ApiKeySource.GRADLE_PROPERTY)

        // Cleanup
        ooCiFile.delete()
    }

    @Test
    fun `M prefer environment variable over config file W resolveApiKey() { both present }`(
        forge: Forge
    ) {
        // Given
        val envApiKey = forge.anHexadecimalString()
        val fileApiKey = forge.anHexadecimalString()

        fakeProject = spy(ProjectBuilder.builder().build())
        val emptyProvider: Provider<String> = fakeProject.provider { null }
        val apiKeyProvider: Provider<String> = fakeProject.provider { envApiKey }
        val fakeProviderFactory = spy(fakeProject.providers)

        whenever(fakeProject.providers) doReturn fakeProviderFactory
        with(fakeProviderFactory) {
            whenever(environmentVariable(DdAndroidGradlePlugin.OPENOBSERVE_API_KEY)) doReturn emptyProvider
            whenever(environmentVariable(DdAndroidGradlePlugin.OO_API_KEY)) doReturn apiKeyProvider
            whenever(gradleProperty(DdAndroidGradlePlugin.OPENOBSERVE_API_KEY)) doReturn emptyProvider
            whenever(gradleProperty(DdAndroidGradlePlugin.OO_API_KEY)) doReturn emptyProvider
        }

        val ooCiFile = File(fakeProject.projectDir, "datadog-ci.json")
        ooCiFile.writeText("""{"apiKey": "$fileApiKey"}""")

        // When
        val apiKey = testedPlugin.resolveApiKey(fakeProject).get()

        // Then
        assertThat(apiKey.value).isEqualTo(envApiKey)
        assertThat(apiKey.source).isEqualTo(ApiKeySource.ENVIRONMENT)

        // Cleanup
        ooCiFile.delete()
    }
}

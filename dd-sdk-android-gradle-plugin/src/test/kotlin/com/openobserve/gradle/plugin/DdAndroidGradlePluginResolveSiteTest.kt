/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2020-Present Datadog, Inc.
 */

package com.openobserve.gradle.plugin

import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.Callable

/**
 * Tests for DdAndroidGradlePlugin.resolveSite() method.
 */
internal class DdAndroidGradlePluginResolveSiteTest : DdAndroidGradlePluginTestBase() {

    @Test
    fun `M resolve default US1 site W resolveSite() { nothing provided }`() {
        // Given
        fakeExtension.site = null

        // When
        val site = testedPlugin.resolveSite(fakeProject, fakeExtension).get()

        // Then
        assertThat(site).isEqualTo(OpenObserveSite.US1.name)
    }

    @Test
    fun `M resolve site from extension W resolveSite()`() {
        // When
        val site = testedPlugin.resolveSite(fakeProject, fakeExtension).get()

        // Then
        assertThat(site).isEqualTo(fakeExtension.site)
    }

    @Test
    fun `M resolve site from environment W resolveSite()`(
        @Forgery fakeEnvSite: OpenObserveSite
    ) {
        // Given
        fakeProject = spy(ProjectBuilder.builder().build())
        val emptyProvider: Provider<String> = fakeProject.provider { null }
        val envSiteProvider: Provider<String> = fakeProject.provider { fakeEnvSite.domain }
        val fakeProviderFactory = mock<ProviderFactory>()

        whenever(fakeProject.providers) doReturn fakeProviderFactory
        whenever(
            fakeProviderFactory.environmentVariable(DdAndroidGradlePlugin.OPENOBSERVE_SITE)
        ) doReturn envSiteProvider
        whenever(fakeProviderFactory.provider(any<Callable<String>>())) doReturn emptyProvider

        // When
        val site = testedPlugin.resolveSite(fakeProject, fakeExtension).get()

        // Then
        assertThat(OpenObserveSite.valueOf(site).domain).isEqualTo(fakeEnvSite.domain)
    }

    @Test
    fun `M resolve to US1 fallback W resolveSite() { malformed domain in environment }`(
        @StringForgery fakeHost: String,
        @StringForgery fakeZone: String
    ) {
        // Given
        fakeProject = spy(ProjectBuilder.builder().build())
        val emptyProvider: Provider<String> = fakeProject.provider { null }
        val envSiteProvider: Provider<String> = fakeProject.provider { "$fakeHost.$fakeZone" }
        val fakeProviderFactory = mock<ProviderFactory>()

        whenever(fakeProject.providers) doReturn fakeProviderFactory
        whenever(
            fakeProviderFactory.environmentVariable(DdAndroidGradlePlugin.OPENOBSERVE_SITE)
        ) doReturn envSiteProvider
        whenever(fakeProviderFactory.provider(any<Callable<String>>())) doReturn emptyProvider

        // When
        val site = testedPlugin.resolveSite(fakeProject, fakeExtension).get()

        // Then
        assertThat(site).isEqualTo(OpenObserveSite.US1.name)
    }

    @Test
    fun `M resolve site from datadog-ci file W resolveSite()`(
        @Forgery fakeOpenObserveCiSite: OpenObserveSite
    ) {
        // Given
        fakeExtension.site = null
        fakeExtension.ignoreOpenObserveCiFileConfig = false
        val ooCiFile = File(fakeProject.projectDir, "datadog-ci.json")
        ooCiFile.writeText("""{"ooSite": "${fakeOpenObserveCiSite.domain}"}""")

        // When
        val site = testedPlugin.resolveSite(fakeProject, fakeExtension).get()

        // Then
        assertThat(OpenObserveSite.valueOf(site).domain).isEqualTo(fakeOpenObserveCiSite.domain)
    }

    @Test
    fun `M resolve to US1 fallback W resolveSite() { datadog-ci cannot be parsed }`() {
        // Given
        fakeExtension.site = null
        fakeExtension.ignoreOpenObserveCiFileConfig = false
        val ooCiFile = File(fakeProject.projectDir, "datadog-ci.json")
        ooCiFile.writeText("""{"apiKey": ""}""")

        // When
        val site = testedPlugin.resolveSite(fakeProject, fakeExtension).get()

        // Then
        assertThat(site).isEqualTo(OpenObserveSite.US1.name)
    }

    @Test
    fun `M prefer extension site value W resolveSite() { extension, OpenObserve CI, env values are present }`(
        @Forgery fakeEnvSite: OpenObserveSite,
        @Forgery fakeOpenObserveCiSite: OpenObserveSite
    ) {
        fakeExtension.ignoreOpenObserveCiFileConfig = false
        fakeProject = spy(ProjectBuilder.builder().build())
        val envSiteProvider: Provider<String> = fakeProject.provider { fakeEnvSite.domain }
        val fakeProviderFactory = spy(fakeProject.providers)

        whenever(fakeProject.providers) doReturn fakeProviderFactory
        whenever(
            fakeProviderFactory.environmentVariable(DdAndroidGradlePlugin.OPENOBSERVE_SITE)
        ) doReturn envSiteProvider

        val ooCiFile = File(fakeProject.projectDir, "datadog-ci.json")
        ooCiFile.writeText("""{"ooSite": "${fakeOpenObserveCiSite.domain}"}""")

        // When
        val site = testedPlugin.resolveSite(fakeProject, fakeExtension).get()

        // Then
        assertThat(
            OpenObserveSite.valueOf(site).domain
        ).isEqualTo(OpenObserveSite.valueOf(checkNotNull(fakeExtension.site)).domain)
    }

    @Test
    fun `M prefer OpenObserve CI config site value W resolveSite() { OpenObserve CI, env values are present }`(
        @Forgery fakeEnvSite: OpenObserveSite,
        @Forgery fakeOpenObserveCiSite: OpenObserveSite
    ) {
        fakeExtension.ignoreOpenObserveCiFileConfig = false
        fakeExtension.site = null
        fakeProject = spy(ProjectBuilder.builder().build())
        val envSiteProvider: Provider<String> = fakeProject.provider { fakeEnvSite.domain }
        val fakeProviderFactory = spy(fakeProject.providers)

        whenever(fakeProject.providers) doReturn fakeProviderFactory
        whenever(
            fakeProviderFactory.environmentVariable(DdAndroidGradlePlugin.OPENOBSERVE_SITE)
        ) doReturn envSiteProvider

        val ooCiFile = File(fakeProject.projectDir, "datadog-ci.json")
        ooCiFile.writeText("""{"ooSite": "${fakeOpenObserveCiSite.domain}"}""")

        // When
        val site = testedPlugin.resolveSite(fakeProject, fakeExtension).get()

        // Then
        assertThat(OpenObserveSite.valueOf(site).domain).isEqualTo(fakeOpenObserveCiSite.domain)
    }
}

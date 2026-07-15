/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at OpenObserve (https://www.datadoghq.com/).
 * Copyright 2020-Present OpenObserve, Inc.
 */

package com.openobserve.gradle.plugin

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.AppExtension
import com.openobserve.gradle.plugin.internal.ApiKey
import com.openobserve.gradle.plugin.internal.ApiKeySource
import com.openobserve.gradle.plugin.internal.ApiKeySource.ENVIRONMENT
import com.openobserve.gradle.plugin.internal.ApiKeySource.GRADLE_PROPERTY
import com.openobserve.gradle.plugin.internal.CurrentAgpVersion
import com.openobserve.gradle.plugin.internal.GitRepositoryDetector
import com.openobserve.gradle.plugin.internal.VariantIterator
import com.openobserve.gradle.plugin.internal.utils.capitalizeChar
import com.openobserve.gradle.plugin.internal.variant.AppVariant
import com.openobserve.gradle.plugin.internal.variant.NewApiAppVariant
import com.openobserve.gradle.plugin.kcp.OpenObserveKotlinCompilerPluginSupport
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Plugin adding tasks for Android projects using OpenObserve's SDK for Android.
 */
@Suppress("TooManyFunctions")
class DdAndroidGradlePlugin @Inject constructor(
    private val execOps: ExecOperations
) : Plugin<Project> {

    // region Plugin

    /** @inheritdoc */
    override fun apply(target: Project) {
        val extension = target.extensions.create(EXT_NAME, DdExtension::class.java)
        val apiKeyProvider = resolveApiKey(target)
        // need to use withPlugin instead of afterEvaluate, because otherwise generated assets
        // folder with buildId is not picked by AGP by some reason
        target.pluginManager.withPlugin("com.android.application") {
            val knownVariants = mutableListOf<AppVariant>()
            val forceLegacyVariant = target.providers.gradleProperty(OO_FORCE_LEGACY_VARIANT_API)
            if (CurrentAgpVersion.CAN_ENABLE_NEW_VARIANT_API && !forceLegacyVariant.isPresent) {
                val androidComponentsExtension = target.androidApplicationComponentExtension ?: return@withPlugin
                androidComponentsExtension.onVariants { variant ->
                    val appVariant = AppVariant.create(variant, target)
                    knownVariants.add(appVariant)
                    configureTasksForVariant(
                        target,
                        extension,
                        appVariant,
                        apiKeyProvider
                    )
                }
            } else {
                val androidExtension = target.legacyAndroidApplicationExtension ?: return@withPlugin
                androidExtension.applicationVariants.all { variant ->
                    val appVariant = AppVariant.create(variant, androidExtension, target)
                    knownVariants.add(appVariant)
                    if (extension.enabled) {
                        configureTasksForVariant(
                            target,
                            extension,
                            appVariant,
                            apiKeyProvider
                        )
                    }
                }
            }

            target.afterEvaluate {
                if (extension.enabled) {
                    warnOnUnknownVariants(extension, knownVariants, target)
                }
            }
        }

        if (CurrentAgpVersion.IMPLEMENTS_BUILT_IN_KOTLIN) {
            target.pluginManager.apply(OpenObserveKotlinCompilerPluginSupport::class.java)
        } else {
            target.pluginManager.withPlugin("org.jetbrains.kotlin.android") {
                target.pluginManager.apply(OpenObserveKotlinCompilerPluginSupport::class.java)
            }
        }

        target.afterEvaluate {
            if (!target.isAndroidAppPluginApplied && extension.composeInstrumentation == InstrumentationMode.DISABLE) {
                // We show this error when composeInstrumentation is not enabled but the whole plugin is still applied
                // to a non-Android module.
                LOGGER.error(ERROR_NOT_ANDROID)
            } else if (!extension.enabled) {
                LOGGER.info(MSG_PLUGIN_DISABLED)
            }
        }
    }

    // endregion

    // region Internal

    internal fun configureTasksForVariant(
        target: Project,
        ooExtension: DdExtension,
        variant: AppVariant,
        apiKeyProvider: Provider<ApiKey>
    ) {
        val isObfuscationEnabled = isObfuscationEnabled(variant, ooExtension)
        val isNativeBuildRequired = variant.isNativeBuildEnabled
        val isNativeSymbolsTaskRequired =
            isNativeBuildRequired || ooExtension.additionalSymbolFilesLocations?.isNotEmpty() == true

        if (isObfuscationEnabled || isNativeBuildRequired || isNativeSymbolsTaskRequired) {
            val buildIdGenerationTask =
                configureBuildIdGenerationTask(target, variant)

            if (isObfuscationEnabled) {
                configureVariantForUploadTask(
                    target,
                    variant,
                    buildIdGenerationTask,
                    apiKeyProvider,
                    ooExtension
                )
            } else {
                LOGGER.info("Minifying disabled for variant ${variant.name}, no mapping file upload task created")
            }

            if (isNativeSymbolsTaskRequired) {
                configureNdkSymbolUploadTask(
                    target,
                    ooExtension,
                    variant,
                    buildIdGenerationTask,
                    apiKeyProvider
                )
            } else {
                LOGGER.info(
                    "No native build tasks found for variant ${variant.name}," +
                        " no additionalSymbolFilesLocations provided," +
                        " no NDK symbol file upload task created."
                )
            }
        }

        if (variant is NewApiAppVariant) {
            // need to run this in afterEvaluate, because with new Variant API tasks won't be created yet at this point
            target.afterEvaluate {
                configureVariantForSdkCheck(target, variant, ooExtension)
            }
        } else {
            configureVariantForSdkCheck(target, variant, ooExtension)
        }
    }

    /**
     * Resolves the OpenObserve API key from multiple sources using lazy evaluation.
     *
     * The API key is resolved in the following priority order (first match wins):
     * 1. OO_API_KEY gradle property (e.g., -PDD_API_KEY=xxx or gradle.properties)
     * 2. OPENOBSERVE_API_KEY gradle property (e.g., -POPENOBSERVE_API_KEY=xxx or gradle.properties)
     * 3. OO_API_KEY environment variable
     * 4. OPENOBSERVE_API_KEY environment variable
     * 5. apiKey field in datadog-ci.json file (searched in project directory and parent directories)
     * 6. NONE (default - will cause task validation to fail)
     *
     * @param target The Gradle project to resolve the API key for
     * @return A Provider that lazily evaluates to the resolved API key and its source
     */
    internal fun resolveApiKey(target: Project): Provider<ApiKey> {
        // https://docs.gradle.org/current/javadoc/org/gradle/api/provider/ProviderFactory.html
        // The Callable may return null, in which case the provider is considered to have no value.
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        fun resolve(key: String, provider: (String) -> Provider<String>, source: ApiKeySource) =
            provider(key).map { it.ifBlank { null } }.map { ApiKey(it, source) }

        val providers = target.providers
        val ooCiFileProvider = providers.provider {
            TaskUtils.findOpenObserveCiFile(target.projectDir)
        }.map {
            val apiKey = it.parseAsOpenObserveCiConfig()?.apiKey
            if (!apiKey.isNullOrEmpty()) {
                LOGGER.info("API key found in OpenObserve CI config file, using it.")
                ApiKey(apiKey, ApiKeySource.OPENOBSERVE_CI_CONFIG_FILE)
            } else {
                null
            }
        }

        return resolve(key = OO_API_KEY, provider = providers::gradleProperty, source = GRADLE_PROPERTY)
            .orElse(resolve(key = OPENOBSERVE_API_KEY, provider = providers::gradleProperty, source = GRADLE_PROPERTY))
            .orElse(resolve(key = OO_API_KEY, provider = providers::environmentVariable, source = ENVIRONMENT))
            .orElse(resolve(key = OPENOBSERVE_API_KEY, provider = providers::environmentVariable, source = ENVIRONMENT))
            .orElse(ooCiFileProvider)
            .orElse(target.providers.provider { ApiKey.NONE })
    }

    internal fun resolveSite(target: Project, extensionConfiguration: DdExtensionConfiguration): Provider<String> {
        return target.providers
            .provider {
                val site = extensionConfiguration.site?.ifBlank { null }
                if (site != null) {
                    LOGGER.info("Site property found in the extension configuration.")
                }
                site
            }
            .run {
                if (!extensionConfiguration.ignoreOpenObserveCiFileConfig) {
                    orElse(siteFromOpenObserveCiConfig(target))
                } else {
                    this
                }
            }
            .orElse(
                target.providers
                    .environmentVariable(OPENOBSERVE_SITE)
                    .map {
                        val site = OpenObserveSite.fromDomain(it)
                        if (site == null) {
                            LOGGER.warn("Unknown OpenObserve domain provided in environment variable: $it, ignoring it.")
                            null
                        } else {
                            LOGGER.info("Site property found in environment variable, using it.")
                            site.name
                        }
                    }
            )
            .orElse(OpenObserveSite.US1.name)
    }

    private fun siteFromOpenObserveCiConfig(target: Project): Provider<String> {
        return target.providers.provider {
            TaskUtils.findOpenObserveCiFile(target.projectDir)
        }.map {
            val siteAsDomain = it.parseAsOpenObserveCiConfig()?.site
            if (!siteAsDomain.isNullOrEmpty()) {
                val site = OpenObserveSite.fromDomain(siteAsDomain)
                if (site == null) {
                    LOGGER.warn(
                        "Unknown OpenObserve domain provided in OpenObserve CI config file: $siteAsDomain, ignoring it."
                    )
                    null
                } else {
                    LOGGER.info("Site property found in OpenObserve CI config file, using it.")
                    site.name
                }
            } else {
                null
            }
        }
    }

    private fun configureNdkSymbolUploadTask(
        target: Project,
        extension: DdExtension,
        variant: AppVariant,
        buildIdTask: TaskProvider<GenerateBuildIdTask>,
        apiKeyProvider: Provider<ApiKey>
    ): TaskProvider<NdkSymbolFileUploadTask> {
        val extensionConfiguration = resolveExtensionConfiguration(extension, variant)

        val uploadTask = NdkSymbolFileUploadTask.register(
            target,
            variant,
            buildIdTask,
            GenerateBuildIdTask.buildIdFile(target, variant.name),
            apiKeyProvider,
            resolveSite(target, extensionConfiguration),
            extensionConfiguration,
            GitRepositoryDetector(execOps)
        )

        return uploadTask
    }

    @Suppress("StringLiteralDuplication")
    private fun configureBuildIdGenerationTask(
        target: Project,
        variant: AppVariant
    ): TaskProvider<GenerateBuildIdTask> {
        val buildIdGenerationTask = GenerateBuildIdTask.register(target, variant)

        return buildIdGenerationTask
    }

    @Suppress("DefaultLocale", "ReturnCount")
    internal fun configureVariantForUploadTask(
        target: Project,
        variant: AppVariant,
        buildIdGenerationTask: TaskProvider<GenerateBuildIdTask>,
        apiKeyProvider: Provider<ApiKey>,
        extension: DdExtension
    ): TaskProvider<MappingFileUploadTask> {
        val uploadTaskName = UPLOAD_TASK_NAME +
            variant.name.replaceFirstChar { capitalizeChar(it) }
        val uploadTask = target.tasks.register(
            uploadTaskName,
            MappingFileUploadTask::class.java,
            GitRepositoryDetector(execOps)
        ).apply {
            configure { uploadTask ->
                val extensionConfiguration = resolveExtensionConfiguration(extension, variant)
                configureVariantTask(
                    target.objects,
                    uploadTask,
                    apiKeyProvider,
                    resolveSite(target, extensionConfiguration),
                    extensionConfiguration,
                    variant
                )

                uploadTask.buildIdFile.set(GenerateBuildIdTask.buildIdFile(target, variant.name))
                uploadTask.mustRunAfter(buildIdGenerationTask)
                uploadTask.mappingFilePackagesAliases = extensionConfiguration.mappingFilePackageAliases
                uploadTask.mappingFileTrimIndents = extensionConfiguration.mappingFileTrimIndents

                uploadTask.repositoryFile.set(TaskUtils.resolveOpenObserveRepositoryFile(target))
            }
        }

        return uploadTask
    }

    @Suppress("ReturnCount")
    internal fun configureVariantForSdkCheck(
        target: Project,
        variant: AppVariant,
        extension: DdExtension
    ): TaskProvider<CheckSdkDepsTask>? {
        if (!extension.enabled) {
            LOGGER.info("Extension disabled for variant ${variant.name}, no sdk check task created")
            return null
        }

        val compileTask = findCompilationTask(target.tasks, variant)

        if (compileTask == null) {
            LOGGER.warn(
                "Cannot find compilation task for the ${variant.name} variant, please" +
                    " report the issue at" +
                    " https://github.com/openobserve/openobserve-sdk-android-gradle-plugin/issues"
            )
            return null
        } else {
            val extensionConfiguration = resolveExtensionConfiguration(
                extension,
                variant
            )
            if (extensionConfiguration.checkProjectDependencies == SdkCheckLevel.NONE ||
                extensionConfiguration.checkProjectDependencies == null
            ) {
                return null
            }
            val checkDepsTaskName = "checkSdkDeps${variant.name.replaceFirstChar { capitalizeChar(it) }}"
            val resolvedCheckDependencyFlag =
                extensionConfiguration.checkProjectDependencies ?: SdkCheckLevel.FAIL
            val checkDepsTaskProvider = target.tasks.register(
                checkDepsTaskName,
                CheckSdkDepsTask::class.java
            ) {
                it.configurationName.set(variant.compileConfiguration.name)
                it.sdkCheckLevel.set(resolvedCheckDependencyFlag)
                it.variantName.set(variant.name)
            }
            compileTask.finalizedBy(checkDepsTaskProvider)
            return checkDepsTaskProvider
        }
    }

    @Suppress("DefaultLocale")
    private fun findCompilationTask(
        taskContainer: TaskContainer,
        appVariant: AppVariant
    ): Task? {
        val variantCapitalized = appVariant.name.replaceFirstChar { capitalizeChar(it) }
        // variants will have name like proDebug, but compile task will have a name like
        // compileProDebugSources. It can be other tasks like compileProDebugAndroidTestSources
        // or compileProDebugUnitTestSources, but we are not interested in these. This is fragile
        // and depends on the AGP naming convention

        // tricky moment: compileXXXSources exists before AGP 7.1.0, but in AGP 7.1 it is in the
        // container, but doesn't participate in the build process (=> not called). On the other
        // hand compileXXXJavaWithJavac exists on AGP 7.1 and is part of the build process. So we
        // will try first to get newer task and if it is not there, then fallback to the old one.
        return taskContainer.findByName("compile${variantCapitalized}JavaWithJavac")
            ?: taskContainer.findByName("compile${variantCapitalized}Sources")
    }

    private fun resolveMappingFile(
        extensionConfiguration: DdExtensionConfiguration,
        objectFactory: ObjectFactory,
        variant: AppVariant
    ): Provider<RegularFile> {
        val customPath = extensionConfiguration.mappingFilePath
        return if (customPath != null) {
            objectFactory.fileProperty().fileValue(File(customPath))
        } else {
            variant.mappingFile
        }
    }

    private fun configureVariantTask(
        objectFactory: ObjectFactory,
        uploadTask: MappingFileUploadTask,
        apiKeyProvider: Provider<ApiKey>,
        siteProvider: Provider<String>,
        extensionConfiguration: DdExtensionConfiguration,
        variant: AppVariant
    ) {
        uploadTask.apiKey.set(apiKeyProvider.map { it.value })
        uploadTask.apiKeySource.set(apiKeyProvider.map { it.source })
        uploadTask.variantName.set(variant.flavorName)

        uploadTask.applicationId.set(variant.applicationId)

        uploadTask.mappingFile.set(resolveMappingFile(extensionConfiguration, objectFactory, variant))
        uploadTask.sourceSetRoots.set(variant.collectJavaAndKotlinSourceDirectories())

        uploadTask.site.set(siteProvider)
        if (extensionConfiguration.versionName != null) {
            uploadTask.versionName.set(extensionConfiguration.versionName)
        } else {
            uploadTask.versionName.set(variant.versionName)
        }
        uploadTask.versionCode.set(variant.versionCode)
        if (extensionConfiguration.serviceName != null) {
            uploadTask.serviceName.set(extensionConfiguration.serviceName)
        } else {
            uploadTask.serviceName.set(variant.applicationId)
        }
        uploadTask.remoteRepositoryUrl.set(extensionConfiguration.remoteRepositoryUrl.orEmpty())

        variant.bindWith(uploadTask)
    }

    /**
     * Returns the set of names registered in [DdExtension.variants] that do not correspond to any
     * real partial variant combination generated from [knownVariants].
     *
     * Uses [VariantIterator] to produce all valid partial names for each real variant (the same
     * logic used during config resolution), then returns configured names not present in that set.
     */
    internal fun findUnknownVariantConfigNames(
        extension: DdExtension,
        knownVariants: Collection<AppVariant>
    ): Set<String> {
        val configuredNames = extension.variants.names
        if (configuredNames.isEmpty()) return emptySet()

        val validPartialNames = mutableSetOf<String>()
        for (variant in knownVariants) {
            VariantIterator(variant.flavors + variant.buildTypeName)
                .forEach { validPartialNames.add(it) }
        }

        return configuredNames.filterTo(mutableSetOf()) { it !in validPartialNames }
    }

    internal fun warnOnUnknownVariants(
        extension: DdExtension,
        knownVariants: Collection<AppVariant>,
        target: Project
    ) {
        val unknownNames = findUnknownVariantConfigNames(extension, knownVariants)
        if (unknownNames.isEmpty()) return

        val variantList = unknownNames.joinToString("\n") { "  - $it" }
        LOGGER.warn(WARN_UNKNOWN_VARIANTS.format(target.path, variantList))
    }

    internal fun resolveExtensionConfiguration(
        extension: DdExtension,
        variant: AppVariant
    ): DdExtensionConfiguration {
        val configuration = DdExtensionConfiguration()
        configuration.updateWith(extension)

        val flavors = variant.flavors
        val buildType = variant.buildTypeName
        val iterator = VariantIterator(flavors + buildType)
        iterator.forEach {
            val config = extension.variants.findByName(it)
            if (config != null) {
                configuration.updateWith(config)
            }
        }
        return configuration
    }

    private fun isObfuscationEnabled(
        variant: AppVariant,
        extension: DdExtension
    ): Boolean {
        val extensionConfiguration = resolveExtensionConfiguration(extension, variant)
        val isDefaultObfuscationEnabled = variant.isMinifyEnabled
        val isNonDefaultObfuscationEnabled = extensionConfiguration.nonDefaultObfuscation
        return isDefaultObfuscationEnabled || isNonDefaultObfuscationEnabled
    }

    private val Project.isAndroidAppPluginApplied: Boolean
        get() = extensions.findByName("android")?.let {
            // legacy vs new API
            it is AppExtension || it is ApplicationExtension
        } ?: false

    private val Project.legacyAndroidApplicationExtension: AppExtension?
        get() = extensions.findByType(AppExtension::class.java)

    private val Project.androidApplicationComponentExtension: ApplicationAndroidComponentsExtension?
        get() = extensions.findByType(ApplicationAndroidComponentsExtension::class.java)

    private fun File.parseAsOpenObserveCiConfig(): OpenObserveCiConfig? {
        return try {
            val config = JSONObject(readText())
            OpenObserveCiConfig(
                site = config.optString(OPENOBSERVE_CI_SITE_PROPERTY, null),
                apiKey = config.optString(OPENOBSERVE_CI_API_KEY_PROPERTY, null)
            )
        } catch (jsonException: JSONException) {
            LOGGER.warn("Failed to parse datadog-ci.json", jsonException)
            null
        } catch (ioException: IOException) {
            LOGGER.warn("Failed to read datadog-ci.json file", ioException)
            null
        }
    }

    private data class OpenObserveCiConfig(val site: String?, val apiKey: String?)

    // endregion

    internal companion object {

        internal const val OPENOBSERVE_CI_API_KEY_PROPERTY = "apiKey"
        internal const val OPENOBSERVE_CI_SITE_PROPERTY = "ooSite"
        internal const val OPENOBSERVE_SITE = "OPENOBSERVE_SITE"

        private const val OO_FORCE_LEGACY_VARIANT_API = "dd-force-legacy-variant-api"

        internal const val OO_API_KEY = "OO_API_KEY"

        internal const val OPENOBSERVE_API_KEY = "OPENOBSERVE_API_KEY"

        internal const val OPENOBSERVE_TASK_GROUP = "datadog"

        internal val LOGGER = Logging.getLogger("DdAndroidGradlePlugin")

        private const val EXT_NAME = "datadog"

        internal const val UPLOAD_TASK_NAME = "uploadMapping"

        private const val ERROR_NOT_ANDROID = "The dd-android-gradle-plugin has been applied on " +
            "a non-Android application project"

        private const val MSG_PLUGIN_DISABLED =
            "OpenObserve extension disabled, no upload task created, no Compose instrumentation applied"

        internal const val WARN_UNKNOWN_VARIANTS =
            "============================================================\n" +
                "OpenObserve plugin config mentions variant(s) that don't match\n" +
                "any variant in module `%s`:\n" +
                "%s\n" +
                "Check for typos or remove obsolete entries.\n" +
                "============================================================"
    }
}

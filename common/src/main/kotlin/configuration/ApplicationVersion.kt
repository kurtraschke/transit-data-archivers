@file:OptIn(ExperimentalHoplite::class)

package systems.choochoo.transit_data_archivers.common.configuration

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceSource
import java.time.Instant

data class ApplicationVersion(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val commitId: String,
    val branch: String,
    val buildTimestamp: Instant
)

fun loadApplicationVersion(): ApplicationVersion = ConfigLoaderBuilder.empty()
    .addDefaultDecoders()
    .addDefaultPreprocessors()
    .addDefaultNodeTransformers()
    .addDefaultParamMappers()
    .addDefaultParsers()
    .addResourceSource("/maven-version.properties")
    .withExplicitSealedTypes()
    .build()
    .loadConfigOrThrow<ApplicationVersion>()
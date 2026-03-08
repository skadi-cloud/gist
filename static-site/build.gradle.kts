plugins {
    application
    kotlin("jvm")
}

val targetJvm: String by project

application {
    mainClass.set("cloud.skadi.gist.static.GeneratorKt")
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(11))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = targetJvm
        apiVersion = "1.5"
    }
}

val ktor_version = "1.6.8"
val exposed_version = "0.48.0"

dependencies {
    // Reuse server code: data models, queries, markdown rendering, base62 encoding.
    implementation(project(":server"))

    // The following are transitive dependencies of :server, but because :server declares
    // them as `implementation` (not `api`), they are not exposed on the compile classpath
    // of consumers.  We need to list them here explicitly so the generator code compiles.

    // kotlinx.html DSL types (FlowContent, BODY, createHTML, etc.)
    implementation("io.ktor:ktor-html-builder:$ktor_version")

    // Exposed ORM types used in queries (SortOrder, transaction, etc.)
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")

    // Shared models (GistVisibility enum)
    implementation(project(":shared"))

    // PostgreSQL JDBC driver (needed at runtime for Database.connect)
    implementation("org.postgresql:postgresql:42.7.3")
}

/**
 * Custom task to generate the static site.
 *
 * Required environment variables:
 *   SQL_USER      - PostgreSQL username
 *   SQL_PASSWORD  - PostgreSQL password
 *   SQL_HOST      - PostgreSQL host:port (default: localhost:5432)
 *   SQL_DB        - PostgreSQL database name (default: gist)
 *
 * Optional environment variables:
 *   OUTPUT_DIR    - Output directory for the static site (default: build/static-site)
 *   STORAGE_DIR   - Path to the local image storage directory (required to include gist images)
 */
val generateStaticSite by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Generates the static site from the database content"
    dependsOn(tasks.named("classes"))
    mainClass.set("cloud.skadi.gist.static.GeneratorKt")
    classpath = sourceSets["main"].runtimeClasspath
    environment(
        mapOf(
            "OUTPUT_DIR" to (System.getenv("OUTPUT_DIR") ?: "${layout.buildDirectory.get().asFile}/static-site"),
            "STORAGE_DIR" to (System.getenv("STORAGE_DIR") ?: ""),
            "SQL_USER" to (System.getenv("SQL_USER") ?: ""),
            "SQL_PASSWORD" to (System.getenv("SQL_PASSWORD") ?: ""),
            "SQL_HOST" to (System.getenv("SQL_HOST") ?: "localhost:5432"),
            "SQL_DB" to (System.getenv("SQL_DB") ?: "gist")
        )
    )
}

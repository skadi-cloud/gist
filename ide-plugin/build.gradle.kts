import org.jetbrains.intellij.tasks.PatchPluginXmlTask

plugins {
    id("org.jetbrains.intellij")
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
    maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
}

val intellijVersion: String by project
val mpsVersion: String by project
val targetJvm: String by project

version = "$intellijVersion.3"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.0")

    implementation("io.ktor:ktor-client-core:1.6.8")
    implementation("io.ktor:ktor-client-java:2.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-html:0.8.1")

    implementation(project(":shared"))

    compileOnly("com.jetbrains:mps-workbench:$mpsVersion")
    compileOnly("com.jetbrains:mps-core:$mpsVersion")
    compileOnly("com.jetbrains:mps-platform:$mpsVersion")
    compileOnly("com.jetbrains:mps-openapi:$mpsVersion")
    compileOnly("com.jetbrains:mps-editor:$mpsVersion")
    compileOnly("com.jetbrains:mps-editor-api:$mpsVersion")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version.set(intellijVersion)

}


java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(11))
}


tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = targetJvm
    kotlinOptions.apiVersion = "1.4"
}

tasks.getByName("buildSearchableOptions").enabled = false
tasks.withType<PatchPluginXmlTask>().configureEach {
    untilBuild.set("")
}

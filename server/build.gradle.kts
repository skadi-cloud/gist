plugins {
    application
    kotlin("jvm")
}

val ktor_version = "1.6.8"
val logback_version = "1.4.6"
val prometeus_version = "1.10.6"
val exposed_version = "0.48.0"

val targetJvm: String by project


application {
    mainClass.set("cloud.skadi.gist.ApplicationKt")
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

dependencies {
    implementation(project (":shared"))

    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-auth:$ktor_version")
    implementation("io.ktor:ktor-locations:$ktor_version")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-apache:$ktor_version")
    implementation("io.ktor:ktor-server-sessions:$ktor_version")
    implementation("io.ktor:ktor-server-host-common:$ktor_version")
    implementation("io.ktor:ktor-metrics:$ktor_version")
    implementation("io.ktor:ktor-metrics-micrometer:$ktor_version")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometeus_version")
    implementation("io.ktor:ktor-html-builder:$ktor_version")
    implementation("io.ktor:ktor-websockets:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-jackson:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")

    implementation("io.seruco.encoding:base62:0.1.3")

    implementation("io.sentry:sentry-logback:7.6.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.kohsuke:github-api:1.319")

    implementation("org.bouncycastle:bcprov-jdk15on:1.70")


    implementation (platform ("software.amazon.awssdk:bom:2.25.11"))
    implementation("software.amazon.awssdk:s3")

    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jsoup:jsoup:1.17.2")
}

val copyJs by tasks.creating(Copy::class.java){
    dependsOn(":js:build")
    from(File(rootDir, "/js/dist/script.js"))
    into(File(projectDir, "src/main/resources/static"))
}

tasks.getByName("processResources").dependsOn.add(copyJs)
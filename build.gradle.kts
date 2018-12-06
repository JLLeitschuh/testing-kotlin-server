plugins {
    id("org.jetbrains.kotlin.jvm").version("1.3.10")
    id("com.diffplug.gradle.spotless") version "3.16.0"
    application
}

repositories {
    jcenter()
}

dependencies {
    // Use the Kotlin JDK 8 standard library
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    val ktorVersion = "1.0.1"
    implementation(group = "io.ktor", name = "ktor-server-netty", version = ktorVersion)
    implementation(group = "com.github.ajalt", name = "clikt", version = "1.3.0")
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.0.13")

    // Use the Kotlin test library
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

spotless {
    kotlin {
        ktlint("0.29.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

application {
    mainClassName = "org.jlleitschuh.testing.server.AppKt"
}

tasks.register("stage") {
    description = "Task executed by heroku to build this gradle project for deployment."
    group = "heroku"
    dependsOn("installDist")
}

tasks.withType<Wrapper>().configureEach {
    gradleVersion = "5.0"
    distributionType = Wrapper.DistributionType.ALL
}

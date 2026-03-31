 // NovaLang Interpreter Gradle Build File

plugins {
    java
    jacoco // Required for code coverage report
    application // For easy running via 'gradle run'
    pmd
    checkstyle
    id("io.freefair.lombok") version "9.2.0"
    id("com.github.spotbugs") version "6.4.8" 
}

group = "com.novalang"
version = "1.0-SNAPSHOT"

// Ensure we target the required Java version
java.toolchain {
    // Assuming Java 25 is the target (or the latest LTS supporting virtual threads/sealed types)
    languageVersion.set(JavaLanguageVersion.of(25))

}

repositories { 
    mavenCentral() 
}

/*
// Enable preview features needed for records, sealed classes, and pattern matching
tasks.withType(JavaCompile) {
    options.compilerArgs.addAll(['--enable-preview'])
}

tasks.withType(Test) {
    useJUnitPlatform()
    // Required to run tests that use preview features like Virtual Threads
    jvmArgs.addAll(['--enable-preview']) 
}
*/

// Configure the main class for 'gradle run'
application {
    mainClass = "com.novalang.App"
}

// Dependencies: JUnit 5 for testing and Lombok
dependencies {
    // JUnit 5 Platform and Jupiter Engine for testing (MLO10)
    testImplementation(platform("org.junit:junit-bom:6.0.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Project Lombok 
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
}

// JaCoCo configuration (Required for 70% coverage)
jacoco {
    toolVersion = "0.8.14"
}

pmd {
    isConsoleOutput = true
    toolVersion = "7.21.0" 
    rulesMinimumPriority = 5
    // Point to your new file
    ruleSets = listOf() // Clear default
    ruleSetFiles = files("config/pmd/ruleset.xml") 
}

spotbugs {
    toolVersion = "4.9.8"
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    // Point to your new exclude file
    showProgress.set(true)
    excludeFilter.set(file("config/spotbugs/exclude.xml"))
    reportLevel.set(com.github.spotbugs.snom.Confidence.DEFAULT)
}

checkstyle {
    toolVersion = "10.21.2" // Move to the latest available version
    isIgnoreFailures = false
    configFile = file("${rootProject.projectDir}/config/checkstyle/checkstyle.xml")
}

tasks.test {
    // Ensures Gradle uses the JUnit Platform to run tests
    useJUnitPlatform()

    finalizedBy(tasks.jacocoTestReport) // run report after tests

    // Provides helpful console output during testing
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(false)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal() // 80% line coverage
            }
        }
    }
}

tasks.withType<Checkstyle>().configureEach {
    configProperties = mapOf(
        "config_loc" to rootProject.file("config/checkstyle")
    )

    reports {
        xml.required = false
        html.required = true
        sarif.required = true
    }
}
plugins {
    java
    id("com.gradleup.shadow") version "8.3.10"
}

group = "com.stabdan"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.14.0")
    implementation("software.amazon.awssdk:dynamodb:2.29.52")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("all")

    isZip64 = true
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
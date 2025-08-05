plugins {
    kotlin("jvm") version "2.1.10"
    id("com.google.devtools.ksp") version "2.1.10-1.0.29"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // KSP
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.10-1.0.29")
    
    // ASM for bytecode analysis
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
    implementation("org.ow2.asm:asm-util:9.7")
    
    // Spring annotations for analysis
//    implementation("org.springframework:spring-web:6.1.4")
//    implementation("org.springframework:spring-webmvc:6.1.4")
    
    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    
    // Reflection
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.10")
    
    // File processing
    implementation("org.apache.commons:commons-lang3:3.14.0")
    
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

// KSP 설정
ksp {
    arg("option1", "value1")
}

// Application 설정
application {
    mainClass.set("MainKt")
}
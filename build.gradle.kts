plugins {
    java
}

group = "io.bluestaggo"
version = "1.0.0"

java.toolchain {
    languageVersion = JavaLanguageVersion.of(8)
}

repositories {
    mavenCentral()
    maven("https://repo.md-5.net/content/groups/public")
}

dependencies {
    compileOnly("org.bukkit:bukkit:1.4.7-R1.0")
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to version)
        }
    }
}
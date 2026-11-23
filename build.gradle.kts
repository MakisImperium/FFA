plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow") version "9.6.1"
}

group = "net.zernyth.ffa"
version = providers.gradleProperty("releaseVersion").getOrElse("3.0.0")

repositories {
    mavenCentral()
    maven("https://repo.powernukkitx.org/releases") {
        content {
            includeGroup("org.powernukkitx")
        }
    }
}

dependencies {
    compileOnly("org.powernukkitx:server:3.0.6-SNAPSHOT:all") {
        isTransitive = false
    }
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.mysql:mysql-connector-j:9.4.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveFileName = "ZernythFFA.jar"
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            from(components["shadow"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MakisImperium/FFA")
            credentials {
                username = System.getenv("USERNAME")
                password = System.getenv("TOKEN")
            }
        }
    }
}

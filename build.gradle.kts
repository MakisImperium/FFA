plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow") version "9.6.1"
}

group = "net.zernyth.ffa"
version = providers.gradleProperty("releaseVersion").getOrElse("4.0.0")

repositories {
    mavenCentral()
    maven("https://repo.powernukkitx.org/releases") {
        content {
            includeGroup("org.powernukkitx")
        }
    }
}

// The server is a snapshot; checking it once a week instead of daily keeps builds working while
// repo.powernukkitx.org is unreachable, which happens.
configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(7, "days")
}

dependencies {
    compileOnly("org.powernukkitx:server:3.0.6-SNAPSHOT:all") {
        isTransitive = false
    }
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.mysql:mysql-connector-j:9.4.0")
    // Connection pool for MySQL. SLF4J comes with the server, so the plugin does not ship a second copy.
    implementation("com.zaxxer:HikariCP:6.3.3") {
        exclude(group = "org.slf4j")
    }
    testImplementation("org.slf4j:slf4j-api:2.0.16")

    // The server classes for tests that touch PowerNukkitX types; most tests need none.
    testImplementation("org.powernukkitx:server:3.0.6-SNAPSHOT:all") {
        isTransitive = false
    }
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // MySQL in a container for the repository test; skipped where Docker is not running.
    testImplementation("org.testcontainers:mysql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
}

tasks.test {
    useJUnitPlatform()
    // Docker Desktop 29 rejects the API version Testcontainers 1.21 asks for by default.
    environment("DOCKER_API_VERSION", "1.44")
    systemProperty("api.version", "1.44")
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

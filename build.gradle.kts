plugins {
    idea
    kotlin("jvm") version "1.7.20"
}

base.archivesName.set("Simpledeobf")
version = "0.7"
group = "com.octarine"

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

repositories {
    mavenCentral()
}

val shade: Configuration by configurations.creating

dependencies {
    shade("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.21")
    shade("org.ow2.asm:asm-debug-all:6.0_BETA")
    shade("net.sf.jopt-simple:jopt-simple:6.0-alpha-3")
    shade("com.nothome:javaxdelta:2.0.1")
    implementation(shade)
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf(
                "-Xinline-classes",
                "-Xopt-in=kotlin.contracts.ExperimentalContracts",
                "-Xlambdas=indy",
                "-Xjvm-default=all",
            )
        }
    }
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest {
            attributes(
                "Main-Class" to "com.octarine.simpledeobf.Main"
            )
        }
        from(
            shade.map {
                if (it.isDirectory) it
                else zipTree(it)
            }
        )
        exclude(
            "org/intellij/**",
            "org/jetbrains/**",
            "gnu/**",
            "module-info.class",
            "META-INF/maven/**",
            "META-INF/versions/**",
            "META-INF/*.kotlin_module"
        )
    }
}

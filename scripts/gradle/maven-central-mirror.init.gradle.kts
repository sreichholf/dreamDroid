// Serve Maven Central artifacts from Google's GCS mirror, falling back to
// Maven Central only for what the mirror does not have yet (a 404 moves on to
// the next repository; a 429 does not). Sonatype throttles by egress IP, and
// CI runners and cloud agent sessions share theirs with many other builds.
// Installed into ~/.gradle/init.d by CI and .claude/hooks/session-start.sh.

val central = "https://repo.maven.apache.org/maven2"
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
val fallbackName = "MavenCentralFallback"

fun RepositoryHandler.preferMirror() {
    withType<MavenArtifactRepository>().configureEach {
        if (name != fallbackName && url.toString().trimEnd('/') == central) {
            setUrl(mirror)
        }
    }
}

fun RepositoryHandler.addFallback() {
    val mirrored = any { it is MavenArtifactRepository && it.url.toString() == mirror }
    if (mirrored && none { it.name == fallbackName }) {
        mavenCentral { name = fallbackName }
    }
}

beforeSettings {
    pluginManagement.repositories.preferMirror()
    dependencyResolutionManagement.repositories.preferMirror()
}

settingsEvaluated {
    pluginManagement.repositories.addFallback()
    dependencyResolutionManagement.repositories.addFallback()
}

allprojects {
    buildscript.repositories.preferMirror()
    repositories.preferMirror()
    afterEvaluate {
        buildscript.repositories.addFallback()
        repositories.addFallback()
    }
}

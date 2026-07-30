import org.gradle.api.Project
import java.io.File
import java.util.Properties

object Secrets {
    private val props = Properties()

    private fun load(rootDir: File) {
        if (props.isNotEmpty()) return
        val f = File(rootDir, "keystore.properties")
        if (f.exists()) {
            f.inputStream().use { props.load(it) }
        }
    }

    private fun gitRevisionCount(): Int = try {
        Runtime.getRuntime().exec("git rev-list --count HEAD")
            .inputStream.bufferedReader().readText().trim().toInt()
    } catch (e: Exception) {
        1
    }

    fun get(project: Project, name: String): String {
        load(project.rootProject.projectDir)
        return System.getenv(name)
            ?: props.getProperty(name)
            ?: project.properties[name]?.toString()?.removeSurrounding("\"")
            ?: ""
    }

    fun versionCode(project: Project): Int {
        (project.findProperty("appVersionCode") as? String)?.toIntOrNull()?.let { return it }
        return gitRevisionCount()
    }

    fun versionName(project: Project): String =
        (project.findProperty("appVersionName") as? String)
            ?: System.getenv("APP_VERSION_NAME")
            ?: "0.1.${gitRevisionCount()}"
}

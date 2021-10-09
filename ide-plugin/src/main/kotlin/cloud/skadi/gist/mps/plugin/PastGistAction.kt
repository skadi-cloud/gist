package cloud.skadi.gist.mps.plugin

import cloud.skadi.gist.mps.plugin.config.SkadiGistSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import kotlinx.coroutines.runBlocking
import java.awt.datatransfer.DataFlavor
import java.net.URL
import java.util.regex.Pattern

class PastGistAction() : AnAction("Import Gist") {
    private val cpManager = CopyPasteManager.getInstance()
    override fun update(e: AnActionEvent) {
        val instance = SkadiGistSettings.getInstance()
        val potentialLink: String? = cpManager.getContents(DataFlavor.stringFlavor)
        if (potentialLink != null) {
            try {
                val url = URL(potentialLink)
                val backEndUrl = URL(instance.backendAddress)
                if (url.host == backEndUrl.host && url.port == backEndUrl.port && url.path.startsWith("/gist/")) {
                    e.presentation.isEnabled = true
                    return
                }
            } catch (_: Exception) {
                //ignored
            }
        }
        e.presentation.isEnabled = false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val instance = SkadiGistSettings.getInstance()
        val potentialLink: String? = cpManager.getContents(DataFlavor.stringFlavor)
        if (potentialLink != null) {
            try {
                val url = URL(potentialLink)
                val backEndUrl = URL(instance.backendAddress)
                if (url.host == backEndUrl.host && url.port == backEndUrl.port && url.path.startsWith("/gist/")) {
                    val r = """${Pattern.quote("${backEndUrl.toExternalForm()}gist/")}(\w*)""".toRegex()
                        .matchEntire(potentialLink)

                    val gistId = r?.groupValues?.get(1)
                    if (gistId != null) {
                        runBlocking {
                            importIntoProject(gistId, e.project!!)
                        }
                    }
                }
            } catch (_: Exception) {
                //ignored
            }
        }
    }
}
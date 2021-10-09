package cloud.skadi.gist.mps.plugin

import cloud.skadi.gist.mps.plugin.config.SkadiGistSettings
import cloud.skadi.gist.mps.plugin.http.DoImportAction
import cloud.skadi.gist.mps.plugin.ui.ModelChooser
import cloud.skadi.gist.shared.ImportGistMessage
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import jetbrains.mps.ide.datatransfer.CopyPasteUtil
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.smodel.ModelAccess
import java.net.URL

private val mapper = JsonMapper.builder()
    .addModule(KotlinModule())
    .build()

suspend fun importIntoProject(gistId: String, project: Project) {
    val settings = SkadiGistSettings.getInstance()

    val response = client.get<HttpResponse>(URL("${settings.backendAddress}/gist/$gistId/nodes")) {
        accept(ContentType.Application.Json)
    }
    val toImport = mapper.readValue<ImportGistMessage>(response.content.toInputStream())


    if (toImport.roots.size == 1 && !toImport.roots.first().isRootNode) {

        ModelAccess.instance().runReadAction {
            CopyPasteUtil.copyNodeToClipboard(toImport.roots.first().root.toSNode(null))
        }

        val notificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("Skadi Gist")
        notificationGroup.createNotification(
            "Gist copied",
            "Gist '${toImport.name}' was copied to the clipboard."
        ).notify(project)

    } else {
        chooseModelAndImport(project, toImport)
    }
}

fun chooseModelAndImport(project: Project, toImport: ImportGistMessage): Boolean {
    val modelChooser = ModelChooser(project)
    if(modelChooser.showAndGet()) {
        val selectedModel = modelChooser.selectedModel
        if (selectedModel == null) {
            return false
        }
        val mpsProject = ProjectHelper.fromIdeaProject(project)
        mpsProject?.modelAccess?.executeCommandInEDT {
            toImport.roots.forEach {
                it.importInto(selectedModel)
            }
        }
        return true
    }
    return false
}
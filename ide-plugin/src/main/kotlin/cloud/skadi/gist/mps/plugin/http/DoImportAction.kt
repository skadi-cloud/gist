package cloud.skadi.gist.mps.plugin.http

import cloud.skadi.gist.mps.plugin.chooseModelAndImport
import cloud.skadi.gist.mps.plugin.importInto
import cloud.skadi.gist.mps.plugin.ui.ModelChooser
import cloud.skadi.gist.shared.ImportGistMessage
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import jetbrains.mps.ide.project.ProjectHelper


class DoImportAction(private val toImport: ImportGistMessage) : NotificationAction("Import Gist") {

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        val project = e.project!!
        if(chooseModelAndImport(project, toImport)) {
            notification.expire()
        }
    }
}


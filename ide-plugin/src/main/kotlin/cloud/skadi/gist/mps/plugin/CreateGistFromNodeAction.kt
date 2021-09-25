package cloud.skadi.gist.mps.plugin

import cloud.skadi.gist.mps.plugin.config.SkadiGistSettings
import cloud.skadi.gist.mps.plugin.ui.SkadiToolWindowController
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.wm.ToolWindowManager
import jetbrains.mps.ide.actions.MPSCommonDataKeys

class CreateGistFromNodeAction : AnAction("Create Gist" ) {
    val logger = Logger.getInstance(CreateGistFromNodeAction::class.java)
    init {
        isEnabledInModalContext = true
    }
    override fun actionPerformed(e: AnActionEvent) {
        val window = ToolWindowManager.getInstance(e.project!!).getToolWindow(SkadiToolWindowController.ID) ?: return
        window.activate {
            window.contentManager.selectedContent?.getUserData(SkadiToolWindowController.KEY)
                ?.createGist(e.project!!, e.dataContext)
        }
    }

    override fun update(e: AnActionEvent) {
        val node = e.dataContext.getData(MPSCommonDataKeys.NODE)
        e.presentation.isEnabled = node != null
    }
}
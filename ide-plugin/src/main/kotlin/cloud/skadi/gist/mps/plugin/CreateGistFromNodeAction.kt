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
            val model = e.dataContext.getData(MPSCommonDataKeys.CONTEXT_MODEL)
            if(model == null) {
                logger.error("no model")
                return@activate
            }
            val nodes = e.dataContext.getData(MPSCommonDataKeys.NODES) ?: listOf(e.dataContext.getData(MPSCommonDataKeys.NODE))

            if(nodes.filterNotNull().isEmpty()) {
                logger.error("no nodes")
                return@activate
            }

            window.contentManager.selectedContent?.getUserData(SkadiToolWindowController.KEY)
                ?.createGist(e.project!!, nodes, model.repository)
        }
    }

    override fun update(e: AnActionEvent) {
        val node = e.dataContext.getData(MPSCommonDataKeys.NODE)
        val nodes = e.dataContext.getData(MPSCommonDataKeys.NODES)
        e.presentation.isEnabled = nodes != null || node != null
    }
}
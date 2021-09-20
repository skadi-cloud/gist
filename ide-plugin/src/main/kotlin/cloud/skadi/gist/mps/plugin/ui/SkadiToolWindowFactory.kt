package cloud.skadi.gist.mps.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout

class SkadiToolWindowFactory: ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = SkadiToolWindowController(toolWindow)
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(myToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
        content.component.apply {
            layout = BorderLayout()
            background = UIUtil.getListBackground()
        }
        content.putUserData(SkadiToolWindowController.KEY, myToolWindow)
    }
}
package cloud.skadi.gist.mps.plugin.http

import cloud.skadi.gist.mps.plugin.CreateGistFromNodeAction
import cloud.skadi.gist.mps.plugin.importInto
import cloud.skadi.gist.shared.ImportGistMessage
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.ide.projectPane.logicalview.ProjectTree
import jetbrains.mps.ide.projectPane.logicalview.ProjectTreeFindHelper
import jetbrains.mps.ide.ui.tree.MPSTreeNode
import jetbrains.mps.ide.ui.tree.smodel.SModelTreeNode
import jetbrains.mps.ide.ui.tree.smodel.SNodeTreeNode
import org.jetbrains.mps.openapi.model.SModel
import java.awt.Dimension
import javax.swing.JComponent


class DoImportAction(private val toImport: ImportGistMessage) : NotificationAction("Import Gist") {
    val logger = Logger.getInstance(DoImportAction::class.java)

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        val project = e.project!!
        val modelChooser = ModelChooser(project)
        if(modelChooser.showAndGet()) {
            val selectedModel = modelChooser.selectedModel
            if (selectedModel == null) {
                logger.warn("no model selected")
                return
            }
            val mpsProject = ProjectHelper.fromIdeaProject(project)
            mpsProject?.modelAccess?.executeCommandInEDT {
                toImport.roots.forEach {
                    it.importInto(selectedModel)
                }
            }
            notification.expire()
        }
    }
}

class ModelChooser(project: Project): DialogWrapper(project) {
    private val myComponent: JComponent
    private val myTree: ProjectTree

    var selectedModel: SModel? = null
        private set

    val component
        get() = myComponent

    init {
        myComponent = JBScrollPane()
        myTree = ProjectTree(ProjectHelper.fromIdeaProject(project))
        myComponent.setViewportView(myTree)
        myTree.rebuildNow()
        init()
        title = "Select Model"
    }

    fun selectModel(model: SModel) {
        val helper = ProjectTreeFindHelper(myTree)
        val treeNode: MPSTreeNode = helper.findMostSuitableModelTreeNode(model)
        selectInTree(treeNode)
    }


    private fun selectInTree(treeNode: MPSTreeNode) {
        myTree.runWithoutExpansion { myTree.selectNode(treeNode) }
    }



    override fun doOKAction() {
        selectedModel = when (val selection = myTree.selectionPath?.lastPathComponent) {
            is SNodeTreeNode -> selection.sModelModelTreeNode?.model
            is SModelTreeNode -> selection.model
            else -> null
        }
        super.doOKAction()
    }

    override fun createCenterPanel(): JComponent {
        component.preferredSize = Dimension(400, 900)
        Disposer.register(disposable, myTree)
        return component
    }
}
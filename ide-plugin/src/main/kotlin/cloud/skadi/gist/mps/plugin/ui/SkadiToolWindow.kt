package cloud.skadi.gist.mps.plugin.ui

import cloud.skadi.gist.mps.plugin.calculateReader
import cloud.skadi.gist.mps.plugin.config.SkadiGistSettings
import cloud.skadi.gist.mps.plugin.upload
import cloud.skadi.gist.shared.GistVisibility
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.CopyPasteManagerEx
import com.intellij.ide.CopyPasteUtil
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SRepository
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Graphics
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.text.PlainDocument


class SkadiToolWindowController(private val window: ToolWindow) {

    companion object {
        const val ID = "Skadi Cloud"
        val KEY = Key.create<SkadiToolWindowController>("Skadi.Cloud.Gist.ToolWindow.Controller")
    }


    private val titleDocument = PlainDocument()
    lateinit var emptyText: StatusText
    private val descriptionDocument = PlainDocument()
    private val wrapper = object : JPanel() {
        override fun paintComponent(g: Graphics?) {
            super.paintComponent(g)
            emptyText.paint(this, g)
        }
    }
    private var showPlaceholder = true
    private val settings = SkadiGistSettings.getInstance()
    private var visiblility = settings.visiblility

    fun getContent(): JComponent {
        return wrapper
    }

    init {
        emptyText = object : StatusText(wrapper) {
            override fun isStatusVisible(): Boolean {
                return showPlaceholder
            }
        }
        placeholderContent()
    }

    private fun placeholderContent() {
        emptyText.appendLine("No node(s) selected.")
        emptyText.appendLine("To create a gist right on ad node in the editor")
        emptyText.appendLine("or select multiple nodes in the logical view.")
        emptyText.appendLine("")
        emptyText.appendLine("Help", SimpleTextAttributes.LINK_ATTRIBUTES, ActionListener {
            val gistSettings = SkadiGistSettings.getInstance()
            BrowserUtil.browse(gistSettings.backendAddress + "how-to")
        })
    }

    fun createGist(project: Project, nodes: List<SNode>, repo: SRepository) {
        val createAction = CreateGistAction(project, nodes, repo) { visiblility }
        val nodeNames = repo.modelAccess.calculateReader { nodes.map { it.presentation } } ?: emptyList()

        val cancelAction = object : AbstractAction("Cancel") {
            override fun actionPerformed(e: ActionEvent?) {
                titleDocument.remove(0, titleDocument.length)
                descriptionDocument.remove(0, descriptionDocument.length)
                removeMainContent()
                window.hide()
            }
        }

        val titleField = JBTextArea(titleDocument).apply {
            background = UIUtil.getListBackground()
            border = BorderFactory.createCompoundBorder(
                IdeBorderFactory.createBorder(SideBorder.BOTTOM),
                JBUI.Borders.empty(8)
            )
            emptyText.text = "Title"
            lineWrap = true
        }.also {
            CollaborationToolsUIUtil.overrideUIDependentProperty(it) {
                font = UIUtil.getLabelFont()
            }
            CollaborationToolsUIUtil.registerFocusActions(it)
        }

        val descriptionField = JBTextArea(descriptionDocument).apply {
            background = UIUtil.getListBackground()
            border = JBUI.Borders.empty(8, 8, 0, 8)
            emptyText.text = "Description (Markdown supported!)"
            lineWrap = true
        }.also {
            CollaborationToolsUIUtil.overrideUIDependentProperty(it) {
                font = UIUtil.getLabelFont()
            }
            CollaborationToolsUIUtil.registerFocusActions(it)
        }

        val descriptionPane = JBScrollPane(
            descriptionField,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        ).apply {
            isOpaque = false
            border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
        }

        val createButton = JButton(createAction)
        val cancelButton = JButton(cancelAction)
        val actionsPanel = JPanel(HorizontalLayout(JBUIScale.scale(8))).apply {
            add(createButton)
            add(cancelButton)
        }

        val publicBtn = JBRadioButton("Public").apply {
            isSelected = visiblility == SkadiGistSettings.Visiblility.Public
            addActionListener {
                visiblility = SkadiGistSettings.Visiblility.Public
                if (settings.rememberVisiblility) {
                    settings.visiblility = SkadiGistSettings.Visiblility.Public
                }
            }
            toolTipText = "Gist will be listed on the front page."
        }
        val internalBtn = JBRadioButton("Unlisted").apply {
            isSelected = visiblility == SkadiGistSettings.Visiblility.Internal
            addActionListener {
                visiblility = SkadiGistSettings.Visiblility.Internal
                if (settings.rememberVisiblility) {
                    settings.visiblility = SkadiGistSettings.Visiblility.Internal
                }
            }
            toolTipText = "Gist will be accessible for everyone with the link but it's not listed on the front page."
        }
        val privateBtn = JBRadioButton("Private").apply {
            isSelected = visiblility == SkadiGistSettings.Visiblility.Private
            addActionListener {
                visiblility = SkadiGistSettings.Visiblility.Private
                if (settings.rememberVisiblility) {
                    settings.visiblility = SkadiGistSettings.Visiblility.Private
                }
            }
            isEnabled = settings.isLoggedIn
            settings.registerLoginListener(this) {
                isEnabled = settings.isLoggedIn
            }
            toolTipText = "Gist will only be accessible to you. (requires login)"
        }
        val visibilityGroup = ButtonGroup().apply {
            add(publicBtn)
            add(internalBtn)
            add(privateBtn)
        }

        val visibilityLabel = JLabel("Visiblity")

        val visibilityPanel = JPanel(HorizontalLayout(JBUIScale.scale(8))).apply {
            border = JBUI.Borders.empty(8)
            add(visibilityLabel)
            add(publicBtn)
            add(internalBtn)
            add(privateBtn)
        }

        val loginWarning = createLoginWarning(settings)

        val nodesPanel = JPanel().apply {
            border = JBUI.Borders.empty(2)
            val titleLabel = JLabel("Creating gist for:")
            val nodeLabel = if (nodeNames.size == 1)
                JLabel(nodeNames.first())
            else
                JLabel("${nodeNames.size} Nodes").apply {
                    toolTipText = nodeNames.joinToString("<br>")
                }
            nodeLabel.foreground = JBColor.blue.brighter()
            add(titleLabel)
            add(nodeLabel)
        }

        val statusPanel = JPanel().apply {
            layout = MigLayout(LC().gridGap("0", "${JBUIScale.scale(8)}").insets("0").fill().flowY().hideMode(3))
            border = JBUI.Borders.empty(8)
            add(visibilityPanel, CC().minWidth("0"))
            add(loginWarning, CC().minWidth("0"))
            add(nodesPanel, CC().minWidth("0"))
            add(actionsPanel, CC().minWidth("0"))
        }

        val newContent = JPanel(null).apply {
            background = UIUtil.getListBackground()
            layout = MigLayout(LC().gridGap("0", "0").insets("0").fill().flowY())
            isFocusCycleRoot = true
            focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
                override fun getDefaultComponent(aContainer: Container?): Component {
                    return if (aContainer == this@apply) titleField
                    else super.getDefaultComponent(aContainer)
                }
            }

            add(titleField, CC().growX().pushX().minWidth("0"))
            add(descriptionPane, CC().grow().push().minWidth("0"))
            add(statusPanel, CC().growX().pushX())
        }
        setMainContent(newContent)
    }

    private fun removeMainContent() {
        showPlaceholder = true
        wrapper.removeAll()
        wrapper.revalidate()
        wrapper.repaint()
    }

    private fun setMainContent(newContent: JComponent) {
        showPlaceholder = false
        wrapper.removeAll()
        wrapper.layout = BorderLayout()
        wrapper.add(newContent, BorderLayout.CENTER)
        wrapper.revalidate()
        wrapper.repaint()
    }

    private fun createLoginWarning(settings: SkadiGistSettings): JComponent {
        val iconLabel = JLabel(AllIcons.Ide.Notification.WarningEvents)
        val textPane = LinkLabel.create("Without loging in you are not able to delete the gist!") {
            ShowSettingsUtilImpl.showSettingsDialog(null, "cloud.skadi.gist.mps.plugin.config.SkadiConfigurable", "")
        }

        val pane = JPanel(MigLayout(LC().insets("0").gridGap("0", "0"))).apply {
            add(iconLabel, CC().alignY("top").gapRight("${iconLabel.iconTextGap}"))
            add(textPane, CC().minWidth("0"))
        }
        pane.isVisible = !settings.isLoggedIn

        settings.registerLoginListener(this) {
            pane.isVisible = !settings.isLoggedIn
        }
        return pane
    }

    inner class CreateGistAction(
        val project: Project,
        val nodes: List<SNode>,
        val repo: SRepository,
        val getVisibility: () -> SkadiGistSettings.Visiblility,
    ) : AbstractAction("Create gist") {
        override fun actionPerformed(e: ActionEvent?) {
            object : Task.Backgroundable(project, "Create gist") {
                override fun run(indicator: ProgressIndicator) {
                    runBlocking {

                        val url = upload(
                            titleDocument.getText(0, titleDocument.length),
                            descriptionDocument.getText(0, descriptionDocument.length),
                            getVisibility().toModel(),
                            nodes,
                            repo,
                            settings.deviceToken,
                            settings
                        )
                        val notificationGroup =
                            NotificationGroupManager.getInstance().getNotificationGroup("Skadi Gist")
                        if (url != null) {
                            notificationGroup.createNotification(
                                "Gist created successfully",
                                "",
                                NotificationType.INFORMATION
                            ).addAction(object : NotificationAction("Open in Browser") {
                                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                                    BrowserUtil.browse(url)
                                    notification.expire()
                                }
                            }).addAction(object : NotificationAction("Copy URL") {
                                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                                    CopyPasteManagerEx.getInstanceEx().setContents(StringSelection(url))
                                    notification.expire()
                                }
                            }).notify(project)

                            titleDocument.remove(0, titleDocument.length)
                            descriptionDocument.remove(0, descriptionDocument.length)
                            removeMainContent()
                            window.hide()
                        } else {
                            notificationGroup.createNotification(
                                "Error creating gist",
                                "Can't create gist. You might not be connected to the internet.",
                                NotificationType.ERROR
                            ).notify(project)
                        }
                    }
                }
            }.queue()
        }
    }
}

private fun SkadiGistSettings.Visiblility.toModel() =
    when (this) {
        SkadiGistSettings.Visiblility.Private -> GistVisibility.Private
        SkadiGistSettings.Visiblility.Public -> GistVisibility.Public
        SkadiGistSettings.Visiblility.Internal -> GistVisibility.UnListed
    }


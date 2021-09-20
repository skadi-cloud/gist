package cloud.skadi.gist.mps.plugin

import com.intellij.util.ui.ImageUtil
import jetbrains.mps.editor.runtime.HeadlessEditorComponent
import jetbrains.mps.nodeEditor.cells.ParentSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.mps.openapi.module.SRepository
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private const val SCALE_FACTOR = 1.05

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun org.jetbrains.mps.openapi.model.SNode.asImage(repository: SRepository): ByteArrayOutputStream {
    return withContext(Dispatchers.Swing) {
        val headlessEditorComponent = HeadlessEditorComponent(repository)
        headlessEditorComponent.editNode(this@asImage)
        this@asImage.model!!.repository.modelAccess.runReadAction {
            headlessEditorComponent.updater.update()
        }
        headlessEditorComponent.size = headlessEditorComponent.preferredSize

        headlessEditorComponent.layoutRecursive()

        val rootCell = headlessEditorComponent.rootCell

        val w = (rootCell.x + rootCell.width) * SCALE_FACTOR
        val h = (rootCell.y + rootCell.height) * SCALE_FACTOR
        val image = ImageUtil.createImage(w.toInt(), h.toInt(), BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VBGR)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE)
        g.color = Color.WHITE
        g.fillRect(0, 0, w.toInt(), h.toInt())
        rootCell.paintCell(g, ParentSettings.createDefaultSetting())
        rootCell.paintDecorations(g)

        withContext(Dispatchers.IO) {
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(image, "png", outputStream)
            outputStream
        }
    }
}

private fun Component.layoutRecursive() {
    this.doLayout()

    if (this is Container) {
        this.components.forEach { it.layoutRecursive() }
    }
}
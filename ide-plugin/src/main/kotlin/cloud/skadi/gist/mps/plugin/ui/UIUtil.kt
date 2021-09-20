package cloud.skadi.gist.mps.plugin.ui


import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.KeyStroke

object CollaborationToolsUIUtil {

    /**
     * Adds actions to transfer focus by tab/shift-tab key for given [component].
     *
     * May be helpful for overwriting tab symbol input for text fields
     */
    fun registerFocusActions(component: JComponent) {
        component.registerKeyboardAction(
            { component.transferFocus() },
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
            JComponent.WHEN_FOCUSED
        )
        component.registerKeyboardAction(
            { component.transferFocusBackward() },
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
            JComponent.WHEN_FOCUSED
        )
    }

    /**
     * Add [listener] that will be invoked on each "UI" property change
     */
    fun <T : JComponent> overrideUIDependentProperty(component: T, listener: T.() -> Unit) {
        component.addPropertyChangeListener("UI", PropertyChangeListener {
            listener.invoke(component)
        })
        listener.invoke(component)
    }

}
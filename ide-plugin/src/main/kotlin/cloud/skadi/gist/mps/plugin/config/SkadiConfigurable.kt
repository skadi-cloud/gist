package cloud.skadi.gist.mps.plugin.config

import cloud.skadi.gist.mps.plugin.getLoginUrl
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.layout.*
import io.ktor.client.engine.java.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import java.net.URL
import javax.swing.JTextField

class SkadiConfigurable : BoundConfigurable("Skadi Gist") {

    private val settings = SkadiGistSettings.getInstance()
    private val listeners = mutableListOf<(Boolean) -> Unit>()

    override fun disposeUIResources() {
        super.disposeUIResources()
        settings.unregisterLoginListener(this)
    }

    override fun reset() {
        super.reset()
        settings.unregisterLoginListener(this)
        settings.registerLoginListener(this) {
            listeners.forEach { listener -> listener(it)
            reset()
            }
        }
    }

    private val ifLoginChanged: ComponentPredicate = object : ComponentPredicate() {
        override fun addListener(listener: (Boolean) -> Unit) {
            listeners.add(listener)
        }

        override fun invoke() = settings.isLoggedIn
    }

    override fun createPanel(): DialogPanel {

        return panel {
            row("Backend Address") {
                textField(settings::backendAddress).withValidationOnApply(validateBackend())
            }
            row("Default visibility") {
                buttonGroup(settings::visibility) {
                    row { radioButton("Public", SkadiGistSettings.Visibility.Public) }
                    row { radioButton("Internal", SkadiGistSettings.Visibility.Internal) }
                    row { radioButton("Private", SkadiGistSettings.Visibility.Private).enableIf(ifLoginChanged) }
                }
                checkBox("Remember visibility", settings::rememberVisibility)
            }
            row {

            }
            row("Logged in as") {
                textField(settings::loggedInUser).applyIfEnabled().visibleIf(ifLoginChanged).enabled(false)
                label("Not logged in").visibleIf(ifLoginChanged.not())
                browserLink("Login", getLoginUrl(settings)).visibleIf(ifLoginChanged.not()).withBinding(
                    {}, { link, _ ->
                        val urlField = BrowserLink::class.java.getDeclaredField("url")
                        urlField.isAccessible = true
                        urlField.set(link, getLoginUrl(settings))
                     },
                    PropertyBinding({}, {}))
                link("Log out") {
                    settings.logout()
                    reset()
                }.visibleIf(ifLoginChanged).withLargeLeftGap()
            }
        }
    }

    private fun validateBackend(): ValidationInfoBuilder.(JTextField) -> ValidationInfo? = {
        val checkUrl = checkUrl(it.text)
        if(checkUrl == null) error("Can not parse Url.")
        else if (checkUrl.protocol != "https") warning("Using a backend over HTTP is not recommended")
        else when (checkBackendConnection(checkUrl)) {
            null -> null
            is String -> error(it)
            else -> error("Unknown error")
        }
    }

    private fun checkUrl(url: String): URL? {
        return try {
            URL(url)
        } catch (e : Exception) {
            null
        }
    }

    private fun checkBackendConnection(url: URL): String? {
        val client = io.ktor.client.HttpClient(Java) {
            followRedirects = true
        }
        val helloEndpoint = URL(url, "/ide/hello")
        return runBlocking {
            try {
                val res = client.get<HttpResponse>(helloEndpoint) {
                    timeout {
                        connectTimeoutMillis = 10_000
                        requestTimeoutMillis = 10_000
                        socketTimeoutMillis = 10_000
                    }
                }
                return@runBlocking null
            } catch (e: Exception) {
                return@runBlocking "Error connecting to backend: ${e.message}"
            }
        }
    }
}
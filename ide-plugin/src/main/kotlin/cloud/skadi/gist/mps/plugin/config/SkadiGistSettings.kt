package cloud.skadi.gist.mps.plugin.config

import cloud.skadi.gist.mps.plugin.CreateGistFromNodeAction
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import io.ktor.util.*


@Service
@State(name = "SkadiGistSettings", storages = [Storage("skadi-settings.xml")], reportStatistic = false)
class SkadiGistSettings : PersistentStateComponentWithModificationTracker<SkadiGistSettings.State> {

    val logger = Logger.getInstance(SkadiGistSettings::class.java)
    enum class Visiblility {
        Public,
        Internal,
        Private
    }

    private val listeners = mutableMapOf<Any, (Boolean) -> Unit>()
    private var csfrToken: String? = null

    class State : BaseState() {
        var visiblity by enum(Visiblility.Public)
        var backendAddress by string(DEFAULT_BACKEND)
        var rememberVisiblility by property(true)
        var loggedInUser by string()
    }

    var visiblility
        get() = state.visiblity
        set(value) {
            state.visiblity = value
        }

    var backendAddress: String
        get() = state.backendAddress.orEmpty().ifEmpty { DEFAULT_BACKEND }
        set(value) {
            if(!value.endsWith("/"))
            {
                state.backendAddress = "$value/"
            } else {
                state.backendAddress = value
            }
        }

    var rememberVisiblility
        get() = state.rememberVisiblility
        set(value) {
            state.rememberVisiblility = value
        }

    var loggedInUser
        get() = state.loggedInUser ?: ""
        set(value) {
            state.loggedInUser = value
            listeners.forEach { it.value(isLoggedIn) }
        }

    val isLoggedIn
        get() = state.loggedInUser != null

    fun logout() {
        state.loggedInUser = null
        PasswordSafe.instance.set(createCredentialAttributes("device"), null)
        listeners.forEach { it.value(isLoggedIn) }
    }

    fun registerLoginListener(key: Any, listener: (Boolean) -> Unit) {
        listeners[key] = listener
    }

    fun unregisterLoginListener(key: Any) {
        listeners.remove(key)
    }

    private fun createCredentialAttributes(key: String) =
        CredentialAttributes(generateServiceName("Skadi Cloud Gist", key))


    var deviceToken
        get() = PasswordSafe.instance.get(createCredentialAttributes("device"))?.getPasswordAsString()
        set(value) {
            PasswordSafe.instance.set(createCredentialAttributes("device"), Credentials(loggedInUser, value))
        }


    private var state = State()

    override fun getState() = state

    override fun loadState(state: State) {
        this.state = state
    }

    override fun getStateModificationCount() = state.modificationCount


    fun newCsrfToken(): String {
        val nonce = generateNonce()
        csfrToken = nonce
        return nonce
    }

    fun checkCsrfToken(token: String): Boolean {
        if(token == csfrToken) {
            csfrToken = null
            return true
        }
        logger.warn("got invalid csrf token ($token)")
        return false
    }

    companion object {
        const val DEFAULT_BACKEND = "https://gist.skadi.cloud/"

        @JvmStatic
        fun getInstance() = ApplicationManager.getApplication().getService(SkadiGistSettings::class.java)
    }


}
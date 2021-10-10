package cloud.skadi.gist.plugins

import cloud.skadi.gist.data.User
import cloud.skadi.gist.data.createNewCSRFToken
import cloud.skadi.gist.data.userByEmail
import cloud.skadi.gist.getEnvOrDefault
import cloud.skadi.gist.sha256
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.html.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.kohsuke.github.GitHubBuilder
import java.time.LocalDateTime
import java.util.*

const val SALT_DEFAULT = "1234567890"
val COOKIE_SALT = getEnvOrDefault("COOKIE_SALT", SALT_DEFAULT)
val GITHUB_SECRET = getEnvOrDefault("GITHUB_SECRET", "")
val GITHUB_ID = getEnvOrDefault("GITHUB_ID", "")

const val REDIRECT_PARAM = "rd"

data class GistSession(val login: String, val email: String, val ghToken: String)

@Location("/login/{type?}")
class Login(val type: String = "")

val ApplicationCall.gistSession
    get() = sessions.get<GistSession>()

class SkadiNonceManager(val call: ApplicationCall) : NonceManager {
    private var nonce: String? = null

    @OptIn(InternalAPI::class)
    override suspend fun newNonce(): String {
        nonce = generateNonce()
        val redirectTarget = call.parameters[REDIRECT_PARAM]
        return if (redirectTarget != null) {
            "${nonce!!}:${
                redirectTarget.let {
                    Base64.getUrlEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
                }
            }"
        } else {
            nonce!!
        }
    }

    override suspend fun verifyNonce(nonce: String): Boolean {
        return true
    }
}


fun ApplicationCall.getRedirectTargetFromState(): String? {
    return this.parameters["state"]?.split(":")?.getOrNull(1)
        ?.let {
            String(Base64.getUrlDecoder().decode(it), charset = Charsets.UTF_8)
        }
}

suspend fun ApplicationCall.redirectToLoginAndBack() {
    val target = this.request.uri
    respondRedirect(URLBuilder.createFromCall(this).apply {
        takeFrom("/login/github")
        parameters[REDIRECT_PARAM] = target
    }.build().fullPath)
}

fun Application.configureOAuth(isProduction: Boolean) {

    val loginProviders = mapOf("github" to { call: ApplicationCall ->
        OAuthServerSettings.OAuth2ServerSettings(
            name = "github",
            authorizeUrl = "https://github.com/login/oauth/authorize",
            accessTokenUrl = "https://github.com/login/oauth/access_token",
            defaultScopes = listOf("user:email"),
            clientId = GITHUB_ID,
            clientSecret = GITHUB_SECRET,
            nonceManager = SkadiNonceManager(call)
        )
    }
    )
    authentication {
        oauth("gitHubOAuth") {
            client = HttpClient(Apache)
            providerLookup = { loginProviders[application.locations.resolve<Login>(Login::class, this).type]?.invoke(this) }
            urlProvider = { url(Login(it.name)) }
        }
    }
    install(Sessions) {
        cookie<GistSession>("GistSession") {
            val salt = COOKIE_SALT.sha256()
            transform(SessionTransportTransformerMessageAuthentication(salt))
            cookie.extensions["SameSite"] = "lax"
            cookie.secure = isProduction
            cookie.maxAgeInSeconds = 0
        }
    }

    routing {
        authenticate("gitHubOAuth") {
            location<Login>() {
                param("error") {
                    handle {
                        call.loginFailedPage(call.parameters.getAll("error").orEmpty())
                    }
                }

                handle {
                    val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
                    val accessToken = principal!!.accessToken
                    val github =
                        withContext(Dispatchers.IO) {
                            GitHubBuilder().withOAuthToken(accessToken).build()
                        }
                    val myself = github.myself


                    val email = myself.emails2.find { it.isPrimary && it.isVerified }?.email
                        ?: throw IllegalArgumentException("no email provided by Github!")

                    val name = myself.name ?: myself.login
                    val login = myself.login

                    val session = GistSession(
                        login = login,
                        email = email,
                        ghToken = accessToken
                    )

                    val user = userByEmail(email)

                    if (user == null) {
                        newSuspendedTransaction {
                            User.new {
                                this.email = email
                                this.name = name
                                this.login = login
                                this.regDate = LocalDateTime.now()
                                this.lastLogin = LocalDateTime.now()
                                this.avatarUrl = myself.avatarUrl
                            }
                        }
                    } else {
                        newSuspendedTransaction {
                            user.lastLogin = LocalDateTime.now()
                        }
                        user.createNewCSRFToken()
                    }

                    call.sessions.set(session)
                    val target = call.getRedirectTargetFromState() ?: "/"
                    call.loginSuccess(target)
                }
            }
        }
        // Perform logout by cleaning cookies
        get("/logout") {
            call.sessions.clear<GistSession>()
            call.respondRedirect("/")
        }
    }
}

private suspend fun ApplicationCall.loginFailedPage(errors: List<String>) {
    respondHtml {
        head {
            title { +"Login with" }
        }
        body {
            h1 {
                +"Login error"
            }

            for (e in errors) {
                p {
                    +e
                }
            }
        }
    }
}

private suspend fun ApplicationCall.loginSuccess(target: String) {
    respondHtml {
        head {
            title { +"Login Successful" }
            meta {
                httpEquiv = "refresh"
                content = "0; url=$target"
            }
        }
        body {
            h1 {
                +"Login Successful"
            }
        }
    }
}
package cloud.skadi.gist.routing

import cloud.skadi.gist.authenticated
import cloud.skadi.gist.data.Token
import cloud.skadi.gist.data.user
import cloud.skadi.gist.plugins.gistSession
import cloud.skadi.gist.shared.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

fun Application.configureIdeRoutes() {

    routing {
        route("/ide") {
            get("hello") {
                call.respond("Hello")
            }
            get("login") {
                call.authenticated { user ->
                    val callback = call.parameters[PARAMETER_CALLBACK]
                    val deviceName = call.parameters[PARAMETER_DEVICE_NAME]
                    val csrfToken = call.parameters[PARAMETER_CSRF_TOKEN]

                    if(callback == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@authenticated
                    }
                    if (deviceName == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@authenticated
                    }

                    if(csrfToken == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@authenticated
                    }
                    newSuspendedTransaction {
                        val token = Token.new {
                            name = deviceName
                            this.user = user
                            created = LocalDateTime.now()
                            token = generateNonce()
                        }
                        call.respondRedirect { takeFrom(callback)
                            parameters[PARAMETER_CSRF_TOKEN] = csrfToken
                            parameters[PARAMETER_DEVICE_TOKEN] = token.token
                            parameters[PARAMETER_USER_NAME] = user.login }
                    }

                }

            }
        }
    }

}
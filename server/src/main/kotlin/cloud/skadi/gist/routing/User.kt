package cloud.skadi.gist.routing

import cloud.skadi.gist.authenticated
import cloud.skadi.gist.data.allPublicGists
import cloud.skadi.gist.data.getUserByLogin
import cloud.skadi.gist.optionallyAthenticated
import cloud.skadi.gist.storage.StorageProvider
import cloud.skadi.gist.url
import cloud.skadi.gist.views.*
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.html.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.format.DateTimeFormatter

fun Application.configureUserRouting(store: StorageProvider) = routing {
    get("/user") {

    }
    get("/user/settings") {
        call.authenticated { user ->
            newSuspendedTransaction {
                call.respondHtmlTemplate(RootTemplate("Settings", user)) {
                    menu {
                        userMenu(call, user)
                    }
                    content {
                        h2 {
                            +"User Settings"
                        }
                        div {
                            p { +"Login: ${user.login}" }
                            p { +"Name: ${user.name}" }
                            p { +"Email: ${user.email}" }
                        }
                        div {
                            table {
                                thead {
                                    tr {
                                        th {
                                            +"Device"
                                        }
                                        th {
                                            +"Created"
                                        }
                                        th {
                                            +"Last Used"
                                        }
                                        th { }
                                    }
                                }
                                tbody {
                                    user.tokens.notForUpdate().filter { !it.isTemporary }.forEach {
                                        tr {
                                            td {
                                                +it.name
                                            }
                                            td {
                                                withRelativeDate(it.created)
                                                val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                                                +formatter.format(it.created)
                                            }
                                            td {
                                                val lastUsed = it.lastUsed
                                                if(lastUsed != null) {
                                                    withRelativeDate(lastUsed)
                                                    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                                                    +formatter.format(lastUsed)
                                                } else {
                                                    +"Never"
                                                }
                                            }
                                            td {
                                                form {
                                                    action = url { path("user", "settings", "token", "delete") }
                                                    method = FormMethod.post
                                                    hiddenInput {
                                                        name = "id"
                                                        value = it.id.value.toString()
                                                    }
                                                    withCSRFToken(user)
                                                    input {
                                                        type = InputType.submit
                                                        name = "Delete"
                                                        value = "Delete"
                                                    }
                                                }
                                            }
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
    }
    get("/user/{login}") {
        call.optionallyAthenticated { user ->
            val login = call.parameters["login"]
            if (login == null) {
                call.respond(HttpStatusCode.NotFound)
                return@optionallyAthenticated
            }

            val profile = getUserByLogin(login)

            if (profile == null) {
                call.respond(HttpStatusCode.NotFound)
                return@optionallyAthenticated
            }

            newSuspendedTransaction {
                call.respondHtmlTemplate(RootTemplate(login, user)) {
                    menu {
                        userMenu(call, user)
                    }
                    content {
                        allPublicGists(profile).notForUpdate().forEach { gist ->
                            gistSummary(gist, { store.getPreviewUrl(call, it) }, { call.url(it) }, user)
                        }
                    }
                }
            }
        }
    }
}
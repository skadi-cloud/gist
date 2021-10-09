package cloud.skadi.gist.routing

import cloud.skadi.gist.authenticated
import cloud.skadi.gist.data.allPublicGists
import cloud.skadi.gist.data.getUserByLogin
import cloud.skadi.gist.optionallyAthenticated
import cloud.skadi.gist.storage.StorageProvider
import cloud.skadi.gist.url
import cloud.skadi.gist.views.RootTemplate
import cloud.skadi.gist.views.gistSummary
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.html.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

fun Application.configureUserRouting(store: StorageProvider) = routing {
    get("/user") {

    }
    get("/user/settings") {
        call.authenticated { user ->
            newSuspendedTransaction {
                call.respondHtmlTemplate(RootTemplate("Settings", user)) {
                    content {
                        h2 {
                            +"User Settings"
                        }
                        div {
                            p { +"Login: ${user.login}" }
                            p { +"Name: ${user.name}" }
                            p { +"Email: ${user.email}" }
                        }
                        div{
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
                                    user.tokens.notForUpdate().forEach {
                                        tr {
                                            td {
                                                +it.name
                                            }
                                            td {
                                                +it.created.toString()
                                            }
                                            td {
                                                +it.lastUsed.toString()
                                            }
                                            td {

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
            if(login == null) {
                call.respond(HttpStatusCode.NotFound)
                return@optionallyAthenticated
            }

            val profile = getUserByLogin(login)

            if(profile == null) {
                call.respond(HttpStatusCode.NotFound)
                return@optionallyAthenticated
            }

            newSuspendedTransaction {
                call.respondHtmlTemplate(RootTemplate(login, user)) {
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
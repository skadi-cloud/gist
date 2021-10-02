package cloud.skadi.gist.routing

import cloud.skadi.gist.authenticated
import cloud.skadi.gist.views.RootTemplate
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.routing.*
import kotlinx.html.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

fun Application.configureUserRouting() = routing {
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

    }
}
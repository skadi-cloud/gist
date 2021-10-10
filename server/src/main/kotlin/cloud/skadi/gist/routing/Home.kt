package cloud.skadi.gist.routing

import cloud.skadi.gist.data.allPublicGists
import cloud.skadi.gist.optionallyAthenticated
import cloud.skadi.gist.storage.StorageProvider
import cloud.skadi.gist.turbo.StartPageTurboStream
import cloud.skadi.gist.turbo.TurboStreamMananger
import cloud.skadi.gist.url
import cloud.skadi.gist.views.RootTemplate
import cloud.skadi.gist.views.gistSummary
import cloud.skadi.gist.views.userMenu
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.html.a
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

@ExperimentalStdlibApi
fun Application.configureHomeRouting(tsm: TurboStreamMananger, store: StorageProvider) {
    routing {
        get("/") {
            call.optionallyAthenticated { user ->
                newSuspendedTransaction {
                    call.respondHtmlTemplate(RootTemplate("Home", user)) {
                        content {
                            allPublicGists().notForUpdate().forEach { gist ->
                                gistSummary(gist, { store.getPreviewUrl(call, it) }, { call.url(it) }, user)
                            }
                        }
                        menu {
                            userMenu(call, user)
                        }
                    }
                }
            }
        }
        webSocket("/") {
            call.optionallyAthenticated { user ->
                tsm.runWebSocket(this, StartPageTurboStream(user, { store.getPreviewUrl(call, it) }, this))
            }
        }

        get("/how-to") {
            call.respondText("no yet there")
        }
    }

}
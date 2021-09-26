package cloud.skadi.gist.routing

import cloud.skadi.gist.GistStorage
import cloud.skadi.gist.data.allPublicGists
import cloud.skadi.gist.optionallyAthenticated
import cloud.skadi.gist.turbo.StartPageTurboStream
import cloud.skadi.gist.turbo.TurboStreamMananger
import cloud.skadi.gist.url
import cloud.skadi.gist.views.RootTemplate
import cloud.skadi.gist.views.mainHtmlFragment
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.routing.*
import io.ktor.websocket.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

@ExperimentalStdlibApi
fun Application.configureHomeRouting(tsm: TurboStreamMananger, store: GistStorage) {
    routing {
        get("/") {
            call.optionallyAthenticated { user ->
                newSuspendedTransaction {
                    call.respondHtmlTemplate(RootTemplate("Home", user)) {
                        content {
                            allPublicGists().notForUpdate().forEach { gist ->
                                mainHtmlFragment(gist, { store.get(call, it) }, { call.url(it) }, user)
                            }
                        }
                    }
                }
            }
        }
        webSocket("/") {
            call.optionallyAthenticated { user ->
                tsm.runWebSocket(this, StartPageTurboStream(user, { store.get(call, it) }, this))
            }
        }
    }

}
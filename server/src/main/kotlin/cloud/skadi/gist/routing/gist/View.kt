package cloud.skadi.gist.routing.gist

import cloud.skadi.gist.acceptsTurbo
import cloud.skadi.gist.data.toOg
import cloud.skadi.gist.data.toTwitterCard
import cloud.skadi.gist.encodeBase62
import cloud.skadi.gist.shared.AST
import cloud.skadi.gist.shared.ImportGistMessage
import cloud.skadi.gist.storage.StorageProvider
import cloud.skadi.gist.turbo.GistUpdate
import cloud.skadi.gist.turbo.SingelGistTurboStream
import cloud.skadi.gist.turbo.TurboStreamMananger
import cloud.skadi.gist.turbo.turboStream
import cloud.skadi.gist.url
import cloud.skadi.gist.views.ToolTipSide
import cloud.skadi.gist.views.renderGistContent
import cloud.skadi.gist.views.templates.RootTemplate
import cloud.skadi.gist.views.userDetailsAndName
import cloud.skadi.gist.views.withToolTip
import cloud.skadi.gist.withUserReadableGist
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.html.*
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

@ExperimentalStdlibApi
fun Application.installGistViews(storage: StorageProvider, tsm: TurboStreamMananger) = routing {
    get("/gist/{id}") {
        call.withUserReadableGist { gist, user ->
            newSuspendedTransaction {
                val previewUrl = storage.getPreviewUrl(call, gist)
                call.respondHtmlTemplate(
                    RootTemplate(
                        "Skadi Gist",
                        call,
                        user = user,
                        twitterCard = gist.toTwitterCard(previewUrl),
                        og = gist.toOg(previewUrl, call.url(gist))
                    )
                ) {
                    aboveContainer {
                        userDetailsAndName(gist) { call.url(it) }
                        noScript {
                            p {
                                +("Seems like you have JavaScript disabled. We won't be able to import the Gist into MPS for you. " +
                                        "Please copy the URL of the Gist and use the Code -> Import Gist action in MPS.")
                            }
                        }

                        div {
                            button(classes = "tooltip button") {
                                classes = classes + "tooltip"
                                id = "copy-button"
                                attributes["data-controller"] = "gist-copy"
                                attributes["data-gist-copy-target"] = "button"
                                attributes["data-gist-copy-copied-class"] = "copied"
                                attributes["data-action"] = "click->gist-copy#doCopy"
                                span {
                                    attributes["data-gist-copy-target"] = "text"
                                    +"Copy Link"
                                }
                                withToolTip(ToolTipSide.Left) {
                                    p { +"After copying the link you can import the gist via Code -> Import Gist in MPS" }
                                }
                            }
                            if (user != null && gist.user?.id?.value == user.id.value) {
                                button(classes = "button") {
                                    a {
                                        href = call.url {
                                            path("gist", gist.id.value.encodeBase62(), "edit")
                                        }
                                        +"Edit"
                                    }
                                }
                            }
                        }

                    }
                    content {
                        turboStream(call.url { })
                        renderGistContent(gist, storage, call)
                    }
                }
            }
        }
    }

    webSocket("/gist/{id}") {
        call.withUserReadableGist { gist, _ ->
            tsm.runWebSocket(this, SingelGistTurboStream(gist, storage, this))
        }
    }

    get("/gist/{id}/nodes") {
        call.withUserReadableGist { gist, _ ->
            val jsonNodes = newSuspendedTransaction {
                gist.roots.notForUpdate().map { jacksonObjectMapper().readValue<AST>(it.node) }
            }
            call.respond(ImportGistMessage(gist.name, jsonNodes))
        }
    }

    post("/gist/{id}/toggle-like") {
        call.withUserReadableGist { gist, user ->
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@withUserReadableGist
            }
            newSuspendedTransaction {
                if (gist.likedBy.contains(user)) {
                    gist.likedBy = SizedCollection(gist.likedBy - user)
                } else {
                    gist.likedBy = SizedCollection(gist.likedBy + user)
                }
            }
            log.debug(call.request.acceptItems().joinToString())
            if (call.acceptsTurbo()) {
                // nothing to do, client will get the update via TSM.
                call.respond(HttpStatusCode.OK)
            } else {
                call.respondRedirect() {
                    path("gist", gist.id.value.toString())
                }
            }
            tsm.sendTurboChannelUpdate(gist.id.value.toString(), GistUpdate.Edited(gist))
        }
    }
}

package cloud.skadi.gist.routing

import cloud.skadi.gist.*
import cloud.skadi.gist.data.*
import cloud.skadi.gist.plugins.gistSession
import cloud.skadi.gist.shared.*
import cloud.skadi.gist.storage.DirectoryBasedStorage
import cloud.skadi.gist.storage.StorageProvider
import cloud.skadi.gist.turbo.*
import cloud.skadi.gist.views.CSSClasses
import cloud.skadi.gist.views.RootTemplate
import cloud.skadi.gist.views.gistRoot
import cloud.skadi.gist.views.mainDivId
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vladsch.flexmark.parser.ParserEmulationProfile
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.unsafe
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

@ExperimentalStdlibApi
fun Application.configureGistRoutes(
    tsm: TurboStreamMananger,
    storage: StorageProvider,
) {
    routing {
        post("/gist/create") {
            val token = call.request.header(HEADER_SKADI_TOKEN)

            val user = if (token != null)
                userByToken(token)
            else
                null

            if (token != null && user == null)
                log.warn("Can't find user by token ($token)")

            val (name, description, visibility, roots) = call.receive<GistCreationRequest>()

            if (roots.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                log.error("missing property of the gist name=$name, description=$description, visibility=$visibility, partsIsEmpty=${roots.isEmpty()}")
                return@post
            }

            if (user == null && visibility == GistVisibility.Private) {
                call.respond(HttpStatusCode.BadRequest)
                log.error("tried to create private gist without a user.")
                return@post
            }

            val gistAndRoots = newSuspendedTransaction {
                val gist = Gist.new {
                    this.description = description
                    this.name = name
                    if (visibility == null) {
                        this.visibility = GistVisibility.Public
                    } else {
                        this.visibility = visibility
                    }
                    this.created = LocalDateTime.now()
                    this.user = user
                }

                gist to roots.map {
                    GistRoot.new {
                        this.gist = gist
                        this.name = it.name
                        this.node = it.serialised.asJson()
                        this.isRoot = it.isRoot
                    } to it.base64Img.decodeBase64().inputStream()
                }
            }

            newSuspendedTransaction {
                gistAndRoots.second.forEach {
                    storage.storeRoot(it.first, it.second)
                }
            }

            val encodedId = gistAndRoots.first.id.value.encodeBase62()
            call.respondRedirect(call.url {
                path("gist", encodedId)
            })
            if (gistAndRoots.first.visibility == GistVisibility.Public) {
                newSuspendedTransaction {
                    tsm.sendTurboChannelUpdate(gistAndRoots.first.id.value, GistUpdate.Added(gistAndRoots.first))
                }
            }
        }
        get("/gist/{id}") {
            call.withUserReadableGist { gist, user ->
                newSuspendedTransaction {
                    call.respondHtmlTemplate(RootTemplate("Skadi Gist", user = user)) {
                        content {
                            h2(classes = CSSClasses.GistName.className) {
                                +gist.name
                            }
                            p(classes = CSSClasses.GistDescription.className) {
                                unsafe { +markdownToHtml(gist.description ?: "") }
                            }
                            gist.roots.notForUpdate().forEach { root ->
                                gistRoot({ storage.getUrls(call, it) }, root)
                            }
                        }
                    }
                }
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
                if(call.acceptsTurbo()) {
                    // nothing to do, client will get the update via TSM.
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respondRedirect("")
                }
                tsm.sendTurboChannelUpdate(gist.id.value.toString(), GistUpdate.Edited(gist))
            }
        }
    }
}


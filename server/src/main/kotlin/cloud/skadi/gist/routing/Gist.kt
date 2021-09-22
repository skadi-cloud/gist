package cloud.skadi.gist.routing

import cloud.skadi.gist.*
import cloud.skadi.gist.data.*
import cloud.skadi.gist.plugins.gistSession
import cloud.skadi.gist.shared.*
import cloud.skadi.gist.views.RootTemplate
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
import kotlinx.html.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.io.InputStream
import java.time.LocalDateTime

fun Application.configureGistRoutes(
    upload: suspend (GistRoot, InputStream) -> Unit,
    url: (ApplicationCall, GistRoot) -> UrlList
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
                    upload(it.first, it.second)
                }
            }

            val encodedId = gistAndRoots.first.id.value.encodeBase62()
            call.respondRedirect(call.url {
                path("gist", encodedId)
            })

        }
        get("/gist/{id}") {
            call.withUserReadableGist { gist, user ->
                newSuspendedTransaction {

                    ParserEmulationProfile.COMMONMARK
                    call.respondHtmlTemplate(RootTemplate("Skadi Gist", user = user)) {
                        content {
                            h2(classes = "gist-name") {
                                +gist.name
                            }
                            p(classes = "gist-description") {
                                unsafe { +markdownToHtml(gist.description ?: "") }
                            }
                            gist.roots.notForUpdate().forEach { root ->
                                div(classes = "root") {
                                    h3(classes = "root-name") {
                                        root.name
                                    }
                                    img(classes = "rendered") {
                                        src = url(call, root).mainUrl
                                    }

                                    div(classes = "comments") {
                                        root.comments.notForUpdate().forEach { comment ->
                                            div(classes = "comment") {
                                                p {
                                                    +comment.markdown
                                                }
                                            }
                                        }
                                        if (call.gistSession != null) {
                                            div(classes = "create-comment") {
                                                form {

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
        get("/gist/{id}/nodes") {
            call.withUserReadableGist { gist, _ ->
                val jsonNodes = newSuspendedTransaction {
                    gist.roots.notForUpdate().map { jacksonObjectMapper().readValue<AST>(it.node) }
                }
                call.respond(ImportGistMessage(gist.name, jsonNodes))
            }
        }
    }
}

suspend fun ApplicationCall.withUserReadableGist(block: suspend (Gist, User?) -> Unit) {
    val token = this.request.header(HEADER_SKADI_TOKEN)
    val session = this.gistSession

    val user = if (token != null)
        userByToken(token)
    else if (session != null)
        userByEmail(session.email)
    else
        null

    val idParam = this.parameters["id"]

    if (idParam == null) {
        this.respond(HttpStatusCode.BadRequest)
        return
    }
    val gistId = idParam.decodeBase62UUID()

    val gist = newSuspendedTransaction { Gist.findById(gistId) }
    if (gist == null) {
        this.application.log.warn("unknown gist: $gistId")
        this.respond(HttpStatusCode.NotFound)
        return
    }

    if (gist.visibility == GistVisibility.Private && gist.user != user) {
        this.application.log.warn("gist $gistId not visible for user")
        this.respond(HttpStatusCode.NotFound)
        return
    }
    block(gist, user)
}
package cloud.skadi.gist.routing.gist

import cloud.skadi.gist.encodeBase62
import cloud.skadi.gist.storage.StorageProvider
import cloud.skadi.gist.turbo.GistUpdate
import cloud.skadi.gist.turbo.TurboStreamMananger
import cloud.skadi.gist.url
import cloud.skadi.gist.views.CSSClasses
import cloud.skadi.gist.views.csrf.validateCSRFToken
import cloud.skadi.gist.views.csrf.withCSRFToken
import cloud.skadi.gist.views.templates.RootTemplate
import cloud.skadi.gist.views.userDetailsAndName
import cloud.skadi.gist.withUserOwnedGist
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.html.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

@ExperimentalStdlibApi
fun Application.installGistEdit(storage: StorageProvider, tsm: TurboStreamMananger) = routing {
    get("/gist/{id}/edit") {
        call.withUserOwnedGist { gist, user ->
            newSuspendedTransaction {
                val call = call
                call.respondHtmlTemplate(RootTemplate("Edit ${gist.name}", user)) {
                    aboveContainer {
                        userDetailsAndName(gist) { call.url(it) }
                    }
                    content {
                        form(classes = "edit-gist") {
                            method = FormMethod.post
                            attributes["data-controller"] = "markdown-editor"
                            withCSRFToken(user)
                            div("buttons") {
                                submitInput(classes = "button") {
                                    value = "Safe"
                                }

                                button(classes = "button") {
                                    a {
                                        href = "/gist/${gist.id.value.encodeBase62()}/delete"
                                        +"Delete Gist"
                                    }
                                }
                            }
                            div("row") {
                                label {
                                    htmlFor = "title"
                                    +"Title:"
                                }
                                textInput {
                                    name = "title"
                                    id = "title"
                                    value = gist.name
                                }
                            }
                            label {
                                htmlFor = "description"
                                +"Description:"
                            }
                            textArea {
                                attributes["data-markdown-editor-target"] = "edit"
                                name = "description"
                                id = "description"
                                +(gist.description ?: "")
                            }
                            div("roots") {
                                gist.roots.notForUpdate().forEach {
                                    div("delete-root") {
                                        label {
                                            htmlFor = "delete-${it.id.value}"
                                            +it.name
                                            checkBoxInput {
                                                id = "delete-${it.id.value}"
                                                name = "delete-${it.id.value}"
                                            }
                                            div("container") {
                                                i("fas fa-trash-alt") {
                                                }
                                                img(classes = "${CSSClasses.GistRoot.className} rendered") {
                                                    src = storage.getUrls(call, it).mainUrl
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
    post("/gist/{id}/edit") {
        call.withUserOwnedGist { gist, user ->
            val parameters = call.receiveParameters()
            if (!call.validateCSRFToken(user)) {
                call.respond(HttpStatusCode.BadRequest)
                return@withUserOwnedGist
            }
            val title = parameters["title"]
            val description = parameters["description"]

            newSuspendedTransaction {
                if (title != null)
                    gist.name = title

                if (description != null)
                    gist.description = description

                val rootIdsToDelete =
                    parameters.filter { key, _ -> key.startsWith("delete-") }.entries().mapNotNull { (key, values) ->
                        if (values.contains("on")) {
                            key.split("-").last().let { UUID.fromString(it) }
                        } else
                            null
                    }
                gist.roots.forUpdate().forEach { if (rootIdsToDelete.contains(it.id.value)) it.delete() }
                call.respondRedirect(call.url(gist))
                tsm.sendTurboChannelUpdate(gist.id.value.toString(), GistUpdate.Edited(gist))
            }
        }
    }

    get("/gist/{id}/delete") {
        call.withUserOwnedGist { gist, user ->
            newSuspendedTransaction {
                call.respondHtmlTemplate(RootTemplate("Edit ${gist.name}?", user)) {
                    aboveContainer {
                        userDetailsAndName(gist) { call.url(it) }
                    }
                    content {
                        h2 { +"Delete ${gist.name}?" }
                        form {
                            p {
                                +"Do you really want to permanently delete the gist: "
                                strong { +gist.name }
                                +"?"
                                br
                                br
                                strong {
                                    +"This action cannot be undone!"
                                }
                            }
                            withCSRFToken(user)
                            submitInput(classes = "button delete") {
                                formMethod = InputFormMethod.post
                                value = "Delete"
                            }
                        }
                    }
                }
            }
        }
    }

    post("/gist/{id}/delete") {
        call.withUserOwnedGist { gist, user ->
            if(!call.validateCSRFToken(user)) {
                call.respond(HttpStatusCode.BadRequest)
                return@withUserOwnedGist
            }
            transaction {
                gist.delete()
            }
            call.respondRedirect("/")
        }
    }
}
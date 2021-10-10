package cloud.skadi.gist.views

import cloud.skadi.gist.data.User
import cloud.skadi.gist.data.createNewCSRFToken
import cloud.skadi.gist.data.getCSRFToken
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.request.*
import io.ktor.util.*
import kotlinx.html.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class CSSClasses(val className: String) {
    GistDescription("gist-description"),
    GistName("gist-name"),
    GistRoot("root")
    ;
    override fun toString() = className
}

enum class ToolTipSide {
    Left, Right;
    fun toClass() : String = when(this) {
        Left -> "left"
        Right -> "right"
    }
}

fun FlowContent.withToolTip(side: ToolTipSide, block: DIV.() -> Unit) {
    div(side.toClass()) {
        block()
    }
}

private const val CSRFTokenInput = "CSRFToken"
fun FORM.withCSRFToken(user: User) {
    val csrfToken = user.getCSRFToken() ?: user.createNewCSRFToken()
    hiddenInput {
        name = CSRFTokenInput
        value = csrfToken
    }
}

suspend fun ApplicationCall.validateCSRFToken(user: User): Boolean {
    val parameters = this.receiveParameters()
    val tokenString = parameters[CSRFTokenInput]
    val usersToken = user.getCSRFToken()
    return tokenString == usersToken
}

fun FlowContent.withRelativeDate(date: LocalDateTime, contentFormatPattern: String? = null) {
    attributes["data-relative-date-date-value"] = "${date.toEpochSecond(ZoneOffset.UTC) * 1000}"
    if(contentFormatPattern != null) {
        attributes["data-relative-date-format-value"] = contentFormatPattern
    }
    attributes["data-controller"] = "relative-date"
    attributes["data-relative-date-target"] = "item"
}

fun FlowContent.userMenu(call: ApplicationCall, user: User?) {
    if (user == null) {
        a {
            href = call.url { path("login", "github") }
            +"Login"
        }
    } else {
        ul("user-actions") {
            li {
                a {
                    href = call.url {
                        path("user", "settings")
                    }
                    attributes["data-turbo"] = "false"
                    +"Settings"
                }
            }
            li {
                a {
                    href = call.url {
                        path("logout")
                    }
                    +"Log out"
                }
            }
        }

        div("user-self") {
            a {
                href = call.url { path("user", user.login) }
                img("user-avatar") {
                    src = user.avatarUrl ?: DEFAULT_USER_IMAGE
                }
            }
        }
    }
}

fun HEAD.favicons() {
    link {
        rel = "apple-touch-icon"
        sizes = "180x180"
        href = "/assets/apple-touch-icon.png"
    }

    link {
        rel = "icon"
        sizes = "32x32"
        href = "/assets/favicon-32x32.png"
    }

    link {
        rel = "icon"
        sizes = "16x16"
        href = "/assets/favicon-16x16.png"
    }

    link {
        rel = "ask-icon"
        attributes["color"] = "#00cc99"
        href = "/assets/safari-pinned-tab.svg"
    }

    meta {
        name = "msapplication-TileColor"
        attributes["color"] = "#00cc99"
    }

    meta {
        name = "theme-color"
        attributes["color"] = "#00cc99"
    }
}

class RootTemplate(private val pageName: String, private val user: User? = null) : Template<HTML> {
    val content = Placeholder<HtmlBlockTag>()
    val aboveContainer = Placeholder<HtmlBlockTag>()
    val menu = Placeholder<HtmlBlockTag>()
    override fun HTML.apply() {

        head {
            meta {
                name = "viewport"
                content = "width=device-width, initial-scale=1.0"
            }
            title { +pageName }
            styleLink("/assets/styles/styles.css")
            styleLink("/assets/font-awesome/css/all.css")
            favicons()
            script {
                src = "/assets/script.js"
            }
        }
        body {
            div() {
                id = "header"
                div {
                    id = "branding"
                    a {
                        href = "/"
                        img {
                            id = "header-image"
                            src = "/assets/icon-inverted.png"
                        }
                    }

                    div {
                        h1 { +"Skadi Cloud" }
                        h1(classes = "sub") { +"Gist" }
                    }
                    a {

                    }

                }
                div {
                    id = "menu"
                    insert(menu)
                }
            }
            div("above") {
                insert(aboveContainer)
            }

            div(classes = "container") {
                insert(content)
            }
        }
    }
}
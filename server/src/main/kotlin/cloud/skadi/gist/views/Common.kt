package cloud.skadi.gist.views

import cloud.skadi.gist.data.User
import io.ktor.html.*
import kotlinx.html.*

enum class CSSClasses(val className: String) {
    GistDescription("gist-description"),
    GistName("gist-name"),
    GistRoot("root")
    ;
    override fun toString() = className
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
    val menu = Placeholder<HtmlBlockTag>()
    override fun HTML.apply() {

        head {
            meta {
                name = "viewport"
                content = "width=device-width, initial-scale=1.0"
            }
            title { +pageName }
            styleLink("/assets/styles/styles.css")
            favicons()
        }
        body {
            div() {
                id = "header"
                div {
                    id = "branding"
                    img {
                        id = "header-image"
                        src = "/assets/icon-inverted.png"
                    }
                    div {
                        h1 { +"Skadi Cloud" }
                        h1(classes = "sub") { +"Gist" }
                    }
                }
                div {
                    id = "menu"
                    insert(menu)
                }
            }
            div(classes = "container") {
                insert(content)
            }
        }
    }
}
package cloud.skadi.gist.views.templates

import cloud.skadi.gist.data.User
import cloud.skadi.gist.views.userMenu
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import java.security.MessageDigest

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

enum class OpenGraphType {
    Article, Website;

    fun toMetaContent() = when (this) {
        Article -> "article"
        Website -> "website"
    }
}

data class OpenGraphData(
    val title: String,
    val type: OpenGraphType,
    val url: String,
    val image: String? = null,
    val description: String? = null,
    val siteName: String? = null,
    val imageAlt: String? = null
)

fun HEAD.addOpenGraph(og: OpenGraphData) {
    meta {
        attributes["property"] = "og:title"
        content = og.title
    }

    meta {
        attributes["property"] = "og:type"
        content = og.type.toMetaContent()
    }

    if (og.image != null) {
        meta {
            attributes["property"] = "og:image"
            content = og.image
        }
    }

    meta {
        attributes["property"] = "og:url"
        content = og.url
    }

    if (og.description != null) {
        meta {
            attributes["property"] = "og:description"
            content = og.description
        }
    }

    if (og.siteName != null) {
        meta {
            attributes["property"] = "og:site_name"
            content = og.siteName
        }
    }

    if (og.imageAlt != null) {
        meta {
            attributes["property"] = "og:image:alt"
            content = og.imageAlt
        }
    }
}

@Suppress("EnumEntryName")
enum class TwitterCardType {
    `Summary Card`, `Summary Card with Large Image`, `App Card`, `Player Card`;

    fun toMetaContent() = when (this) {
        `Summary Card` -> "summary"
        `Summary Card with Large Image` -> "summary_large_image"
        `App Card` -> "app"
        `Player Card` -> "player"
    }
}

data class TwitterCard(
    val card: TwitterCardType,
    val title: String,
    val site: String? = null,
    val creator: String? = null,
    val description: String? = null,
    val image: String? = null,
    val `image alt`: String? = null,
)

private fun HEAD.addTwitterCard(card: TwitterCard) {
    meta {
        name = "twitter:card"
        content = card.card.toMetaContent()
    }
    meta {
        name = "twitter:title"
        content = card.title
    }
    if (card.description != null) {
        meta {
            name = "twitter:description"
            content = card.description
        }
    }
    if (card.image != null) {
        meta {
            name = "twitter:image"
            content = card.image
        }
    }
    if (card.`image alt` != null) {
        meta {
            name = "twitter:image:alt"
            content = card.`image alt`
        }
    }
    if (card.site != null) {
        meta {
            name = "twitter:site"
            content = card.site
        }
    }
    if (card.creator != null) {
        meta {
            name = "twitter:creator"
            content = card.creator
        }
    }
}

private val hashCache = mutableMapOf<String, String>()

@InternalAPI
fun HEAD.styleLinkWithHash(styleResource: String, styleUrl: String) {
    val classLoader = ApplicationEngineEnvironment::class.java.classLoader
    val normalizedPath = (
            styleResource.split('/', '\\')
            ).normalizePathComponents().joinToString("/")

    val url = if (hashCache.containsKey(styleUrl)) {
        hashCache[styleUrl]
    } else {
        var pathWithHash: String? = null
        for (url in classLoader.getResources(normalizedPath).asSequence()) {
            val content = resourceClasspathResource(
                url,
                normalizedPath
            ) { ContentType.defaultForFileExtension(it) } as? OutgoingContent.ReadChannelContent
            if (content != null) {
                val bytes = ByteArray(content.contentLength!!.toInt())
                runBlocking {
                    content.readFrom().readFully(bytes, 0, content.contentLength!!.toInt())
                }
                val digest = MessageDigest.getInstance("MD5")
                val md5Hash = digest.digest(bytes)

                val dot = styleUrl.lastIndexOf(".")

                pathWithHash = if (dot != -1) {
                    styleUrl.substring(0 until dot) + "-" + hex(md5Hash) + styleUrl.substring(dot)
                } else {
                    styleUrl + "-" + hex(md5Hash)
                }
                break
            }
        }
        if (pathWithHash != null) {
            hashCache[styleUrl] = pathWithHash
        }
        pathWithHash
    }

    if (url == null) {
        throw Exception("Can't find resouce!")
    }
    styleLink(url)
}


@OptIn(InternalAPI::class)
class RootTemplate(
    private val pageName: String,
    private val call: ApplicationCall,
    private val user: User? = null,
    val twitterCard: TwitterCard? = null,
    val og: OpenGraphData? = null
) : Template<HTML> {
    val content = Placeholder<HtmlBlockTag>()
    val aboveContainer = Placeholder<HtmlBlockTag>()
    override fun HTML.apply() {

        head {
            meta {
                name = "viewport"
                content = "width=device-width, initial-scale=1.0"
            }
            title { +pageName }
            if (twitterCard != null) {
                addTwitterCard(twitterCard)
            }
            if (og != null) {
                addOpenGraph(og)
            }
            styleLinkWithHash("styles/styles.css","/assets/styles/styles.css")
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
                    userMenu(call, user)
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
package cloud.skadi.gist.views

import cloud.skadi.gist.data.Gist
import cloud.skadi.gist.data.GistRoot
import cloud.skadi.gist.data.User
import cloud.skadi.gist.data.isEditableBy
import cloud.skadi.gist.encodeBase62
import cloud.skadi.gist.markdownToHtml
import cloud.skadi.gist.storage.UrlList
import kotlinx.html.*
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun Gist.mainDivId() = "gist-${this.id.value.encodeBase62()}"
const val HTML_ID_GIST_CONTAINER = "gists"

fun FlowContent.editControlls(gist: Gist) = div {
    a {
        href = "/gist/${gist.id.value.encodeBase62()}/edit"
        +"edit"
    }
    a {
        href = "/gist/${gist.id.value.encodeBase62()}/delete"
        +"delete"
    }
}


fun FlowContent.likeable(gist: Gist, user: User) = div {
    form {
        method = FormMethod.post
        action = "/gist/${gist.id.value.encodeBase62()}/toggle-like"
        submitInput {
            if (gist.likedBy.contains(user)) {
                +"remove like"
            } else {
                +"like"
            }
        }
    }
}

const val DEFAULT_USER_IMAGE = "/assets/annon.jpg"
fun FlowContent.userDetailsAndName(gist: Gist, getUrl: (Gist) -> String) {
    val user = gist.user
    div("name-and-user") {
        img {
            src = user?.avatarUrl ?: DEFAULT_USER_IMAGE
        }
        div("profile-date") {
            span {
                if (user != null) {
                    a {
                        href = "/user/${user.login}"
                        +user.login
                    }
                } else {
                    p {
                        +"Annonymous"
                    }
                }
                +"/"
                a {
                    href = getUrl(gist)
                    +gist.name.ifEmpty { "No name" }
                }

            }
            span {
                val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                attributes["data-relative-date-date-value"] = "${gist.created.toEpochSecond(ZoneOffset.UTC) * 1000}"
                attributes["data-relative-date-format-value"] = "Created {0}"
                attributes["data-controller"] = "relative-date"
                attributes["data-relative-date-target"] = "item"
                +"Created ${gist.created}"
            }
        }

    }
}

fun FlowContent.gistMetadata(gist: Gist, user: User?) {
    div("facts") {
        ul {
            li(classes = "roots") {
                i(classes = "far fa-file-code") {  }
                +"${gist.roots.count()} roots"
            }
            /*
            li("comments") {
                +"${gist.comments.count()} comments"
            }
            li("stars") {
                if (user != null && gist.user != user) {
                    likeable(gist, user)
                }
                +"${gist.likedBy.count()} stars"
            }*/
        }
    }
}

fun FlowContent.gistSummary(
    gist: Gist,
    getPreviewUrl: (Gist) -> String,
    getUrl: (Gist) -> String,
    user: User?
) = div(classes = "gist snippet") {
    id = gist.mainDivId()
    div("meta") {
        userDetailsAndName(gist, getUrl)

        gistMetadata(gist, user)
        if (gist.isEditableBy(user)) {
            //editControlls(gist)
        }
    }
    div(classes = "summary") {
        if(!gist.description.isNullOrBlank()) {
            unsafe { +markdownToHtml(gist.description?.take(1024) ?: "") }
        } else {
            p{+""}
        }
    }
    val root = gist.roots.first()

    div(CSSClasses.GistRoot.className) {
        h3(classes = "name") {
            root.name
        }
        a {
            href = getUrl(gist)
            img(classes = "rendered") {
                src = getPreviewUrl(gist)
            }
        }
    }
}
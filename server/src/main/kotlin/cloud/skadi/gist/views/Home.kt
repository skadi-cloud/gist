package cloud.skadi.gist.views

import cloud.skadi.gist.data.Gist
import cloud.skadi.gist.data.GistRoot
import cloud.skadi.gist.data.User
import cloud.skadi.gist.data.isEditableBy
import cloud.skadi.gist.encodeBase62
import cloud.skadi.gist.markdownToHtml
import cloud.skadi.gist.storage.UrlList
import kotlinx.html.*

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

const val DEFAULT_USER_IMAGE = ""
fun FlowContent.userDetailsAndName(gist: Gist, getUrl: (Gist) -> String) {
    val user = gist.user
    div("name-and-user") {
        img {
            src = user?.avatarUrl ?: DEFAULT_USER_IMAGE
        }
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
    }
}

fun FlowContent.gistMetadata(gist: Gist, user: User?) {
    div("facts") {
        ul {
            li(classes = "roots") {
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
    p(classes = "summary") {
        unsafe { +markdownToHtml(gist.description?.take(1024) ?: "") }
    }
    val root = gist.roots.first()

    div(CSSClasses.GistRoot.className) {
        h3(classes = "name") {
            root.name
        }
        img(classes = "rendered") {
            src = getPreviewUrl(gist)
        }
    }
}
package cloud.skadi.gist.views

import cloud.skadi.gist.UrlList
import cloud.skadi.gist.data.Gist
import cloud.skadi.gist.data.GistRoot
import cloud.skadi.gist.data.User
import cloud.skadi.gist.data.isEditableBy
import cloud.skadi.gist.encodeBase62
import cloud.skadi.gist.markdownToHtml
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

fun FlowContent.mainHtmlFragment(gist: Gist, getScreenShotUrl: (GistRoot) -> UrlList, getUrl: (Gist) -> String, user: User?) = div(classes = "gist") {
    id = gist.mainDivId()
    if (gist.isEditableBy(user)) {
        editControlls(gist)
    }
    if (user != null && gist.user != user) {
        likeable(gist, user)
    }
    h2 {
        a {
            href = getUrl(gist)
            +gist.name
        }
    }
    p {
        unsafe { +markdownToHtml(gist.description ?: "") }
    }
    gistRoot(getScreenShotUrl, gist.roots.first())
    div {
        p("comment-count") { +gist.comments.count().toString() }
        p("likes") { +gist.likedBy.count().toString() }
    }
}
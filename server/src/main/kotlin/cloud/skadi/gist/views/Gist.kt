package cloud.skadi.gist.views

import cloud.skadi.gist.data.Gist
import cloud.skadi.gist.data.GistRoot
import cloud.skadi.gist.data.User
import cloud.skadi.gist.markdownToHtml
import cloud.skadi.gist.storage.StorageProvider
import cloud.skadi.gist.storage.UrlList
import io.ktor.application.*
import kotlinx.html.*

fun FlowContent.renderGistContent(
    gist: Gist,
    storage: StorageProvider,
    call: ApplicationCall
) {
    div {
        id = gist.mainDivId()
        div(classes = CSSClasses.GistDescription.className) {
            unsafe { +markdownToHtml(gist.description ?: "") }
        }
        gist.roots.notForUpdate().forEach { root ->
            gistRoot({ storage.getUrls(call, it) }, root)
        }
    }
}

fun FlowContent.gistComments(gist: Gist, user: User?) = div {
        div(classes = "comments") {
            gist.comments.notForUpdate().forEach { comment ->
                div(classes = "comment") {
                    p {
                        unsafe { +markdownToHtml(comment.markdown) }
                    }
                }
            }
             if (user != null) {
                div(classes = "create-comment") {
                    form {

                    }
                }
            }
        }

}
fun FlowContent.gistRoot(
    url: (GistRoot) -> UrlList,
    root: GistRoot
) {
    div(CSSClasses.GistRoot.className) {
        div(classes = "name") {
            b {+root.name}
        }

        img(classes = "rendered") {
            src = url(root).mainUrl
        }
    }
}
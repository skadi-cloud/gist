package cloud.skadi.gist.views

import cloud.skadi.gist.UrlList
import cloud.skadi.gist.data.Gist
import cloud.skadi.gist.data.GistRoot
import cloud.skadi.gist.data.User
import cloud.skadi.gist.markdownToHtml
import kotlinx.html.*


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
        h3(classes = "name") {
            root.name
        }
        img(classes = "rendered") {
            src = url(root).mainUrl
        }
    }
}
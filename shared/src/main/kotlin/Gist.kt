package cloud.skadi.gist.shared

enum class GistVisibility {
    Public, UnListed, Private
}

data class GistNode(val name: String, val base64Img: String, val serialised: AST, val isRoot: Boolean)

data class GistCreationRequest(
    val name: String,
    val description: String?,
    val visibility: GistVisibility?,
    val roots: List<GistNode>
)

data class ImportGistMessage(val roots: List<AST>)

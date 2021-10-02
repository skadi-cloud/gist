package cloud.skadi.gist.storage

import cloud.skadi.gist.data.Gist
import cloud.skadi.gist.data.GistRoot
import io.ktor.application.*
import java.io.InputStream

data class ScaledGist(val dimension: String, val url: String)
data class UrlList(val mainUrl: String, val scaled: List<ScaledGist>)

sealed interface StorageProvider {
    fun getUrls(call: ApplicationCall, root: GistRoot) : UrlList
    fun getPreviewUrl(call: ApplicationCall, gist: Gist): String
    suspend fun storeRoot(root: GistRoot, input: InputStream)
    suspend fun storePreview(gist: Gist, input: InputStream)
}
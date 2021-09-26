package cloud.skadi.gist

import cloud.skadi.gist.data.GistRoot
import io.ktor.application.*
import java.io.InputStream

typealias GistUrlProvider = (ApplicationCall, GistRoot) -> UrlList
typealias GistStore = suspend (GistRoot, InputStream) -> Unit

data class GistStorage(val get: GistUrlProvider, val set: GistStore)
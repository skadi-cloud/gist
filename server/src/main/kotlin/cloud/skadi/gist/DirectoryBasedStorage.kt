package cloud.skadi.gist

import cloud.skadi.gist.data.GistRoot
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream


data class ScaledGist(val dimension: String, val url: String)
data class UrlList(val mainUrl: String, val scaled: List<ScaledGist>)



class DirectoryBasedStorage(private val directory: File, private val prefix: String) {

    init {
        directory.mkdirs()
    }

    fun install(app: Application) {
        app.routing {
            get("$prefix/{gist}/{root}") {
                val gist = call.parameters["gist"]
                val root = call.parameters["root"]

                if(gist == null || root == null){
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }

                val imageFile = File(File(directory, gist.decodeBase62UUID().toString()), root)

                if(!imageFile.exists()) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respondFile(imageFile)
            }
        }
    }
    suspend fun put(root: GistRoot, data: InputStream) {
        val rootDirectory = File(directory, root.gist.id.toString())
        rootDirectory.mkdirs()
        val imageFile = File(rootDirectory, root.name + ".png")
        if(imageFile.exists()) {
            return
        }

        withContext(Dispatchers.IO) {
            data.copyTo(imageFile.outputStream())
        }
    }

    fun get(call: ApplicationCall, root: GistRoot): UrlList {
        val main = call.url {
            path(prefix,root.gist.id.value.encodeBase62(), root.name + ".png")
        }
        return UrlList(main, emptyList())
    }

}
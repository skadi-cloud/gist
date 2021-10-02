package cloud.skadi.gist.storage

import cloud.skadi.gist.data.Gist
import cloud.skadi.gist.data.GistRoot
import cloud.skadi.gist.decodeBase62UUID
import cloud.skadi.gist.encodeBase62
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

class DirectoryBasedStorage(private val directory: File, private val prefix: String): StorageProvider {

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

    override fun getUrls(call: ApplicationCall, root: GistRoot): UrlList {
        val main = call.url {
            path(prefix,root.gist.id.value.encodeBase62(), root.name + ".png")
        }
        return UrlList(main, emptyList())
    }

    override fun getPreviewUrl(call:ApplicationCall, gist: Gist): String {
        val main = call.url {
            path(prefix,gist.id.value.encodeBase62(), "preview.png")
        }
        return main
    }

    override suspend fun storeRoot(root: GistRoot, input: InputStream) {
        val rootDirectory = File(directory, root.gist.id.toString())
        rootDirectory.mkdirs()
        val imageFile = File(rootDirectory, root.name + ".png")
        if(imageFile.exists()) {
            return
        }

        withContext(Dispatchers.IO) {
            input.copyTo(imageFile.outputStream())
        }
    }

    override suspend fun storePreview(gist: Gist, input: InputStream) {
        val rootDirectory = File(directory, gist.id.toString())
        rootDirectory.mkdirs()
        val imageFile = File(rootDirectory,  "preview.png")
        if(imageFile.exists()) {
            return
        }

        withContext(Dispatchers.IO) {
            input.copyTo(imageFile.outputStream())
        }
    }

}
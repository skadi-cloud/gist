package cloud.skadi.gist.routing.gist

import cloud.skadi.gist.asJson
import cloud.skadi.gist.data.Gist
import cloud.skadi.gist.data.GistRoot
import cloud.skadi.gist.decodeBase64
import cloud.skadi.gist.encodeBase62
import cloud.skadi.gist.optionallyAthenticated
import cloud.skadi.gist.shared.GistCreationRequest
import cloud.skadi.gist.shared.GistVisibility
import cloud.skadi.gist.storage.StorageProvider
import cloud.skadi.gist.turbo.GistUpdate
import cloud.skadi.gist.turbo.TurboStreamMananger
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import javax.imageio.ImageIO

@ExperimentalStdlibApi
fun Application.installGistCreation(storage: StorageProvider, tsm: TurboStreamMananger) = routing {
    post("/gist/create") {
        call.optionallyAthenticated { user ->
            val (name, description, visibility, roots) = call.receive<GistCreationRequest>()

            if (roots.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                log.error("missing property of the gist name=$name, description=$description, visibility=$visibility, partsIsEmpty=${roots.isEmpty()}")
                return@optionallyAthenticated
            }

            if (user == null && visibility == GistVisibility.Private) {
                call.respond(HttpStatusCode.BadRequest)
                log.error("tried to create private gist without a user.")
                return@optionallyAthenticated
            }
            val gistAndRoots = newSuspendedTransaction {
                val gist = Gist.new {
                    this.description = description
                    this.name = name
                    if (visibility == null) {
                        this.visibility = GistVisibility.Public
                    } else {
                        this.visibility = visibility
                    }
                    this.created = LocalDateTime.now()
                    this.user = user
                }

                gist to roots.map {
                    GistRoot.new {
                        this.gist = gist
                        this.name = it.name
                        this.node = it.serialised.asJson()
                        this.isRoot = it.isRoot
                    } to it.base64Img.decodeBase64().inputStream()
                }
            }
            newSuspendedTransaction {
                gistAndRoots.second.forEach {
                    storage.storeRoot(it.first, it.second)
                }
                val firstRoot = gistAndRoots.second.firstOrNull() ?: return@newSuspendedTransaction
                val image = withContext(Dispatchers.IO) {
                    val inputStream = firstRoot.second
                    inputStream.reset()
                    ImageIO.read(inputStream)
                }
                val cropped = if (image.height > 250) {
                    image.getSubimage(0, 0, image.width, 250)
                } else {
                    image
                }

                val output = withContext(Dispatchers.IO) {
                    val outputStream = ByteArrayOutputStream()
                    ImageIO.write(cropped, "png", outputStream)
                    outputStream.toByteArray()
                }
                storage.storePreview(gistAndRoots.first, ByteArrayInputStream(output))
            }
            val encodedId = gistAndRoots.first.id.value.encodeBase62()
            call.respondRedirect(call.url {
                path("gist", encodedId)
            })
            if (gistAndRoots.first.visibility == GistVisibility.Public) {
                newSuspendedTransaction {
                    tsm.sendTurboChannelUpdate(gistAndRoots.first.id.value, GistUpdate.Added(gistAndRoots.first))
                }
            }
        }
    }
}
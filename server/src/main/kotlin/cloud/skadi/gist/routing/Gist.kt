package cloud.skadi.gist.routing

import cloud.skadi.gist.*
import cloud.skadi.gist.data.*
import cloud.skadi.gist.routing.gist.installGistCreation
import cloud.skadi.gist.routing.gist.installGistEdit
import cloud.skadi.gist.routing.gist.installGistViews
import cloud.skadi.gist.shared.*
import cloud.skadi.gist.storage.StorageProvider
import cloud.skadi.gist.turbo.GistUpdate
import cloud.skadi.gist.turbo.TurboStreamMananger
import cloud.skadi.gist.views.*
import cloud.skadi.gist.views.templates.RootTemplate
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.html.*
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.time.LocalDateTime
import javax.imageio.ImageIO

@ExperimentalStdlibApi
fun Application.configureGistRoutes(
    tsm: TurboStreamMananger,
    storage: StorageProvider,
) {
    installGistCreation(storage, tsm)
    installGistViews(storage, tsm)
    installGistEdit(storage, tsm)
}


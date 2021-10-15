package cloud.skadi.gist.turbo

import cloud.skadi.gist.data.Gist
import cloud.skadi.gist.storage.StorageProvider
import cloud.skadi.gist.views.mainDivId
import cloud.skadi.gist.views.renderGistContent
import io.ktor.websocket.*
import org.slf4j.LoggerFactory

class SingelGistTurboStream(
    private val gist: Gist,
    private val storage: StorageProvider,
    private val session: DefaultWebSocketServerSession
) : WebsocketTurboChannel<GistUpdate>(session.outgoing, LoggerFactory.getLogger(SingelGistTurboStream::class.java)) {
    override fun matchesKey(key: String): Boolean {
        return key == gist.id.value.toString()
    }

    override suspend fun sendUpdate(data: GistUpdate): SendResult {
        return when(data) {
            is GistUpdate.Edited -> send {
                    turboReplace(data.gist.mainDivId()) {
                        renderGistContent(gist, storage, session.call)
                    }
            }
            else -> return SendResult.NoOp
        }
    }
}
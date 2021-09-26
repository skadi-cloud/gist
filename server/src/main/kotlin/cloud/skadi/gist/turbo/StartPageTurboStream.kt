package cloud.skadi.gist.turbo

import cloud.skadi.gist.UrlList
import cloud.skadi.gist.data.Gist
import cloud.skadi.gist.data.GistRoot
import cloud.skadi.gist.data.User
import cloud.skadi.gist.url
import cloud.skadi.gist.views.HTML_ID_GIST_CONTAINER
import cloud.skadi.gist.views.mainDivId
import cloud.skadi.gist.views.mainHtmlFragment
import io.ktor.websocket.*
import org.slf4j.LoggerFactory

sealed class GistUpdate(val gist: Gist) {
    class Removed(gist: Gist) : GistUpdate(gist)
    class Added(gist: Gist) : GistUpdate(gist)
    class Edited(gist: Gist) : GistUpdate(gist)
}

class StartPageTurboStream(
    private val user: User?,
    private val urlGetter: (GistRoot) -> UrlList,
    private val session: DefaultWebSocketServerSession
) :
    WebsocketTurboChannel<GistUpdate>(session.outgoing, LoggerFactory.getLogger(StartPageTurboStream::class.java)) {
    override fun matchesKey(key: String): Boolean {
        return true
    }

    override suspend fun sendUpdate(data: GistUpdate): SendResult {
        val targetId = data.gist.mainDivId()
        return when (data) {
            is GistUpdate.Added -> send {
                turboPrepend(HTML_ID_GIST_CONTAINER) {
                    mainHtmlFragment(
                        data.gist,
                        urlGetter,
                        { session.call.url(it) },
                        user
                    )
                }
            }
            is GistUpdate.Removed -> send { turboRemove(targetId) }
            is GistUpdate.Edited -> send {
                turboReplace(targetId) {
                    mainHtmlFragment(
                        data.gist,
                        urlGetter,
                        { session.call.url(it) },
                        user
                    )
                }
            }
        }
    }
}
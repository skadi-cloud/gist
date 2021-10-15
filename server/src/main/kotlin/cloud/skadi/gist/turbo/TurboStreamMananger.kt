package cloud.skadi.gist.turbo

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.html.TagConsumer
import kotlinx.html.stream.createHTML
import kotlinx.html.visitAndFinalize
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KType
import kotlin.reflect.typeOf

enum class SendResult {
    Success, Error, Remove, NoOp
}

interface TurboChannel<T> {
    fun matchesKey(key: String): Boolean
    suspend fun sendUpdate(data: T): SendResult
}

abstract class WebsocketTurboChannel<T>(private val channel: SendChannel<Frame>, public val logger: Logger) :
    TurboChannel<T> {
    protected suspend fun send(frame: Frame): SendResult {
        return try {
            logger.info("sending update")
            channel.send(frame)
            logger.info("update send successfully")
            SendResult.Success
        } catch (e: CancellationException) {
            logger.warn("connection closed")
            SendResult.Remove
        } catch (t: Throwable) {
            logger.error("failed to send update", t)
            SendResult.Error
        }
    }
    protected suspend fun send(block: TagConsumer<String>.() -> Unit = {}): SendResult {
        val consumer = createHTML()
        consumer.block()
        return send(Frame.Text(consumer.finalize()))
    }
}

class TurboStreamMananger {
    val channels = ConcurrentHashMap<KType, MutableList<TurboChannel<*>>>()

    @ExperimentalStdlibApi
    inline fun <reified T> addTurboChannel(channel: TurboChannel<T>) {
        val l = channels.computeIfAbsent(typeOf<T>()) { _ -> Collections.synchronizedList(mutableListOf()) }
        l.add(channel)
    }

    @ExperimentalStdlibApi
    inline fun <reified T> removeTurboChannel(channel: TurboChannel<T>) {
        val l = channels[typeOf<T>()]
        l?.remove(channel)
    }

    @ExperimentalStdlibApi
    suspend inline fun <reified T> sendTurboChannelUpdate(key: Any, data: T) {
        val l = channels[typeOf<T>()]
        val toRemove: List<TurboChannel<T>>? = l?.mapNotNull {
            val channel = it as TurboChannel<T>
            if (channel.matchesKey(key.toString())) {
                val result = channel.sendUpdate(data)
                if (result == SendResult.Remove) {
                    return@mapNotNull channel
                }
            }
            return@mapNotNull null
        }
        toRemove?.forEach {
            removeTurboChannel(it)
        }
    }

    @ExperimentalStdlibApi
    suspend inline fun <reified T> runWebSocket(session: WebSocketSession, stream: WebsocketTurboChannel<T>) {
        addTurboChannel(stream)
        try {
            for (frame in session.incoming) {
                val text = (frame as Frame.Text).readText()
                stream.logger.info("client send $text")
            }
        } catch (e: ClosedReceiveChannelException) {
            removeTurboChannel(stream)
            stream.logger.info("connection closed")
        } catch (e: Throwable) {
            removeTurboChannel(stream)
            stream.logger.error("websocket error", e)
        }
    }

}
package cloud.skadi.gist.mps.plugin.http

import cloud.skadi.gist.mps.plugin.config.SkadiGistSettings
import cloud.skadi.gist.mps.plugin.getLoginUrl
import cloud.skadi.gist.shared.PARAMETER_CSRF_TOKEN
import cloud.skadi.gist.shared.PARAMETER_DEVICE_TOKEN
import cloud.skadi.gist.shared.PARAMETER_USER_NAME
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.util.io.origin
import com.intellij.util.io.referrer
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.handler.stream.ChunkedStream
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.apache.http.entity.ContentType
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.addCommonHeaders
import org.jetbrains.io.addKeepAliveIfNeeded
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.*

class HttpHandler : HttpRequestHandler() {
    override fun isSupported(request: FullHttpRequest): Boolean {
        return request.method() == HttpMethod.GET && (request.uri().startsWith("/skadi-gist/"))
    }

    override fun process(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): Boolean {
        return when {
            urlDecoder.path().endsWith("/login-response") ->
                handleLogin(urlDecoder, request, context)
            else -> false
        }
    }

    private fun handleLogin(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): Boolean {
        val parameters = urlDecoder.parameters()
        val token =
            parameters[PARAMETER_DEVICE_TOKEN]?.firstOrNull() ?: return respondWithError("missing token", request, context)
        val user = parameters[PARAMETER_USER_NAME]?.firstOrNull() ?: return respondWithError("missing user", request, context)
        val csrfToken = parameters[PARAMETER_CSRF_TOKEN]?.firstOrNull() ?: return respondWithError(
            "missing csrf token",
            request,
            context
        )

        val settings = SkadiGistSettings.getInstance()

        if(!settings.checkCsrfToken(csrfToken))
            return respondWithError("invalid csrf token", request, context)


        if (settings.isLoggedIn) {
            val notificationGroup =
                NotificationGroupManager.getInstance().getNotificationGroup("Skadi Gist")
            val logoutAction = object : NotificationAction("Log out") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    settings.logout()
                    BrowserUtil.browse(getLoginUrl(settings))
                }
            }
            notificationGroup.createNotification(
                "Login error",
                "You are already logged in. Please log out first!",
                NotificationType.ERROR
            ).addAction(logoutAction).notify(null)
            return respondWithError("Already logged in.", request, context)
        }

        settings.loggedInUser = user
        settings.deviceToken = token

        respond(request, context) {
            head { title = "Skadi Cloud Gist" }
            body {
                h1 { +"Login Successful" }
                h2 { +"You successfully logged in as $user. You can close this website now." }
            }
        }

        return true
    }

    private fun respondWithError(
        msg: String, request: FullHttpRequest,
        context: ChannelHandlerContext
    ): Boolean {
        respond(request, context) {
            head { title = "Skadi Cloud Gist - Error" }
            body {
                h1 { +"Error" }
                h2 { +msg }
                div {

                }
            }
        }
        return true
    }

    private fun respond(request: FullHttpRequest, context: ChannelHandlerContext, block : HTML.() -> Unit) {
        val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_HTML)
        response.addCommonHeaders()
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, must-revalidate") //NON-NLS
        response.headers().set(HttpHeaderNames.LAST_MODIFIED, Date(Calendar.getInstance().timeInMillis))

        val content = createHTML().html(block = block).toByteArray()

        if (request.method() != HttpMethod.HEAD) {
            HttpUtil.setContentLength(response, content.size.toLong())
        }

        val channel = context.channel()
        channel.write(response)

        if (request.method() != HttpMethod.HEAD) {
            val stream = ByteArrayInputStream(content)
            channel.write(ChunkedStream(stream))
            stream.close()
        }
        val keepAlive = response.addKeepAliveIfNeeded(request)

        val future = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE)
        }
    }
}
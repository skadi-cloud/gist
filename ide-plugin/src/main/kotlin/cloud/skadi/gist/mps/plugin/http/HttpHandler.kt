package cloud.skadi.gist.mps.plugin.http

import cloud.skadi.gist.mps.plugin.config.SkadiGistSettings
import cloud.skadi.gist.mps.plugin.getLoginUrl
import cloud.skadi.gist.mps.plugin.toSNode
import cloud.skadi.gist.shared.*
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ex.ProjectManagerEx
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.stream.ChunkedStream
import jetbrains.mps.ide.datatransfer.CopyPasteUtil
import jetbrains.mps.smodel.ModelAccess
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.apache.http.entity.ContentType
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.addCommonHeaders
import org.jetbrains.io.addKeepAliveIfNeeded
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.URL
import java.util.*

class HttpHandler : HttpRequestHandler() {
    private val client = io.ktor.client.HttpClient(Java) {
        followRedirects = true

    }
    private val mapper = JsonMapper.builder()
        .addModule(KotlinModule())
        .build()

    override fun isSupported(request: FullHttpRequest): Boolean {
        return (request.method() == HttpMethod.GET || request.method() == HttpMethod.POST) && (request.uri()
            .startsWith("/skadi-gist/"))
    }

    override fun process(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): Boolean {
        return when {
            request.method() == HttpMethod.GET && urlDecoder.path().endsWith("/login-response") ->
                handleLogin(urlDecoder, request, context)
            request.method() == HttpMethod.POST && urlDecoder.path().endsWith("/import-gist") ->
                importGist(urlDecoder.parameters()["gist"]!!.first(), request, context)
            else -> false
        }
    }

    private fun importGist(gist: String, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
        val settings = SkadiGistSettings.getInstance()

        val toImport =
            runBlocking {
                val response = client.get<HttpResponse>(URL("${settings.backendAddress}/gist/$gist/nodes")) {
                    accept(io.ktor.http.ContentType.Application.Json)
                }
                mapper.readValue<ImportGistMessage>(response.content.toInputStream())
            }

        if (toImport.roots.size == 1 && !toImport.roots.first().isRootNode) {

            ModelAccess.instance().runReadAction {
                CopyPasteUtil.copyNodeToClipboard(toImport.roots.first().root.toSNode(null))
            }

            ProjectManagerEx.getInstanceExIfCreated()?.openProjects?.filter { !it.isDisposed }?.forEach { project ->
                val notificationGroup =
                    NotificationGroupManager.getInstance().getNotificationGroup("Skadi Gist")
                notificationGroup.createNotification(
                    "Gist copied",
                    "Gist '${toImport.name}' was copied to the clipboard."
                ).notify(project)
            }
        } else {
            ProjectManagerEx.getInstanceExIfCreated()?.openProjects?.filter { !it.isDisposed }?.forEach { project ->
                val notificationGroup =
                    NotificationGroupManager.getInstance().getNotificationGroup("Skadi Gist")
                notificationGroup.createNotification(
                    "Import gist?",
                    "Gist '${toImport.name}' contains root node. Do you want to import the gist?"
                )
                    .addAction(DoImportAction(toImport)).notify(project)
            }
        }


        val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_HTML)
        response.addCommonHeaders()
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, must-revalidate") //NON-NLS
        response.headers().set(HttpHeaderNames.LAST_MODIFIED, Date(Calendar.getInstance().timeInMillis))

        val channel = context.channel()
        channel.write(response)
        val keepAlive = response.addKeepAliveIfNeeded(request)
        val future = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE)
        }

        return true
    }

    private fun handleLogin(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): Boolean {
        val parameters = urlDecoder.parameters()
        val temporaryToken =
            parameters[PARAMETER_TEMPORARY_TOKEN]?.firstOrNull() ?: return respondWithError(
                "missing token",
                request,
                context
            )
        val user =
            parameters[PARAMETER_USER_NAME]?.firstOrNull() ?: return respondWithError("missing user", request, context)
        val csrfToken = parameters[PARAMETER_CSRF_TOKEN]?.firstOrNull() ?: return respondWithError(
            "missing csrf token",
            request,
            context
        )

        val settings = SkadiGistSettings.getInstance()

        if (!settings.checkCsrfToken(csrfToken))
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

        try {
            val token = runBlocking {
                val response = client.submitForm<HttpResponse>(url = "${settings.backendAddress}ide/redeem-token",
                 formParameters = Parameters.build {
                     append(PARAMETER_DEVICE_TOKEN, temporaryToken)
                     append(PARAMETER_USER_NAME, user)
                     append(PARAMETER_DEVICE_NAME, InetAddress.getLocalHost().hostName)
                 })
                response.readText()
            }

            settings.loggedInUser = user
            settings.deviceToken = token
        } catch (e: Exception) {
            return respondWithError("Error redeeming token: ${e.message}", request, context)
        }

        respond(request, context, success = true) {
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

    private fun respond(
        request: FullHttpRequest,
        context: ChannelHandlerContext,
        success: Boolean = false,
        block: HTML.() -> Unit
    ) {
        val response = DefaultHttpResponse(
            HttpVersion.HTTP_1_1,
            if (success) HttpResponseStatus.OK else HttpResponseStatus.BAD_REQUEST
        )
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
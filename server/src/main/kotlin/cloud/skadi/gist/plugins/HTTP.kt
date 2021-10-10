package cloud.skadi.gist.plugins

import io.ktor.features.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.application.*
import io.ktor.jackson.*

fun Application.configureHTTP() {
    install(CachingHeaders) {
        options { outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                ContentType.Text.CSS -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                else -> null
            }
        }
    }
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }
    install(ForwardedHeaderSupport) // WARNING: for security, do not include this if not behind a reverse proxy
    install(XForwardedHeaderSupport) {
        // Treafik, the ingress used for the production deployment uses HttpHeaders.XForwardedServer to send the infromation
        // about the container it is forwarding for. This header is last in the list of host headers and will override
        // the correct information stored in XForwardedHost.
        this.hostHeaders.clear()
        this.hostHeaders.add(HttpHeaders.XForwardedHost)
    }
    install(ContentNegotiation) {
        jackson()
    }
    install(DoubleReceive)
}

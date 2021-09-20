package cloud.skadi.gist.plugins

import io.ktor.locations.*
import io.ktor.features.*
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.routing.*

fun Application.configureRouting() {
    install(Locations) {
    }
    install(AutoHeadResponse)

    routing {
        static("assets") {
            static("webfonts") {
                resources("webfonts")
            }
            static("styles") {
                resources("styles")
            }
            static("js") {
                resources("js")
            }
            resources("static")
        }
    }

}

package cloud.skadi.gist.plugins

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import java.io.File

fun Application.configureRouting() {
    install(Locations) {
    }
    install(AutoHeadResponse)

    routing {
        static("assets") {
            static("font-awesome") {
                resources("font-awesome")
            }
            static("webfonts") {
                resources("webfonts")
            }
            static("styles") {
                resourcesWithHash("styles")
            }
            static("js") {
                resources("js")
            }
            resources("static")
        }
    }

}

const val pathParameterName = "static-content-path-parameter"
public fun Route.resourcesWithHash(resourcePackage: String? = null) {
    val packageName = staticBasePackage.combinePackage(resourcePackage)
    get("{$pathParameterName...}") {
        val allParams = call.parameters.getAll(pathParameterName) ?: return@get

        val file = allParams.last()

        if (!file.contains("-")) {
            return@get
        }

        val dash = file.lastIndexOf("-")
        val dot = file.lastIndexOf(".")

        val name = file.substring(0 until dash)
        val realFile = if (dot != -1) {
            val ending = file.substring(dot until file.length)
            name + ending
        } else {
            name
        }

        val relativePath = (allParams.take(allParams.size - 1) + listOf(realFile)).joinToString(File.separator)

        val content = call.resolveResource(relativePath, packageName)
        if (content != null) {
            call.respond(content)
        }
    }
}

private fun String?.combinePackage(resourcePackage: String?) = when {
    this == null -> resourcePackage
    resourcePackage == null -> this
    else -> "$this.$resourcePackage"
}

package cloud.skadi.gist.turbo

import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.response.*
import kotlinx.html.*
import kotlinx.html.attributes.Attribute
import kotlinx.html.attributes.StringAttribute
import kotlinx.html.stream.createHTML

fun FlowContent.turboStream(url: String) {
    div {
        attributes["data-controller"] = "turbo-stream"
        attributes["data-turbo-stream-url-value"] = url
    }
}

class TurboFrame(consumer: TagConsumer<*>) :
    HTMLTag(
        "turbo-frame", consumer, emptyMap(),
        inlineTag = false,
        emptyTag = false
    ), HtmlBlockTag

fun HTMLTag.turboFrame(block: TurboFrame.() -> Unit = {}) {
    TurboFrame(consumer).visit(block)
}


class TurboStream(consumer: TagConsumer<*>) :
    HTMLTag(
        "turbo-stream", consumer, emptyMap(),
        inlineTag = false,
        emptyTag = false
    ), HtmlBlockTag {
    private val attributeStringString: Attribute<String> = StringAttribute()

    var action: String
        get() = attributeStringString[this, "action"]
        set(newValue) {
            attributeStringString[this, "action"] = newValue
        }
    var target: String
        get() = attributeStringString[this, "target"]
        set(newValue) {
            attributeStringString[this, "target"] = newValue
        }
}

fun <T> TagConsumer<T>.turboStream(block: TurboStream.() -> Unit = {}): T {
    return TurboStream(this).visitAndFinalize(this, block)
}

class TurboTemplate(consumer: TagConsumer<*>) :
    HTMLTag(
        "template", consumer, emptyMap(),
        inlineTag = false,
        emptyTag = false
    ), HtmlBlockTag

fun HTMLTag.template(block: TurboTemplate.() -> Unit = {}) {
    TurboTemplate(consumer).visit(block)
}

fun <T> TagConsumer<T>.turboAppend(targetId: String, block: TurboTemplate.() -> Unit = {}): T = turboStream {
    action = "append"
    target = targetId
    template {
        block()
    }
}

fun <T> TagConsumer<T>.turboPrepend(targetId: String, block: TurboTemplate.() -> Unit = {}): T = turboStream {
    action = "prepend"
    target = targetId
    template {
        block()
    }
}

fun <T> TagConsumer<T>.turboReplace(targetId: String, block: TurboTemplate.() -> Unit = {}): T = turboStream {
    action = "replace"
    target = targetId
    template {
        block()
    }
}

fun <T> TagConsumer<T>.turboUpdate(targetId: String, block: TurboTemplate.() -> Unit = {}): T = turboStream {
    action = "replace"
    target = targetId
    template {
        block()
    }
}

fun <T> TagConsumer<T>.turboRemove(targetId: String): T = turboStream {
    action = "remove"
    target = targetId
}

fun <T> TagConsumer<T>.turboBefore(targetId: String, block: TurboTemplate.() -> Unit = {}): T = turboStream {
    action = "before"
    target = targetId
    template {
        block()
    }
}

fun <T> TagConsumer<T>.turboAfter(targetId: String, block: TurboTemplate.() -> Unit = {}): T = turboStream {
    action = "after"
    target = targetId
    template {
        block()
    }
}

private val turboContentType = ContentType("text", "vnd.turbo-stream.html")
suspend fun ApplicationCall.respondTurbo(block: TagConsumer<String>.() -> String) {
    val createHTML = createHTML()
    this.respondText(contentType = turboContentType) {
        createHTML.block()
    }
}

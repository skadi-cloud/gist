package cloud.skadi.gist.views

import cloud.skadi.gist.data.User
import io.ktor.application.*
import io.ktor.util.*
import kotlinx.html.*
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class CSSClasses(val className: String) {
    GistDescription("gist-description"),
    GistName("gist-name"),
    GistRoot("root")
    ;
    override fun toString() = className
}

enum class ToolTipSide {
    Left, Right;
    fun toClass() : String = when(this) {
        Left -> "left"
        Right -> "right"
    }
}

fun FlowContent.withToolTip(side: ToolTipSide, block: DIV.() -> Unit) {
    div(side.toClass()) {
        block()
    }
}

fun FlowContent.withRelativeDate(date: LocalDateTime, contentFormatPattern: String? = null) {
    attributes["data-relative-date-date-value"] = "${date.toEpochSecond(ZoneOffset.UTC) * 1000}"
    if(contentFormatPattern != null) {
        attributes["data-relative-date-format-value"] = contentFormatPattern
    }
    attributes["data-controller"] = "relative-date"
    attributes["data-relative-date-target"] = "item"
}

fun FlowContent.userMenu(call: ApplicationCall, user: User?) {
    if (user == null) {
        a {
            href = call.url { path("login", "github") }
            +"Login"
        }
    } else {
        ul("user-actions") {
            li {
                a {
                    href = call.url {
                        path("user", "settings")
                    }
                    attributes["data-turbo"] = "false"
                    +"Settings"
                }
            }
            li {
                a {
                    href = call.url {
                        path("logout")
                    }
                    +"Log out"
                }
            }
        }

        div("user-self") {
            a {
                href = call.url { path("user", user.login) }
                img("user-avatar") {
                    src = user.avatarUrl ?: DEFAULT_USER_IMAGE
                }
            }
        }
    }
}




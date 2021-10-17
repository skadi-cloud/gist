package cloud.skadi.gist

import cloud.skadi.gist.data.*
import cloud.skadi.gist.plugins.gistSession
import cloud.skadi.gist.plugins.redirectToLoginAndBack
import cloud.skadi.gist.shared.GistVisibility
import cloud.skadi.gist.shared.HEADER_SKADI_TOKEN
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.seruco.encoding.base62.Base62
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.*


private val base62 = Base62.createInstance()!!
private val mapper = JsonMapper.builder()
    .addModule(KotlinModule(strictNullChecks = true))
    .build()

fun Any.asJson() = mapper.writeValueAsString(this)!!
fun String.decodeBase62UUID(): UUID {
    val decoded = base62.decode(this.toByteArray())
    val bytes = ByteBuffer.allocate(decoded.size).put(decoded).rewind()
    return UUID(bytes.long, bytes.long)
}

fun UUID.encodeBase62() = base62.encode(
    ByteBuffer.allocate(16).putLong(this.mostSignificantBits)
        .putLong(this.leastSignificantBits).array()
).decodeToString()

fun String.decodeBase64() = Base64.getMimeDecoder().decode(this)!!

/**
 * Enforces the user to be authenticated, if no user is loggin the user is first send to login.
 *
 * The loged in user then passed to the consumer. Authentication via cookie or header is supported.
 */
suspend fun ApplicationCall.authenticated(body: suspend (User) -> Unit) {
    val token = this.request.header(HEADER_SKADI_TOKEN)

    val user = if (token != null)
        userByToken(token)
    else gistSession?.user()

    if(gistSession != null && user == null){
        this.application.log.error("Valid session but user (${gistSession?.email}) not found!")
    }

    if (user == null) {
        this.redirectToLoginAndBack()
        return
    }
    body(user)
}

/**
 *  Will retrieve the user that is currently logged in and pass it to the consumer.
 *
 *  Of no user is logged in the user is null.
 */
suspend fun ApplicationCall.optionallyAthenticated(body: suspend (User?) -> Unit) {
    val token = this.request.header(HEADER_SKADI_TOKEN)

    val user = if (token != null)
        userByToken(token)
    else gistSession?.user()

    if(gistSession != null && user == null){
        this.application.log.error("Valid session but user (${gistSession?.email}) not found!")
    }
    body(user)
}

suspend fun ApplicationCall.withUserReadableGist(block: suspend (Gist, User?) -> Unit) {
    val token = this.request.header(HEADER_SKADI_TOKEN)
    val session = this.gistSession

    val user = if (token != null)
        userByToken(token)
    else if (session != null)
        userByEmail(session.email)
    else
        null

    val idParam = this.parameters["id"]

    if (idParam == null) {
        this.respond(HttpStatusCode.BadRequest)
        return
    }
    val gistId = idParam.decodeBase62UUID()

    val gist = newSuspendedTransaction { Gist.findById(gistId) }
    if (gist == null) {
        this.application.log.warn("unknown gist: $gistId")
        this.respond(HttpStatusCode.NotFound)
        return
    }

    if (gist.visibility == GistVisibility.Private && gist.user != user) {
        this.application.log.warn("gist $gistId not visible for user")
        this.respond(HttpStatusCode.NotFound)
        return
    }
    block(gist, user)
}

suspend fun ApplicationCall.withUserOwnedGist(block: suspend (Gist, User) -> Unit) {
    authenticated { user ->
        val idParam = this.parameters["id"]

        if (idParam == null) {
            this.respond(HttpStatusCode.BadRequest)
            return@authenticated
        }
        val gistId = idParam.decodeBase62UUID()

        val gist = newSuspendedTransaction { Gist.findById(gistId) }
        if (gist == null) {
            this.application.log.warn("unknown gist: $gistId")
            this.respond(HttpStatusCode.NotFound)
            return@authenticated
        }

        val call = this

        val isAccessible = newSuspendedTransaction {
            if(gist.visibility != GistVisibility.Private && gist.user?.id != user.id) {
                call.respond(HttpStatusCode.Forbidden)
                return@newSuspendedTransaction false
            } else if (gist.user?.id != user.id) {
                call.respond(HttpStatusCode.NotFound)
                return@newSuspendedTransaction false
            }
            return@newSuspendedTransaction true
        }

        if(!isAccessible)
            return@authenticated


        block(gist, user)
    }
}

fun ApplicationCall.acceptsTurbo(): Boolean {
    // accept header contains "text/vnd.turbo-stream.html" when the client support tubro stream updates
    return false
}

fun ApplicationCall.url(gist: Gist) =
    this.url {
        path("gist", gist.id.value.encodeBase62())
    }

fun String.sha256() : ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(this.toByteArray())
}
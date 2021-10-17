package cloud.skadi.gist.data

import cloud.skadi.gist.decodeBase64
import cloud.skadi.gist.encodeWithArgon
import cloud.skadi.gist.plugins.GistSession
import cloud.skadi.gist.shared.GistVisibility
import io.ktor.util.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.LocalDateTime

fun allPublicGists(page: Int = 0) =
    Gist.find { GistTable.visibility eq GistVisibility.Public }
        .orderBy(GistTable.created to SortOrder.DESC)
        .limit(25, (25 * page).toLong())

fun allPublicGists(user: User, page: Int = 0) =
    Gist.find { (GistTable.visibility eq GistVisibility.Public) and (GistTable.user eq user.id) }
        .orderBy(GistTable.created to SortOrder.DESC)
        .limit(25, (25 * page).toLong())

fun allGistsIncludingUser(user: User, page: Int = 0) =
    Gist.find { (GistTable.visibility eq GistVisibility.Public) or (GistTable.user eq user.id) }
        .orderBy(GistTable.created to SortOrder.DESC)
        .limit(25, (25 * page).toLong())

suspend fun userByEmail(email: String) = newSuspendedTransaction {
    User.find { UserTable.email eq email }.firstOrNull()
}

 fun userByToken(token: String, updateLastUsed: Boolean = true) =
    transaction {
        val tokenToFind = if(token.startsWith("v2_")) {
            val split = token.substring(3).split('$')
            val salt = split.first()
            val token = split.last()
            encodeWithArgon(salt.decodeBase64(), token)
        } else {
            token
        }
        val dbToken = Token.find { TokenTable.token eq tokenToFind and (TokenTable.isTemporary eq false) }.firstOrNull()
        if (updateLastUsed)
            dbToken?.lastUsed = LocalDateTime.now()
        dbToken?.user
    }

suspend fun getUserByLogin(login: String) = newSuspendedTransaction {
    User.find { UserTable.login eq login }.firstOrNull()
}

suspend fun getTemporaryToken(token: String) = newSuspendedTransaction {
    val dbToken =
        Token.find { TokenTable.token eq token and (TokenTable.isTemporary eq true) and (TokenTable.lastUsed.isNull()) }
            .firstOrNull()
    dbToken
}

fun GistSession.user(): User? = transaction {
    User.find { UserTable.email eq this@user.email }.firstOrNull()
}

fun Gist.isAccessibleBy(user: User?) = this.visibility != GistVisibility.Private || (user != null && this.user == user)
fun Gist.isEditableBy(user: User?) = user != null && this.user == user

/**
 * Creates a new CSRF token for the user and will delete any existing token for the user.
 */
fun User.createNewCSRFToken() = transaction {
    CSRFToken.find { CSRFTable.user eq this@createNewCSRFToken.id }.forUpdate().forEach { it.delete() }
    val nonce = generateNonce()
    CSRFToken.new {
        this.token = nonce
        this.created = LocalDateTime.now()
        this.user = this@createNewCSRFToken
    }
    nonce
}

private val csrfMaxAge = Duration.ofHours(24)
/**
 *  Gets the current csrf token for the user. Will renew the token if it is cosidered to old.
 *  If no token exists it will **not** create a token!
 */
fun User.getCSRFToken() = transaction {
    val csrfToken = CSRFToken.find { CSRFTable.user eq this@getCSRFToken.id }.firstOrNull()
    if(csrfToken != null) {
        if(LocalDateTime.now().minus(csrfMaxAge) > csrfToken.created) {
            csrfToken.delete()
            val nonce = generateNonce()
            CSRFToken.new {
                this.token = nonce
                this.created = LocalDateTime.now()
                this.user = this@getCSRFToken
            }
            return@transaction nonce
        }
    }
    return@transaction csrfToken?.token
}

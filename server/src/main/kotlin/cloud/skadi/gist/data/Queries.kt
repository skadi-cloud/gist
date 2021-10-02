package cloud.skadi.gist.data

import cloud.skadi.gist.plugins.GistSession
import cloud.skadi.gist.shared.GistVisibility
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

fun allPublicGists(page: Int = 0) =
    Gist.find { GistTable.visibility eq GistVisibility.Public }
        .orderBy(GistTable.created to SortOrder.DESC)
        .limit(25, (25 * page).toLong())

fun allGistsIncludingUser(user: User, page: Int = 0) =
    Gist.find { (GistTable.visibility eq GistVisibility.Public) or (GistTable.user eq user.id) }
        .orderBy(GistTable.created to SortOrder.DESC)
        .limit(25, (25 * page).toLong())

suspend fun userByEmail(email: String) = newSuspendedTransaction {
    User.find { UserTable.email eq email }.firstOrNull()
}

suspend fun userByToken(token: String, updateLastUsed: Boolean = true) =
    newSuspendedTransaction {
        val dbToken = Token.find { TokenTable.token eq token }.firstOrNull()
        if(updateLastUsed)
            dbToken?.lastUsed = LocalDateTime.now()
        dbToken?.user
    }

suspend fun GistSession.user(): User? = newSuspendedTransaction {
    User.find { UserTable.email eq this@user.email }.firstOrNull()
}

fun Gist.isAccessibleBy(user: User?) = this.visibility != GistVisibility.Private || (user != null && this.user == user)
fun Gist.isEditableBy(user: User?) = user != null && this.user == user

package cloud.skadi.gist.views.csrf

import cloud.skadi.gist.data.User
import cloud.skadi.gist.data.createNewCSRFToken
import cloud.skadi.gist.data.getCSRFToken
import io.ktor.application.*
import io.ktor.request.*
import kotlinx.html.FORM
import kotlinx.html.hiddenInput

private const val CSRFTokenInput = "CSRFToken"
fun FORM.withCSRFToken(user: User) {
    val csrfToken = user.getCSRFToken() ?: user.createNewCSRFToken()
    hiddenInput {
        name = CSRFTokenInput
        value = csrfToken
    }
}

suspend fun ApplicationCall.validateCSRFToken(user: User): Boolean {
    val parameters = this.receiveParameters()
    val tokenString = parameters[CSRFTokenInput]
    val usersToken = user.getCSRFToken()
    return tokenString == usersToken
}
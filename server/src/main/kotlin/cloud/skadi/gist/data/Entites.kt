package cloud.skadi.gist.data

import cloud.skadi.gist.shared.GistVisibility
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.*

object UserTable : IntIdTable() {
    val email = varchar("email", 128).uniqueIndex()
    val regDate = datetime("reg-date")
    val lastLogin = datetime("last-login-date")
    val login = varchar("login", 1024).uniqueIndex()
    val name = varchar("name", 1024)
    val avatarUrl = varchar("avatarUrl", 2048).nullable()
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(UserTable)
    var email by UserTable.email
    var regDate by UserTable.regDate
    var lastLogin by UserTable.lastLogin
    var login by UserTable.login
    var name by UserTable.name
    var avatarUrl by UserTable.avatarUrl
    var likedGists by Gist via LikeTable
    val tokens by Token referrersOn TokenTable.user
}

object TokenTable: LongIdTable() {
    val token = varchar("token", 1024).uniqueIndex()
    val created = datetime("created")
    val lastUsed = datetime("last-used").nullable()
    val name = varchar("name", 256)
    val user = reference("user", UserTable, onDelete = ReferenceOption.CASCADE)
    val isTemporary = bool("is-temporary").default(true)
}

class Token(id: EntityID<Long>): LongEntity(id) {
    companion object: LongEntityClass<Token>(TokenTable)
    var token by TokenTable.token
    var user by User referencedOn TokenTable.user
    var created by TokenTable.created
    var lastUsed by TokenTable.lastUsed
    var name by TokenTable.name
    var isTemporary by TokenTable.isTemporary
}

object CSRFTable: LongIdTable() {
    val user = reference("user", UserTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val created = datetime("created")
    val token = varchar("token", 256).uniqueIndex()
}

class CSRFToken(id: EntityID<Long>): LongEntity(id) {
    companion object: LongEntityClass<CSRFToken>(CSRFTable)
    var user by User referencedOn CSRFTable.user
    var token by CSRFTable.token
    var created by CSRFTable.created
}

object GistTable: UUIDTable() {
    val name = varchar("name", 1024)
    val description = text("description").nullable()
    val user = optReference("user", UserTable, onDelete = ReferenceOption.SET_NULL)
    val visibility = enumeration("visibility", GistVisibility::class).default(GistVisibility.Private)
    val created = datetime("created").index()
}

class Gist(id: EntityID<UUID>): UUIDEntity(id) {
    companion object : UUIDEntityClass<Gist>(GistTable)
    var name by GistTable.name
    var description by GistTable.description
    var visibility by GistTable.visibility
    var user by User optionalReferencedOn GistTable.user
    var created by GistTable.created
    val roots by GistRoot referrersOn GistRootTable.gist
    val comments by Comment referrersOn CommentTable.gist
    var likedBy by User via LikeTable
}

object GistRootTable: UUIDTable() {
    val name = varchar("name", 1024)
    val gist = reference("gist", GistTable, onDelete = ReferenceOption.CASCADE)
    val node = text("node")
    val isRoot = bool("isRoot")
}

class GistRoot(id: EntityID<UUID>): UUIDEntity(id) {
    companion object : UUIDEntityClass<GistRoot>(GistRootTable)
    var gist by Gist referencedOn GistRootTable.gist
    var name by GistRootTable.name
    var node by GistRootTable.node
    var isRoot by GistRootTable.isRoot
}

object CommentTable: IntIdTable() {
    val user = optReference("user", UserTable, onDelete = ReferenceOption.SET_NULL)
    val gist = reference("gist", GistTable, onDelete = ReferenceOption.CASCADE)
    val markdown = text("markdown")
}

class Comment(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<Comment>(CommentTable)
    var user by User optionalReferencedOn CommentTable.user
    var root by Gist referencedOn CommentTable.gist
    var markdown by CommentTable.markdown
}

object LikeTable: Table() {
    var user = reference("user", UserTable, onDelete = ReferenceOption.CASCADE)
    var gist = reference("gist", GistTable, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(user, gist)
}

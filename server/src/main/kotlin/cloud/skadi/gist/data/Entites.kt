package cloud.skadi.gist.data

import cloud.skadi.gist.data.GistRoot.Companion.referrersOn
import cloud.skadi.gist.shared.GistVisibility
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.*

object Users : IntIdTable() {
    val email = varchar("email", 128).uniqueIndex()
    val regDate = datetime("reg-date")
    val lastLogin = datetime("last-login-date")
    val login = varchar("login", 1024).uniqueIndex()
    val name = varchar("name", 1024)
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)
    var email by Users.email
    var regDate by Users.regDate
    var lastLogin by Users.lastLogin
    var login by Users.login
    var name by Users.name
    var likedGists by Gist via LikeTable
}

object TokenTable: IntIdTable() {
    val token = varchar("token", 256).uniqueIndex()
    val created = datetime("created")
    val name = varchar("name", 256)
    val user = reference("user", Users)
}

class Token(id: EntityID<Int>): IntEntity(id) {
    companion object: IntEntityClass<Token>(TokenTable)
    var token by TokenTable.token
    var user by User referencedOn TokenTable.user
    var created by TokenTable.created
    var name by TokenTable.name
}

object GistTable: UUIDTable() {
    val name = varchar("name", 1024)
    val description = text("description").nullable()
    val user = optReference("user", Users)
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
    val gist = reference("gist", GistTable)
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
    val user = reference("user", Users)
    val gist = reference("gist", GistTable)
    val markdown = text("markdown")
}

class Comment(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<Comment>(CommentTable)
    var user by User referencedOn CommentTable.user
    var root by Gist referencedOn CommentTable.gist
    var markdown by CommentTable.markdown
}

object LikeTable: Table() {
    var user = reference("user", Users)
    var gist = reference("gist", GistTable)
    override val primaryKey = PrimaryKey(user, gist)
}

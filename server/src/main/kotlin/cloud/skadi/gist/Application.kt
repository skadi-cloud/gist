package cloud.skadi.gist

import cloud.skadi.gist.data.*
import cloud.skadi.gist.plugins.*
import cloud.skadi.gist.routing.configureGistRoutes
import cloud.skadi.gist.routing.configureHomeRouting
import cloud.skadi.gist.routing.configureIdeRoutes
import cloud.skadi.gist.turbo.TurboStreamMananger
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("dbInfrastructure")

val SQL_PASSWORD = getEnvOfFail("SQL_PASSWORD")
val SQL_USER = getEnvOfFail("SQL_USER")
val SQL_DB = getEnvOfFail("SQL_DB")
val SQL_HOST = getEnvOfFail("SQL_HOST")

fun initDb(jdbc: String, database: String, user: String, password: String): Boolean {


    Database.connect(
        "$jdbc$database", driver = "org.postgresql.Driver",
        user = user, password = password
    )

    return transaction {
        try {
            withDataBaseLock {
                SchemaUtils.createMissingTablesAndColumns(UserTable, GistTable, GistRootTable, TokenTable, LikeTable, CommentTable)
            }
        } catch (e: Throwable) {
            logger.error("error updating schema" ,e)
            return@transaction false
        }
        return@transaction true
    }
}

@ExperimentalStdlibApi
fun main() {

    initDb("jdbc:postgresql://$SQL_HOST/",SQL_DB,SQL_USER, SQL_PASSWORD)
    val storage = DirectoryBasedStorage(File("data"), "rendered")
    val tsm = TurboStreamMananger()
    val store = GistStorage(storage::get, storage::put)
    embeddedServer(Netty, environment = applicationEngineEnvironment {
        connector {
            port = 8080
            host = "0.0.0.0"
        }
        developmentMode = true
        module {
            configureRouting()
            configureOAuth()
            configureHTTP()
            configureMonitoring()
            configureTemplating()
            configureSockets()
            configureGistRoutes(tsm, storage::put, storage::get)
            configureIdeRoutes()
            configureHomeRouting(tsm, store)
            storage.install(this)
        }
    }).start(wait = true)
}

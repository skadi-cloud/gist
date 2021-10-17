package cloud.skadi

import cloud.skadi.gist.encodeBase62
import cloud.skadi.gist.getEnvOfFail
import cloud.skadi.gist.getEnvOrDefault
import cloud.skadi.gist.mainModule
import cloud.skadi.gist.storage.DirectoryBasedStorage
import cloud.skadi.gist.turbo.TurboStreamMananger
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.nio.file.Files
import java.sql.DriverManager
import java.util.*
import kotlin.test.assertEquals

fun newTestDb(): String {
    val SQL_PASSWORD = getEnvOfFail("SQL_PASSWORD")
    val SQL_USER = getEnvOfFail("SQL_USER")
    val testDbId = UUID.randomUUID()
    val SQL_DB = "skadi-gist-test-${testDbId.encodeBase62()}"
    val SQL_HOST = getEnvOrDefault("SQL_HOST", "localhost:5432")
    val url = "jdbc:postgresql://$SQL_HOST/"
    val connection = DriverManager.getConnection(url, SQL_USER, SQL_PASSWORD)
    val statement = connection.createStatement()
    statement.execute("""drop database if exists "$SQL_DB";""")
    connection.createStatement().execute("""create database "$SQL_DB";""")
    return SQL_DB
}

fun cleanDb(name: String) {
    val SQL_PASSWORD = getEnvOfFail("SQL_PASSWORD")
    val SQL_USER = getEnvOfFail("SQL_USER")
    val SQL_HOST = getEnvOrDefault("SQL_HOST", "localhost:5432")
    val url = "jdbc:postgresql://$SQL_HOST/"
    val connection = DriverManager.getConnection(url, SQL_USER, SQL_PASSWORD)
    val statement = connection.createStatement()
    statement.execute("""drop database if exists "$name";""")
}

@ExperimentalStdlibApi
fun Application.testModuleSetup(dbName: String) {
    val tsm = TurboStreamMananger()
    val tempDirectory = Files.createTempDirectory("gist").toFile()
    tempDirectory.deleteOnExit()
    val directoryBasedStorage = DirectoryBasedStorage(tempDirectory, "images")
    mainModule(isProduction = false, tsm = tsm, storage = directoryBasedStorage, sqlDb = dbName)
}

fun withTestDb(block: (String) -> Unit) {
    val dbName = newTestDb()
    try {
        block(dbName)
    } finally {
        cleanDb(dbName)
    }
}

fun TestApplicationEngine.login(user: String = "testuser", email: String = "test@skadi.cloud") {
    handleRequest(HttpMethod.Post, "/testlogin") {
        addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
        setBody(listOf("user" to user, "email" to email).formUrlEncode())
    }.apply {
        assertEquals(HttpStatusCode.OK, response.status())
    }
}
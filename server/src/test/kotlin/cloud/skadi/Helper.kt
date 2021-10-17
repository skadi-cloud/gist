package cloud.skadi

import cloud.skadi.gist.encodeBase62
import cloud.skadi.gist.getEnvOfFail
import cloud.skadi.gist.getEnvOrDefault
import cloud.skadi.gist.mainModule
import cloud.skadi.gist.shared.*
import cloud.skadi.gist.storage.DirectoryBasedStorage
import cloud.skadi.gist.turbo.TurboStreamMananger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.nio.file.Files
import java.sql.DriverManager
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

fun TestApplicationEngine.testGist(visibility: GistVisibility = GistVisibility.Private) =
    handleRequest(HttpMethod.Post, "/gist/create") {
        val createRequest = GistCreationRequest(
            "test gist", "Some awesome stuff", visibility,
            listOf(
                GistNode(
                    "testRoot",
                    TEST_IMAGE_DATA,
                    AST(
                        emptyList(),
                        emptyList(),
                        Node(
                            UUID.randomUUID().toString(),
                            "test-concept",
                            emptyList(),
                            emptyList(),
                            emptyList()
                        )
                    ),
                    true
                )
            )
        )
        setBody(jacksonObjectMapper().writeValueAsString(createRequest))
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }.run {
        assertEquals(HttpStatusCode.Found, response.status())
        val locationHeader = response.headers[HttpHeaders.Location]
        assertNotNull(locationHeader)
        Url(locationHeader)
    }

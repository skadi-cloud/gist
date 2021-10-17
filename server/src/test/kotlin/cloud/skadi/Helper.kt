package cloud.skadi

import cloud.skadi.gist.getEnvOfFail
import cloud.skadi.gist.getEnvOrDefault
import cloud.skadi.gist.mainModule
import cloud.skadi.gist.storage.DirectoryBasedStorage
import cloud.skadi.gist.turbo.TurboStreamMananger
import io.ktor.application.*
import java.nio.file.Files
import java.sql.DriverManager

fun ensureDbEmpty() {
    val SQL_PASSWORD = getEnvOfFail("SQL_PASSWORD")
    val SQL_USER = getEnvOfFail("SQL_USER")
    val SQL_DB = getEnvOrDefault("SQL_DB", "skadi-gist")
    val SQL_HOST = getEnvOrDefault("SQL_HOST", "localhost:5432")
    val url = "jdbc:postgresql://$SQL_HOST/$SQL_DB"
    val connection = DriverManager.getConnection(url, SQL_USER, SQL_PASSWORD)
    val statement = connection.createStatement()
    val result = statement.executeQuery(
        "select 'drop table if exists \"' || tablename || '\" cascade;'\n" +
                "from pg_tables\n" +
                "where schemaname = 'public';"
    )
    while (result.next()) {
        val stmt = connection.createStatement()
        stmt.execute(result.getString(1))
    }
}

@ExperimentalStdlibApi
fun Application.testModuleSetup() {
    val tsm = TurboStreamMananger()
    val tempDirectory = Files.createTempDirectory("gist").toFile()
    tempDirectory.deleteOnExit()
    val directoryBasedStorage = DirectoryBasedStorage(tempDirectory, "images")
    mainModule(isProduction = false, tsm = tsm, storage = directoryBasedStorage)
}
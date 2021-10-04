package cloud.skadi.gist

import cloud.skadi.gist.data.*
import cloud.skadi.gist.plugins.*
import cloud.skadi.gist.routing.configureGistRoutes
import cloud.skadi.gist.routing.configureHomeRouting
import cloud.skadi.gist.routing.configureIdeRoutes
import cloud.skadi.gist.routing.configureUserRouting
import cloud.skadi.gist.storage.DirectoryBasedStorage
import cloud.skadi.gist.storage.S3BasedStorage
import cloud.skadi.gist.turbo.TurboStreamMananger
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.io.File
import java.net.URI


private val logger = LoggerFactory.getLogger("dbInfrastructure")

const val INTERNAL_API_PORT = 9090


val SQL_PASSWORD = getEnvOfFail("SQL_PASSWORD")
val SQL_USER = getEnvOfFail("SQL_USER")
val SQL_DB = getEnvOfFail("SQL_DB")
val SQL_HOST = getEnvOfFail("SQL_HOST")

val S3_ACCESS_KEY = getEnvOrDefault("S3_ACCESS_KEY", "")
val S3_SECRET_KEY = getEnvOrDefault("S3_SECRET_KEY", "")
val S3_BUCKET_NAME = getEnvOrDefault("S3_BUCKET_NAME", "")
val S3_ENDPOINT = getEnvOrDefault("S3_ENDPOINT", "")
val S3_REGION = getEnvOrDefault("S3_REGION", "")

val STORAGE_KIND = getEnvOrDefault("STORAGE_KIND", "directory")
val STORAGE_DIRECTORY = getEnvOrDefault("STORAGE_DIRECTORY", "./data")


fun initDb(jdbc: String, database: String, user: String, password: String): Boolean {


    Database.connect(
        "$jdbc$database", driver = "org.postgresql.Driver",
        user = user, password = password
    )

    return transaction {
        try {
            withDataBaseLock {
                SchemaUtils.createMissingTablesAndColumns(
                    UserTable,
                    GistTable,
                    GistRootTable,
                    TokenTable,
                    LikeTable,
                    CommentTable
                )
            }
        } catch (e: Throwable) {
            logger.error("error updating schema", e)
            return@transaction false
        }
        return@transaction true
    }
}


@ExperimentalStdlibApi
fun main() {
    initDb("jdbc:postgresql://$SQL_HOST/", SQL_DB, SQL_USER, SQL_PASSWORD)
    val storage = when(STORAGE_KIND) {
        "s3" -> {
            val (client, signer) = initS3()
            S3BasedStorage(client, signer, S3_BUCKET_NAME)
        }
        "directory" -> DirectoryBasedStorage(File(STORAGE_DIRECTORY), "rendered")
        else -> throw RuntimeException("Unknown storage kind: $STORAGE_KIND")
    }

    val tsm = TurboStreamMananger()
    embeddedServer(Netty, environment = applicationEngineEnvironment {
        connector {
            port = 8080
            host = "0.0.0.0"
        }
        connector {
            host = "0.0.0.0"
            port = INTERNAL_API_PORT
        }

        developmentMode = true
        module {
            configureRouting()
            configureMetrics()
            configureOAuth()
            configureHTTP()
            configureSockets()
            configureGistRoutes(tsm, storage)
            configureIdeRoutes()
            configureHomeRouting(tsm, storage)
            configureUserRouting()
            if(STORAGE_KIND == "directory") {
                (storage as DirectoryBasedStorage).install(this)
            }
        }
    }).start(wait = true)
}


fun initS3(): Pair<S3Client, S3Presigner> {

    if(S3_ENDPOINT.isBlank()) {
        logger.error("Missing S3_ENDPOINT.")
        throw RuntimeException("Missing S3_ENDPOINT.")
    }

    if(S3_ACCESS_KEY.isBlank()) {
        logger.error("Missing S3_ACCESS_KEY.")
        throw RuntimeException("Missing S3_ACCESS_KEY.")
    }

    if(S3_SECRET_KEY.isBlank()) {
        logger.error("Missing S3_SECRET_KEY.")
        throw RuntimeException("Missing S3_SECRET_KEY.")
    }

    if(S3_REGION.isBlank()) {
        logger.error("Missing S3_REGION.")
        throw RuntimeException("Missing S3_REGION.")
    }

    if(S3_BUCKET_NAME.isBlank()) {
        logger.error("Missing S3_BUCKET_NAME.")
        throw RuntimeException("Missing S3_BUCKET_NAME.")
    }

    val presigner = S3Presigner.builder()
        .region(Region.of(S3_REGION))
        .endpointOverride(URI(S3_ENDPOINT))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(S3_ACCESS_KEY, S3_SECRET_KEY)))
        .build()

    val client = S3Client.builder()
        .region(Region.of(S3_REGION))
        .endpointOverride(URI(S3_ENDPOINT))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(S3_ACCESS_KEY, S3_SECRET_KEY)))
        .build()
    return client to presigner
}

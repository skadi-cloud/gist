package cloud.skadi.gist.static

import cloud.skadi.gist.data.Gist
import cloud.skadi.gist.data.GistTable
import cloud.skadi.gist.encodeBase62
import cloud.skadi.gist.markdownToHtml
import cloud.skadi.gist.shared.GistVisibility
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

// ---------------------------------------------------------------------------
// Immutable snapshots – all data loaded eagerly inside a single transaction
// so HTML generation happens outside any database transaction.
// ---------------------------------------------------------------------------

data class GistSnapshot(
    val id: UUID,
    val name: String,
    val description: String?,
    val userLogin: String?,
    val userAvatarUrl: String?,
    val created: LocalDateTime,
    val roots: List<GistRootSnapshot>
)

data class GistRootSnapshot(
    val id: UUID,
    val name: String,
    val isRoot: Boolean
)

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

fun main() {
    val outputDir = File(System.getenv("OUTPUT_DIR")?.takeIf { it.isNotBlank() } ?: "build/static-site")
    val storageDir = System.getenv("STORAGE_DIR")?.takeIf { it.isNotBlank() }?.let { File(it) }

    val sqlUser = System.getenv("SQL_USER")?.takeIf { it.isNotBlank() }
        ?: error("SQL_USER environment variable is required")
    val sqlPassword = System.getenv("SQL_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: error("SQL_PASSWORD environment variable is required")
    val sqlHost = System.getenv("SQL_HOST")?.takeIf { it.isNotBlank() } ?: "localhost:5432"
    val sqlDb = System.getenv("SQL_DB")?.takeIf { it.isNotBlank() } ?: "gist"

    Database.connect(
        "jdbc:postgresql://$sqlHost/$sqlDb",
        driver = "org.postgresql.Driver",
        user = sqlUser,
        password = sqlPassword
    )

    StaticSiteGenerator(outputDir, storageDir).generate()
}

// ---------------------------------------------------------------------------
// Generator
// ---------------------------------------------------------------------------

class StaticSiteGenerator(
    private val outputDir: File,
    private val storageDir: File?
) {
    private val classLoader = StaticSiteGenerator::class.java.classLoader
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun generate() {
        outputDir.mkdirs()
        println("Generating static site in: ${outputDir.absolutePath}")

        copyStaticAssets()

        // Load all public gists eagerly within a single transaction.
        val gists: List<GistSnapshot> = transaction {
            Gist.find { GistTable.visibility eq GistVisibility.Public }
                .orderBy(GistTable.created to SortOrder.DESC)
                .toList()
                .map { gist ->
                    GistSnapshot(
                        id = gist.id.value,
                        name = gist.name,
                        description = gist.description,
                        userLogin = gist.user?.login,
                        userAvatarUrl = gist.user?.avatarUrl,
                        created = gist.created,
                        roots = gist.roots.toList().map { root ->
                            GistRootSnapshot(id = root.id.value, name = root.name, isRoot = root.isRoot)
                        }
                    )
                }
        }

        println("Found ${gists.size} public gists")

        generateIndexPage(gists)

        gists.forEachIndexed { index, gist ->
            println("Generating page ${index + 1}/${gists.size}: ${gist.id.encodeBase62()}")
            generateGistPage(gist)
            copyGistImages(gist)
        }

        println("Static site generation complete! Output: ${outputDir.absolutePath}")
    }

    // -----------------------------------------------------------------------
    // Asset copying helpers
    // -----------------------------------------------------------------------

    /** Copy a single classpath resource to a file on disk. */
    private fun copyResource(resourcePath: String, destFile: File) {
        val input = classLoader.getResourceAsStream(resourcePath) ?: run {
            println("  Warning: resource not found on classpath: $resourcePath")
            return
        }
        destFile.parentFile.mkdirs()
        input.use { i -> destFile.outputStream().use { o -> i.copyTo(o) } }
    }

    /**
     * Walk a classpath resource *directory* and copy all files to [destDir].
     * Works when running via `./gradlew run` (resources are on the file system).
     */
    private fun copyResourceDirectory(resourcePath: String, destDir: File) {
        val resourceUrl = classLoader.getResource(resourcePath) ?: run {
            println("  Warning: resource directory not found: $resourcePath")
            return
        }
        try {
            val sourceDir = File(resourceUrl.toURI())
            if (!sourceDir.isDirectory) return
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = file.relativeTo(sourceDir).path
                val dest = File(destDir, relativePath)
                dest.parentFile.mkdirs()
                file.copyTo(dest, overwrite = true)
            }
        } catch (e: Exception) {
            println("  Warning: could not copy resource directory $resourcePath: ${e.message}")
        }
    }

    private fun copyStaticAssets() {
        val assetsDir = File(outputDir, "assets")
        println("Copying static assets to: ${assetsDir.absolutePath}")

        // Main stylesheet
        copyResource("styles/styles.css", File(assetsDir, "styles/styles.css"))

        // Font-Awesome (CSS + webfonts)
        copyResourceDirectory("font-awesome", File(assetsDir, "font-awesome"))

        // Custom webfonts (Montserrat, Raleway, NanumGothicCoding)
        copyResourceDirectory("webfonts", File(assetsDir, "webfonts"))

        // Static images
        listOf(
            "annon.jpg",
            "apple-touch-icon-inverted.png",
            "favicon-inverted-16x16.png",
            "favicon-inverted-32x32.png",
            "icon-inverted.png",
            "icon.png",
            "safari-pinned-tab-inverted.svg"
        ).forEach { file ->
            copyResource("static/$file", File(assetsDir, file))
        }
    }

    /** Copy gist images from the local storage directory to the output directory. */
    private fun copyGistImages(gist: GistSnapshot) {
        storageDir ?: return
        val sourceDir = File(storageDir, gist.id.toString())
        if (!sourceDir.exists()) {
            println("  Warning: no images for gist ${gist.id.encodeBase62()} at ${sourceDir.absolutePath}")
            return
        }
        val destDir = File(File(outputDir, "assets/images"), gist.id.encodeBase62())
        destDir.mkdirs()
        sourceDir.listFiles()?.forEach { file ->
            file.copyTo(File(destDir, file.name), overwrite = true)
        }
    }

    // -----------------------------------------------------------------------
    // URL helpers
    // -----------------------------------------------------------------------

    private fun gistPageUrl(gist: GistSnapshot) = "/gist/${gist.id.encodeBase62()}/"
    private fun previewUrl(gist: GistSnapshot) =
        "/assets/images/${gist.id.encodeBase62()}/preview.png"
    private fun rootImageUrl(gistId: UUID, rootName: String) =
        "/assets/images/${gistId.encodeBase62()}/$rootName.png"

    // -----------------------------------------------------------------------
    // HTML building
    // -----------------------------------------------------------------------

    /**
     * Build a complete HTML page string.
     * [pageTitle] and [pageDescription] populate the `<title>` and meta description.
     * [bodyBlock] is appended to `<body>` after the standard header.
     */
    private fun buildPage(
        pageTitle: String,
        pageDescription: String? = null,
        bodyBlock: BODY.() -> Unit
    ): String = createHTML().html {
        head {
            meta { charset = "UTF-8" }
            meta { name = "viewport"; content = "width=device-width, initial-scale=1.0" }
            title { +pageTitle }
            if (pageDescription != null) {
                meta { name = "description"; content = pageDescription }
            }
            styleLink("/assets/styles/styles.css")
            styleLink("/assets/font-awesome/css/all.css")
            link {
                rel = "apple-touch-icon"; sizes = "180x180"
                href = "/assets/apple-touch-icon-inverted.png"
            }
            link {
                rel = "icon"; type = "image/png"; sizes = "32x32"
                href = "/assets/favicon-inverted-32x32.png"
            }
            link {
                rel = "icon"; type = "image/png"; sizes = "16x16"
                href = "/assets/favicon-inverted-16x16.png"
            }
            meta { name = "theme-color"; content = "#00cc99" }
        }
        body {
            // ---- Standard header ----
            div {
                id = "header"
                div {
                    id = "branding"
                    a {
                        href = "/"
                        img {
                            id = "header-image"
                            src = "/assets/icon-inverted.png"
                            alt = "Skadi Cloud logo"
                        }
                    }
                    div {
                        h1 { +"Skadi Cloud" }
                        h1(classes = "sub") { +"Gist" }
                    }
                }
                // No interactive user menu in the static (archived) site.
            }

            // ---- Sunset notice ----
            div {
                style =
                    "background:#fff3cd;border-bottom:2px solid #ffc107;" +
                    "padding:12px 24px;text-align:center;font-family:Montserrat,sans-serif;"
                +"⚠️ "
                strong { +"This service has been sunset." }
                +" No new gists can be created. This is an archived, read-only version of the site."
            }

            bodyBlock()
        }
    }

    // -----------------------------------------------------------------------
    // Shared HTML components
    // -----------------------------------------------------------------------

    private fun FlowContent.renderUserDetailsAndName(gist: GistSnapshot, getUrl: (GistSnapshot) -> String) {
        div("name-and-user") {
            img {
                src = gist.userAvatarUrl ?: "/assets/annon.jpg"
                classes = setOf("user-avatar")
                alt = gist.userLogin ?: "Anonymous"
            }
            div("profile-date") {
                span {
                    if (gist.userLogin != null) {
                        +gist.userLogin
                    } else {
                        span { +"Anonymous" }
                    }
                    +"/"
                    a {
                        href = getUrl(gist)
                        +gist.name.ifEmpty { "No name" }
                    }
                }
                span {
                    +"Created ${gist.created.format(dateFormatter)}"
                }
            }
        }
    }

    private fun FlowContent.renderGistMetadata(gist: GistSnapshot) {
        div("facts") {
            ul {
                li(classes = "roots") {
                    i(classes = "far fa-file-code") {}
                    +" ${gist.roots.size} roots"
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Page generators
    // -----------------------------------------------------------------------

    private fun generateIndexPage(gists: List<GistSnapshot>) {
        val pageTitle = "Skadi Cloud Gist: Home"
        val pageDescription = "Share your MPS code almost as easy as text"

        val html = buildPage(pageTitle, pageDescription) {
            div("container") {
                gists.forEach { gist ->
                    div(classes = "gist snippet") {
                        id = "gist-${gist.id.encodeBase62()}"
                        div("meta") {
                            renderUserDetailsAndName(gist) { gistPageUrl(it) }
                            renderGistMetadata(gist)
                        }
                        div(classes = "summary") {
                            if (!gist.description.isNullOrBlank()) {
                                unsafe { +markdownToHtml(gist.description.take(1024)) }
                            }
                        }
                        val firstRoot = gist.roots.firstOrNull()
                        if (firstRoot != null) {
                            div("root") {
                                div("name") { b { +firstRoot.name } }
                                a {
                                    href = gistPageUrl(gist)
                                    img(classes = "rendered") {
                                        src = previewUrl(gist)
                                        alt = gist.name
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        File(outputDir, "index.html").writeText(html)
        println("Generated index.html (${gists.size} gists)")
    }

    private fun generateGistPage(gist: GistSnapshot) {
        val pageTitle = gist.name.ifEmpty { "Untitled Gist" }

        val html = buildPage(pageTitle, gist.description) {
            div("above") {
                renderUserDetailsAndName(gist) { gistPageUrl(it) }
            }
            div("container") {
                div {
                    id = "gist-${gist.id.encodeBase62()}"
                    div(classes = "gist-description") {
                        unsafe { +markdownToHtml(gist.description ?: "") }
                    }
                    gist.roots.forEach { root ->
                        div("root") {
                            div("name") { b { +root.name } }
                            img(classes = "rendered") {
                                src = rootImageUrl(gist.id, root.name)
                                alt = root.name
                            }
                        }
                    }
                }
            }
        }

        val gistDir = File(File(outputDir, "gist"), gist.id.encodeBase62())
        gistDir.mkdirs()
        File(gistDir, "index.html").writeText(html)
    }
}

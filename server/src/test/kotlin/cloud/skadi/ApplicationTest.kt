package cloud.skadi

import cloud.skadi.gist.shared.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jsoup.Jsoup
import org.junit.Test
import java.util.*
import kotlin.math.log
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExperimentalStdlibApi
class ApplicationTest {
    @Test
    fun testRoot() {
        withTestDb { dbName ->
            withTestApplication({ testModuleSetup(dbName) }) {
                handleRequest(HttpMethod.Get, "/").apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                    val document = Jsoup.parse(response.content)
                    val firstLinkInMenu = document.selectFirst("#menu > a")
                    assertNotNull(firstLinkInMenu)
                    assertEquals("Login", firstLinkInMenu.text())
                }
            }
        }
    }

    @Test
    fun `settings available after login`() {
        withTestDb { dbName ->
            withTestApplication({ testModuleSetup(dbName) }) {
                cookiesSession {
                    login()
                    handleRequest(HttpMethod.Get, "/").apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                        val document = Jsoup.parse(response.content)
                        val firstLinkInMenu = document.selectFirst("#menu > ul > li:nth-child(1) > a")
                        assertNotNull(firstLinkInMenu)
                        assertEquals("Settings", firstLinkInMenu.text())
                    }
                }
            }
        }
    }

    @Test
    fun `can create public gist without login`() {
        withTestDb { dbName ->
            withTestApplication({ testModuleSetup(dbName) }) {
                cookiesSession {
                    handleRequest(HttpMethod.Post, "/gist/create") {
                        val createRequest = GistCreationRequest(
                            "test gist", "Some awesome stuff", GistVisibility.Public,
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
                    }.apply {
                        assertEquals(HttpStatusCode.Found, response.status())
                        val locationHeader = response.headers[HttpHeaders.Location]
                        assertNotNull(locationHeader)
                        handleRequest(HttpMethod.Get, Url(locationHeader).encodedPath).apply {
                            assertEquals(HttpStatusCode.OK, response.status())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `can create unlisted gist without login`() {
        withTestDb { dbName ->
            withTestApplication({ testModuleSetup(dbName) }) {
                cookiesSession {
                    handleRequest(HttpMethod.Post, "/gist/create") {
                        val createRequest = GistCreationRequest(
                            "test gist", "Some awesome stuff", GistVisibility.UnListed,
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
                    }.apply {
                        assertEquals(HttpStatusCode.Found, response.status())
                        val locationHeader = response.headers[HttpHeaders.Location]
                        assertNotNull(locationHeader)
                        handleRequest(HttpMethod.Get, Url(locationHeader).encodedPath).apply {
                            assertEquals(HttpStatusCode.OK, response.status())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `cannot create private gist without login`() {
        withTestDb { dbName ->
            withTestApplication({ testModuleSetup(dbName) }) {
                cookiesSession {
                    handleRequest(HttpMethod.Post, "/gist/create") {
                        val createRequest = GistCreationRequest(
                            "test gist", "Some awesome stuff", GistVisibility.Private,
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
                    }.apply {
                        assertEquals(HttpStatusCode.BadRequest, response.status())
                    }
                }
            }
        }
    }
    @Test
    fun `can create private gist after login`() {
        withTestDb { dbName ->
            withTestApplication({ testModuleSetup(dbName) }) {
                cookiesSession {
                    login()
                    handleRequest(HttpMethod.Post, "/gist/create") {
                        val createRequest = GistCreationRequest(
                            "test gist", "Some awesome stuff", GistVisibility.Private,
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
                    }.apply {
                        assertEquals(HttpStatusCode.Found, response.status())
                    }
                }
            }
        }
    }

    @Test
    fun `cannot access private gist without login`() {
        withTestDb { dbName ->
            withTestApplication({ testModuleSetup(dbName) }) {
                cookiesSession {
                    login()
                    val gistUrl = handleRequest(HttpMethod.Post, "/gist/create") {
                        val createRequest = GistCreationRequest(
                            "test gist", "Some awesome stuff", GistVisibility.Private,
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
                    handleRequest(HttpMethod.Get,"/logout").apply {
                        assertEquals(HttpStatusCode.Found, response.status())
                    }
                    handleRequest(HttpMethod.Get, gistUrl.encodedPath).apply {
                        assertEquals(HttpStatusCode.NotFound, response.status())
                    }
                }
            }
        }
    }
    @Test
    fun `cannot access private gist as a different user`() {
        withTestDb { dbName ->
            withTestApplication({ testModuleSetup(dbName) }) {
                cookiesSession {
                    login()
                    val gistUrl = handleRequest(HttpMethod.Post, "/gist/create") {
                        val createRequest = GistCreationRequest(
                            "test gist", "Some awesome stuff", GistVisibility.Private,
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
                    handleRequest(HttpMethod.Get,"/logout").apply {
                        assertEquals(HttpStatusCode.Found, response.status())
                    }
                    login("testuser2", "test2@skadi.cloud")
                    handleRequest(HttpMethod.Get, gistUrl.encodedPath).apply {
                        assertEquals(HttpStatusCode.NotFound, response.status())
                    }
                }
            }
        }
    }
    @Test
    fun `gist with empty root is rejected`() {
        withTestDb { dbName ->
            withTestApplication({ testModuleSetup(dbName) }) {
                cookiesSession {
                    handleRequest(HttpMethod.Post, "/gist/create") {
                        val createRequest = GistCreationRequest(
                            "test gist", "Some awesome stuff", GistVisibility.Public, emptyList()
                        )
                        setBody(jacksonObjectMapper().writeValueAsString(createRequest))
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }.apply {
                        assertEquals(HttpStatusCode.BadRequest, response.status())
                    }
                }
            }
        }
    }

    @Test
    fun `cannot edit gist gist without login`() {
        withTestDb { dbName ->
            withTestApplication({ testModuleSetup(dbName) }) {
                cookiesSession {
                    login()
                    val gistUrl = handleRequest(HttpMethod.Post, "/gist/create") {
                        val createRequest = GistCreationRequest(
                            "test gist", "Some awesome stuff", GistVisibility.Private,
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
                    handleRequest(HttpMethod.Get,"/logout").apply {
                        assertEquals(HttpStatusCode.Found, response.status())
                    }
                    handleRequest(HttpMethod.Get, gistUrl.encodedPath + "/edit").apply {
                        assertEquals(HttpStatusCode.Found, response.status())
                        assert(response.headers[HttpHeaders.Location]!!.startsWith("/login/github"))
                    }
                }
            }
        }
    }
}
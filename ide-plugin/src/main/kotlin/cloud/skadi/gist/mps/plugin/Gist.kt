package cloud.skadi.gist.mps.plugin

import cloud.skadi.gist.mps.plugin.config.SkadiGistSettings
import cloud.skadi.gist.shared.*
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.util.Urls
import io.ktor.client.engine.java.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SPropertyOperations
import jetbrains.mps.smodel.SNodePointer
import jetbrains.mps.smodel.StaticReference
import jetbrains.mps.smodel.adapter.structure.concept.SConceptAdapterById
import jetbrains.mps.smodel.adapter.structure.language.SLanguageAdapterById
import jetbrains.mps.smodel.adapter.structure.link.SContainmentLinkAdapterById
import jetbrains.mps.smodel.adapter.structure.property.SPropertyAdapterById
import jetbrains.mps.smodel.adapter.structure.ref.SReferenceLinkAdapterById
import jetbrains.mps.smodel.adapter.structure.types.SEnumerationAdapter
import jetbrains.mps.smodel.adapter.structure.types.SPrimitiveTypes
import org.jetbrains.builtInWebServer.BuiltInServerOptions
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SRepository
import java.net.InetAddress
import java.util.*

val client = io.ktor.client.HttpClient(Java) {
    followRedirects = false
}
const val HOST = "http://localhost:8080/gist/create"

private val mapper = JsonMapper.builder()
    .addModule(KotlinModule())
    .build()


fun getLoginUrl(settings: SkadiGistSettings): String {

    val serverPort = BuiltInServerOptions.getInstance().effectiveBuiltInServerPort
    val authority = "localhost:$serverPort"
    val url = Urls.newHttpUrl(authority, "/skadi-gist/login-response")

    return URLBuilder(settings.backendAddress).also { builder ->
        builder.pathComponents("ide", "login")
        builder.parameters.also {
            it.append(PARAMETER_DEVICE_NAME,InetAddress.getLocalHost().hostName)
            it.append(PARAMETER_CALLBACK,url.toString())
            it.append(PARAMETER_CSRF_TOKEN, settings.newCsrfToken())
        }
    }.buildString()
}

suspend fun upload(
    name: String,
    description: String?,
    visibility: GistVisibility,
    nodes: List<SNode>,
    repository: SRepository
): String? {
    val gistCreationRequest = GistCreationRequest(
        name = name,
        description = description,
        visibility = visibility,
        roots = nodes.mapIndexed { index, node ->
            var isRoot = false
            var nodeName : String? = null
            repository.modelAccess.runReadAction {
                isRoot = node.parent == null
                nodeName = node.presentation
            }
            GistNode(
                nodeName ?: "name-$index",
                base64Img = Base64.getMimeEncoder().encodeToString(node.asImage(repository).toByteArray()),
                serialised = serializeRootNode(node),
                isRoot = isRoot
            )
        })
    val response = client.post<HttpResponse>(HOST) {
        expectSuccess = false
        contentType(ContentType.Application.Json)
        body = mapper.writeValueAsString(gistCreationRequest)
    }

    return if (response.status.value == 302) {
        response.headers[HttpHeaders.Location]
    } else {
        null
    }
}

fun serializeRootNode(node: SNode): AST {
    var serializedNode: Node? = null

    var usedLanguages: List<SLanguage> = emptyList()
    var imports: List<SModelReference> = emptyList()

    node.model!!.repository.modelAccess.runReadAction {
        serializedNode = node.serialize()
        val descendants: MutableList<SNode> = SNodeOperations.getNodeDescendants(node, null, true)
        usedLanguages = descendants.map { it.concept.language }.distinct()
        imports = descendants.flatMap { it.references.mapNotNull { reference -> reference.targetSModelReference } }
            .distinct()
    }

    return AST(
        imports.map { Import(it.modelName, it.modelId.toString()) },
        usedLanguages.map { UsedLanguage(it.qualifiedName, (it as SLanguageAdapterById).serialize()) },
        serializedNode!!
    )
}

fun SProperty.convertType() =
    when (this.type) {
        SPrimitiveTypes.STRING -> PropertyType.String
        SPrimitiveTypes.BOOLEAN -> PropertyType.Bool
        SPrimitiveTypes.INTEGER -> PropertyType.Int
        is SEnumerationAdapter -> PropertyType.Enum
        else -> PropertyType.Other
    }

fun SProperty.getValue(node: SNode): String? =
    when (this.type) {
        SPrimitiveTypes.BOOLEAN -> SPropertyOperations.getBoolean(node, this).toString()
        SPrimitiveTypes.INTEGER -> SPropertyOperations.getInteger(node, this).toString()
        else -> SPropertyOperations.getString(node, this)
    }


fun SNode.serialize(): Node {
    return Node(
        id = this.nodeId.toString(),
        concept = (this.concept as SConceptAdapterById).serialize(),
        properties = this.properties.map {
            Property(
                id = (it as SPropertyAdapterById).serialize(),
                value = it.getValue(this),
                type = it.convertType()
            )
        },
        children = this.children.map {
            Child(
                (it.containmentLink as SContainmentLinkAdapterById).serialize(),
                it.serialize()
            )
        },
        references = this.references.filterIsInstance(StaticReference::class.java)
            .map {
                Reference(
                    (it.link as SReferenceLinkAdapterById).serialize(),
                    SNodePointer.serialize(it.targetNodeReference)
                )
            }
    )
}
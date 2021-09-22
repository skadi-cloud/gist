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
import jetbrains.mps.extapi.model.EditableSModelBase
import jetbrains.mps.kernel.model.MissingDependenciesFixer
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SPropertyOperations
import jetbrains.mps.smodel.SModelUtil_new
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
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
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
            it.append(PARAMETER_DEVICE_NAME, InetAddress.getLocalHost().hostName)
            it.append(PARAMETER_CALLBACK, url.toString())
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
            var nodeName: String? = null
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
    var isRoot = false

    var usedLanguages: List<SLanguage> = emptyList()
    var imports: List<SModelReference> = emptyList()

    node.model!!.repository.modelAccess.runReadAction {
        serializedNode = node.serialize()
        val descendants: MutableList<SNode> = SNodeOperations.getNodeDescendants(node, null, true)
        usedLanguages = descendants.map { it.concept.language }.distinct()
        imports = descendants.flatMap { it.references.mapNotNull { reference -> reference.targetSModelReference } }
            .distinct()
        isRoot = node.containingRoot == node
    }

    return AST(
        imports.map { Import(it.modelName, it.modelId.toString(), it.toString()) },
        usedLanguages.map { UsedLanguage(it.qualifiedName, (it as SLanguageAdapterById).serialize()) },
        serializedNode!!,
        isRoot
    )
}

/***
 * Imports the AST into the model and adds the imported models and used languages.
 * This method needs to be called inside a write action. The root node is attached
 * to the model after is being parsed to avoid exessive notifications while creating
 * the nodes.
 */
fun AST.importInto(model: SModel) {
    val node = this.root.toSNode(model.reference)
    model.addRootNode(node)
    if (model is EditableSModelBase) {
        this.imports.forEach { import ->
            model.addModelImport(PersistenceFacade.getInstance().createModelReference(import.reference))
        }
        this.usedLanguage.forEach { usedLanguage ->
            model.addLanguage(SLanguageAdapterById.deserialize(usedLanguage.id))
        }
        MissingDependenciesFixer(model).fixModuleDependencies()
    }
}

/***
 * Converts the AST node to a SNode but doesn't attach it to any model. Model reference is
 * used to keep local references.
 * Needs to be called in a read action.
 */
fun Node.toSNode(targetModel: SModelReference?): SNode {
    val children = this.children.map { child -> child.containmentLinkId to child.node.toSNode(targetModel) }
    /* We don't set the model here because it might in the repository and will send change notifications
    *
    *  Using the node id from the original model could cause a conflict because there is guarantee that
    *  it is unique globally. NodeId doesn't have to good entropy since it's only once initialised with a random value
    *  and the only incremented. Ideally we check if the node id is taken by trying to get a node with its
    *  id. If there is a node with that id already we woudl generate a new one. We would need stora a mapping
    *  to update the references to that node by replacing the node pointer of the SReference.
    *  Since the scope of this method is a "root" in the model we get from the server we would need to keep that
    *  mapping for all "roots" and then have a second phase that sets the references correctly with updated node ids.
    * */

    //method is deprecated but replacement calls into in behaviour to init the node which
    //is not what we want. Executing the constucutor might already create a sub structure
    //that we don't need. e.g. for ClassConcept it would already create a visiblity.
    //After deserialising from JSON we would then add another visiblilty hence violating the
    // model constrains.
    // FIXME: find a way to instantiate a node without initilizing it that isn't deprecated.
    val sNode = SModelUtil_new.instantiateConceptDeclaration(
        SConceptAdapterById.deserialize(this.concept),
        null,
        PersistenceFacade.getInstance().createNodeId(this.id),
        false
    )


    children.forEach { (id, child) ->
        sNode.addChild(SContainmentLinkAdapterById.deserialize(id), child)
    }

    this.properties.forEach {
        val sPropertyAdapterById = SPropertyAdapterById.deserialize(it.id)
        val value = it.value ?: return@forEach
        when (it.type) {
            PropertyType.Bool -> SPropertyOperations.set(sNode, sPropertyAdapterById, value.toBoolean())
            PropertyType.Int -> SPropertyOperations.set(sNode, sPropertyAdapterById, value.toInt())
            else -> SPropertyOperations.set(sNode, sPropertyAdapterById, value)
        }
    }

    this.references.forEach {
        val pointer = if (it.isLocal && targetModel != null)
            SNodePointer.deserialize(it.targetNodeReference).run { SNodePointer(targetModel, nodeId) }
        else
            SNodePointer.deserialize(it.targetNodeReference)

        val referenceId = SReferenceLinkAdapterById.deserialize(it.referenceId)

        /**
         * FIXME: use new API in 2021.1+: sNode.setReference(SReferenceLinkAdapterById.deserialize(it.referenceId), pointer)
         */

        sNode.setReference(
            referenceId,
            StaticReference(referenceId, sNode, pointer.modelReference, pointer.nodeId, it.resolveInfo)
        )
    }
    return sNode
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
                    SNodePointer.serialize(it.targetNodeReference),
                    it.targetNodeReference.modelReference == this.model?.reference,
                    resolveInfo = it.resolveInfo
                )
            }
    )
}
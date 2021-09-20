package cloud.skadi.gist.shared

data class UsedLanguage(val name: String, val id: String)
data class Import(val name: String, val id: String, val reference: String)

enum class PropertyType {
    String, Int, Bool, Enum, Other
}

data class Property(val value: String?, val id: String, val type: PropertyType?)
data class Child(val containmentLinkId: String, val node: Node)
data class Reference(val referenceId: String, val targetNodeReference: String, val isLocal: Boolean = false)
data class Node(
    val id: String,
    val concept: String,
    val properties: List<Property>,
    val children: List<Child>,
    val references: List<Reference>
)

data class AST(val imports: List<Import>, val usedLanguage: List<UsedLanguage>, val root: Node)

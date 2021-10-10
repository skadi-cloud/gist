package cloud.skadi.gist

fun getEnvOfFail(env: String): String {
    return System.getenv(env) ?: throw IllegalArgumentException("missing $env")
}

fun getEnvOrDefault(env: String, default: String): String {
    val value = System.getenv(env) ?: return default
    return value.ifEmpty {
        default
    }
}

fun <T> getEnvOrDefault(env: String, default: String, block: (String) -> T): T {
    val value = System.getenv(env) ?: return block(default)
    return block(value.ifEmpty {
        default
    })
}
fun getEnvOfFail(env: String): String {
    return System.getenv(env) ?: throw IllegalArgumentException("missing $env")
}

fun getEnvOrDefault(env: String, default: String): String {
    val value = System.getenv(env) ?: return default
    if (value.isEmpty()) {
        return default
    } else {
        return value
    }
}
package cloud.skadi.gist.shared

// shared parameter names between the backend and the ide plugin
// changing parameter names will break existing ide plugins

const val PARAMETER_CALLBACK = "callback"
const val PARAMETER_DEVICE_NAME = "device-name"
const val PARAMETER_CSRF_TOKEN = "csfr-token"
const val PARAMETER_DEVICE_TOKEN = "device-token"
const val PARAMETER_TEMPORARY_TOKEN = "device-token"
const val PARAMETER_USER_NAME = "user"

const val HEADER_SKADI_TOKEN = "X-SKADI-GIST-TOKEN"

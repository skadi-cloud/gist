package cloud.skadi.gist.mps.plugin

import org.jetbrains.mps.openapi.module.ModelAccess

fun <T> ModelAccess.calculateReader(block : () -> T): T? {
    var result: T? = null
    this.runReadAction {
        result = block()
    }
    return result
}
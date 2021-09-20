package cloud.skadi.gist.plugins

import io.micrometer.prometheus.*
import io.ktor.metrics.micrometer.*
import io.ktor.application.*

fun Application.configureMonitoring() {

    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        // ...
    }
}

package cloud.skadi.gist.data

import cloud.skadi.gist.views.templates.OpenGraphData
import cloud.skadi.gist.views.templates.OpenGraphType
import cloud.skadi.gist.views.templates.TwitterCard
import cloud.skadi.gist.views.templates.TwitterCardType
import java.net.URL

fun Gist.toTwitterCard(previewUrl: String) =
    TwitterCard(
        card = TwitterCardType.`Summary Card with Large Image`,
        title = this.name,
        description = this.description,
        image = previewUrl
    )

fun Gist.toOg(previewUrl: String, ownUrl: String) =
    OpenGraphData(
        type = OpenGraphType.Article,
        title = this.name,
        description = this.description,
        url = ownUrl,
        image = previewUrl,
        siteName = "Skadi Cloud"
    )
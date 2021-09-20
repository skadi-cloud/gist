package cloud.skadi.gist

import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.emoji.EmojiExtension
import com.vladsch.flexmark.ext.emoji.EmojiImageType
import com.vladsch.flexmark.ext.emoji.EmojiShortcutType
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.util.misc.Extension
import java.util.*


fun markdownToHtml(mardown: String): String {
    val options: MutableDataSet = MutableDataSet().set(
        Parser.EXTENSIONS, listOf(
            AutolinkExtension.create(),
            EmojiExtension.create(),
            StrikethroughExtension.create(),
            TaskListExtension.create(),
            TablesExtension.create()
        ) as Collection<Extension>
    ) // set GitHub table parsing options
        .set(TablesExtension.WITH_CAPTION, false)
        .set(TablesExtension.COLUMN_SPANS, false)
        .set(TablesExtension.MIN_HEADER_ROWS, 1)
        .set(TablesExtension.MAX_HEADER_ROWS, 1)
        .set(TablesExtension.APPEND_MISSING_COLUMNS, true)
        .set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
        .set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
        .set(EmojiExtension.USE_SHORTCUT_TYPE, EmojiShortcutType.GITHUB)
        .set(EmojiExtension.USE_IMAGE_TYPE, EmojiImageType.IMAGE_ONLY) // other options
        .set(HtmlRenderer.ESCAPE_HTML, true)

    options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");

    val parser: Parser = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(options).build()

    val document: Node = parser.parse(mardown)
    return renderer.render(document)
}
package dev.aura.auradroid.ui.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Markdown rendering for the chat, done by hand.
 *
 * A coding agent's replies are almost all prose with a little structure —
 * headers, lists, inline `code`, the occasional link. Pulling in a full
 * markdown library (and its theme) to render that would be heavier than the
 * feature, and would fight the Aura colour scheme. This covers the subset an
 * agent actually produces; fenced code blocks are handled separately as
 * saveable cards, so they are intentionally not parsed here.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val baseColor = MaterialTheme.colorScheme.onSurface
    val codeColor = MaterialTheme.colorScheme.primary
    val linkColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    val blocks = remember(text) { splitBlocks(text) }

    SelectionContainer(modifier = modifier) {
        Column {
            for (block in blocks) {
                RenderBlock(block, baseColor, codeColor, linkColor, mutedColor)
                Spacer(Modifier.height(blockSpacing(block)))
            }
        }
    }
}

/**
 * A vertical stretch of one kind: a heading line, a run of list items, a
 * quote, or a plain paragraph. Splitting on blank lines keeps list items and
 * paragraphs contiguous instead of breaking a list into one-item blocks.
 */
private sealed class Block {
    data class Heading(val level: Int, val text: String) : Block()
    data class ListBlock(val ordered: Boolean, val items: List<String>) : Block()
    data class Quote(val text: String) : Block()
    data class Code(val content: String) : Block()
    data class Paragraph(val text: String) : Block()
}

private fun splitBlocks(src: String): List<Block> {
    val out = mutableListOf<Block>()
    val pendingList = mutableListOf<Pair<Boolean, String>>() // ordered, text
    val pendingParagraph = StringBuilder()

    fun flushParagraph() {
        if (pendingParagraph.isNotBlank()) {
            out.add(Block.Paragraph(pendingParagraph.toString().trim()))
            pendingParagraph.setLength(0)
        }
    }

    fun flushList() {
        if (pendingList.isNotEmpty()) {
            val ordered = pendingList.all { it.first }
            out.add(
                if (ordered) Block.ListBlock(ordered = true, items = pendingList.map { it.second })
                else Block.ListBlock(ordered = false, items = pendingList.map { it.second })
            )
            pendingList.clear()
        }
    }

    fun flushBoth() {
        flushParagraph()
        flushList()
    }

    for (raw in src.lineSequence()) {
        val line = raw.trimEnd()
        when {
            line.isBlank() -> flushBoth()

            line.startsWith("```") -> {
                flushBoth()
                // Fenced code is normally already pulled out as saveable cards
                // before this runs; if any reaches here, show it as a plain
                // monospace block rather than swallowing it.
                out.add(Block.Code(line.removePrefix("```")))
            }

            HEADING.matches(line) -> {
                flushBoth()
                val hashes = line.takeWhile { it == '#' }.length
                out.add(Block.Heading(minOf(hashes, 6), line.drop(hashes).trim()))
            }

            QUOTE.matches(line) -> {
                flushBoth()
                out.add(Block.Quote(line.removePrefix(">").trim()))
            }

            ULIST.matches(line) -> {
                flushParagraph()
                pendingList.add(false to line.removePrefix("-").removePrefix("*").trimStart())
            }

            OLIST.matches(line) -> {
                flushParagraph()
                pendingList.add(true to line.substringAfter('.').trimStart())
            }

            else -> {
                flushList()
                if (pendingParagraph.isNotEmpty()) pendingParagraph.append(' ')
                pendingParagraph.append(line.trim())
            }
        }
    }
    flushBoth()
    return out
}

private val HEADING = Regex("^#{1,6}\\s+\\S.*")
private val QUOTE = Regex("^>\\s?\\S.*")
private val ULIST = Regex("^[-*]\\s+\\S.*")
private val OLIST = Regex("^\\d+\\.\\s+\\S.*")

@Composable
private fun RenderBlock(
    block: Block,
    baseColor: androidx.compose.ui.graphics.Color,
    codeColor: androidx.compose.ui.graphics.Color,
    linkColor: androidx.compose.ui.graphics.Color,
    mutedColor: androidx.compose.ui.graphics.Color,
) {
    val base = MaterialTheme.typography.bodyMedium
    when (block) {
        is Block.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            Text(
                inlineMarkdown(block.text, base.color),
                style = style.copy(fontWeight = FontWeight.Bold),
                color = baseColor,
            )
        }

        is Block.Paragraph -> {
            Text(
                inlineMarkdown(block.text, base.color),
                style = base,
                color = baseColor,
            )
        }

        is Block.Quote -> {
            Text(
                inlineMarkdown(block.text, base.color),
                style = base.copy(fontStyle = FontStyle.Italic),
                color = mutedColor,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        is Block.Code -> {
            Text(
                block.content,
                style = base.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                color = mutedColor,
            )
        }

        is Block.ListBlock -> {
            Column {
                block.items.forEachIndexed { i, item ->
                    val marker = if (block.ordered) "${i + 1}." else "•"
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Text(
                            marker,
                            style = base,
                            color = codeColor,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            inlineMarkdown(item, base.color),
                            style = base,
                            color = baseColor,
                        )
                    }
                }
            }
        }
    }
}

/** Space after a block; tighter inside lists, more around headings. */
@Composable
private fun blockSpacing(block: Block): androidx.compose.ui.unit.Dp = when (block) {
    is Block.Heading -> 8.dp
    is Block.ListBlock -> 4.dp
    else -> 8.dp
}

/**
 * The inline-only rules: **bold**, *italic*, `code`, [text](url), ~~strike~~.
 *
 * Runs left-to-right, consuming the next marker it sees. Overlapping markers
 * (e.g. `**a *b* c**`) are not common in agent output and not handled; the
 * common cases — a single bold phrase, a backticked identifier, a link — are.
 */
private fun inlineMarkdown(
    source: String,
    baseColor: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    val bold = Regex("\\*\\*(.+?)\\*\\*")
    val italic = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
    val strike = Regex("~~(.+?)~~")
    val code = Regex("`([^`]+)`")
    val link = Regex("\\[([^]]+)]\\(([^)]+)\\)")
    // Ordered so the greedier two-char markers win over the single-char ones.
    val all = listOf(bold, code, strike, link, italic)

    return buildAnnotatedString {
        var i = 0
        while (i < source.length) {
            val next = all.asSequence()
                .mapNotNull { rx ->
                    rx.find(source, i)?.let { it to matchStyle(it) }
                }
                .minByOrNull { it.first.range.first }
                ?: break

            append(source, i, next.first.range.first)
            applyMatch(next.first, next.second, baseColor)
            i = next.first.range.last + 1
        }
        if (i < source.length) append(source, i, source.length)
    }
}

/** Which SpanStyle a given matched regex should paint, if any. */
private fun matchStyle(m: MatchResult): SpanStyle? = when {
    m.value.startsWith("**") -> SpanStyle(fontWeight = FontWeight.Bold)
    m.value.startsWith("`") -> SpanStyle(fontFamily = FontFamily.Monospace)
    m.value.startsWith("~~") -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    m.value.startsWith("*") -> SpanStyle(fontStyle = FontStyle.Italic)
    else -> null // links are applied with their own colour in applyMatch
}

private fun AnnotatedString.Builder.applyMatch(
    m: MatchResult,
    style: SpanStyle?,
    baseColor: androidx.compose.ui.graphics.Color,
) {
    when {
        m.value.startsWith("[") -> {
            val text = m.groupValues[1]
            val url = m.groupValues[2]
            withLink(LinkAnnotation.Url(url)) {
                withStyle(SpanStyle(color = baseColor, textDecoration = TextDecoration.Underline)) {
                    append(text)
                }
            }
        }
        style != null -> withStyle(style) { append(m.groupValues[1]) }
        else -> append(m.value)
    }
}

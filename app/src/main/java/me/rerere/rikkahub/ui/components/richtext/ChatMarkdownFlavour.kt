package me.rerere.rikkahub.ui.components.richtext

import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.TokensCache
import org.intellij.markdown.parser.sequentialparsers.impl.MathParser

/** Keep GFM's code/escape handling, but do not pair the dollars introducing two prices. */
internal class ChatMarkdownFlavour : GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true) {
    private val gfmParsers = super.sequentialParserManager.getParserSequence()
    override val sequentialParserManager = object : SequentialParserManager() {
        override fun getParserSequence(): List<SequentialParser> = gfmParsers.map {
            if (it is MathParser) ChatMathParser() else it
        }
    }
}

private class ChatMathParser : SequentialParser {
    override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>): SequentialParser.ParsingResult {
        val result = SequentialParser.ParsingResultBuilder()
        val remaining = RangesListBuilder()
        var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)
        val source = tokens.originalText
        while (iterator.type != null) {
            val size = iterator.length
            if (iterator.type == GFMTokenTypes.DOLLAR &&
                (size != 1 || source.getOrNull(iterator.end)?.isWhitespace() == false)
            ) {
                var closing = iterator.advance()
                while (closing.type != null && !(closing.type == GFMTokenTypes.DOLLAR && closing.length == size)) {
                    if (size == 1 && closing.type == org.intellij.markdown.MarkdownTokenTypes.EOL) break
                    closing = closing.advance()
                }
                if (closing.type == GFMTokenTypes.DOLLAR && closing.length == size &&
                    (size != 1 || (source.getOrNull(closing.start - 1)?.isWhitespace() == false &&
                        source.getOrNull(closing.end)?.isDigit() != true &&
                        !source.subSequence(iterator.end, closing.start).contains('\n')))
                ) {
                    result.withNode(SequentialParser.Node(iterator.index..(closing.index + 1),
                        if (size == 1) GFMElementTypes.INLINE_MATH else GFMElementTypes.BLOCK_MATH))
                    iterator = closing.advance()
                    continue
                }
            }
            // An invalid pair stays available for emphasis/link parsing. Its would-be closing
            // dollar may open the next formula, e.g. "Costs $5, and $x + y$ is an equation".
            remaining.put(iterator.index)
            iterator = iterator.advance()
        }
        return result.withFurtherProcessing(remaining.get())
    }
}

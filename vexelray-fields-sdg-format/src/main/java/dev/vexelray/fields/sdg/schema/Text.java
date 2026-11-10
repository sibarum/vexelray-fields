package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

import java.util.List;

/**
 * A text layer's glyphs (SDG 1.3): glyph {@code glyphs[i]} of font {@code font}, its pen on the canvas at
 * {@code (x[i], y[i])}, {@code size} units an em. The three lists are as long as each other. {@code string} is the text
 * they spell, for search, copying and accessibility, absent if unknown; a reader does not draw from it.
 */
@IntrabufMessage(name = "vexelray.sdg.Text", compat = Compat.FULL)
public record Text(int font, double size,
                   @Field(nonNull = true) List<Integer> glyphs,
                   @Field(nonNull = true) List<Double> x,
                   @Field(nonNull = true) List<Double> y,
                   String string) {
}

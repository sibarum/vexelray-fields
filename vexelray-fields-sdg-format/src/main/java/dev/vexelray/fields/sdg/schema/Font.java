package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

import java.util.List;

/**
 * A font a drawing's text is set in (SDG 1.3): the glyphs it uses, each a field on the drawing's pages, and the font's
 * metrics in ems.
 *
 * @param family     the family's name, as {@code Noto Sans}
 * @param style      the face's, as {@code Regular}
 * @param ascender   ems above the baseline, for an editor laying text out again
 * @param descender  ems below it, negative
 * @param lineHeight ems from one baseline to the next
 * @param glyphs     numbered from 0, as text layers name them
 * @param license    the font's copyright and license, carried with its glyphs; absent if it named none
 */
@IntrabufMessage(name = "vexelray.sdg.Font", compat = Compat.FULL)
public record Font(@Field(nonNull = true) String family, @Field(nonNull = true) String style,
                   double ascender, double descender, double lineHeight,
                   @Field(nonNull = true) List<Glyph> glyphs,
                   String license) {
}

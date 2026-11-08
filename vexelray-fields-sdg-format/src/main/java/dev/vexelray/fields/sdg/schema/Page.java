package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

/**
 * A page of texels as a PNG, the same bytes the XML form carries, so a compiled file's pages are pictures too and
 * there is one way to pack them. Written as 8-bit RGBA; read in any 8-bit colour type, not interlaced.
 */
@IntrabufMessage(name = "vexelray.sdg.Page", compat = Compat.FULL)
public record Page(@Field(nonNull = true) byte[] png) {
}

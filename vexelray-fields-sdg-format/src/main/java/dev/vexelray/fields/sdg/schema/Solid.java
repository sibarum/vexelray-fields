package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

/**
 * One colour.
 *
 * @param rgba straight RGBA8, {@code 0xRRGGBBAA}
 * @param role the theme's colour to draw instead, or null for none; since 1.1
 */
@IntrabufMessage(name = "vexelray.sdg.Solid", minor = 1, compat = Compat.FULL)
public record Solid(@Field(fixed = true) int rgba, @Field(since = 1) String role) {
}

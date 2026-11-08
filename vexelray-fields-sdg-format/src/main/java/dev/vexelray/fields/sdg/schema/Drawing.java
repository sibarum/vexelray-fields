package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

import java.util.List;

/**
 * A whole drawing, the root of an {@code .sdgb} file. Its major and minor are the file's version, SDG's own
 * ({@code Sdg.MAJOR}, {@code Sdg.MINOR}), which a test holds equal; a reader refuses a newer major.
 *
 * @param width  the canvas across, in units
 * @param height the canvas down, in units
 * @param pages  the texels, numbered from 0
 * @param layers back to front
 */
@IntrabufMessage(name = "vexelray.sdg.Drawing", major = 1, minor = 1, compat = Compat.FULL)
public record Drawing(double width, double height,
                      @Field(nonNull = true) List<Page> pages,
                      @Field(nonNull = true) List<Layer> layers) {
}

package dev.vexelray.fields.sdg.past;

import dev.vexelray.fields.sdg.schema.Page;
import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

import java.util.List;

/** The drawing as a 1.0 writer had it, before solid paints had roles. */
@IntrabufMessage(name = "vexelray.sdg.Drawing", major = 1, minor = 0, compat = Compat.FULL)
public record PastDrawing(double width, double height,
                          @Field(nonNull = true) List<Page> pages,
                          @Field(nonNull = true) List<PastLayer> layers) {
}

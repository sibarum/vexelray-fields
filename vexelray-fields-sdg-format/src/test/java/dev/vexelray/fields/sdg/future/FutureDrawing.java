package dev.vexelray.fields.sdg.future;

import dev.vexelray.fields.sdg.schema.Page;
import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

import java.util.List;

/** The drawing as a later writer might have it: a field on the end, and layers grown too. */
@IntrabufMessage(name = "vexelray.sdg.Drawing", major = 1, minor = 2, compat = Compat.FULL)
public record FutureDrawing(double width, double height,
                            @Field(nonNull = true) List<Page> pages,
                            @Field(nonNull = true) List<FutureLayer> layers,
                            @Field(since = 2) String author) {
}

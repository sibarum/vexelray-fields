package dev.vexelray.fields.sdg.future;

import dev.vexelray.fields.sdg.schema.Page;
import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

import java.util.List;

/** The drawing as a later writer might have it: 1.3's fonts at 3, a field of its own at 4, and layers grown too. */
@IntrabufMessage(name = "vexelray.sdg.Drawing", major = 1, minor = 4, compat = Compat.FULL)
public record FutureDrawing(double width, double height,
                            @Field(nonNull = true) List<Page> pages,
                            @Field(nonNull = true) List<FutureLayer> layers,
                            @Field(since = 3) List<FutureFont> fonts,
                            @Field(since = 4) String author) {
}

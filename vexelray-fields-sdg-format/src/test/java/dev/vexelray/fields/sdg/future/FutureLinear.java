package dev.vexelray.fields.sdg.future;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

import java.util.List;

@IntrabufMessage(name = "vexelray.sdg.Linear", compat = Compat.FULL)
public record FutureLinear(double x0, double y0, double x1, double y1,
                           @Field(nonNull = true) List<Integer> stops) {
}

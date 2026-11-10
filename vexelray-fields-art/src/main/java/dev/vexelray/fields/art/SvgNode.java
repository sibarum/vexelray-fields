package dev.vexelray.fields.art;

/**
 * What a drawing is made of, as {@link Svg#tree} gives it: a {@link SvgPart}, one fill or stroke; a {@link SvgText}, text
 * not yet set in a font; or a {@link SvgGroup},
 * parts composited apart and laid over once with an opacity, a clip or a mask.
 */
public sealed interface SvgNode permits SvgPart, SvgGroup, SvgText {
}

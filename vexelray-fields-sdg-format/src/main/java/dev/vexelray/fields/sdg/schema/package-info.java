/**
 * The intrabuf schema of an {@code .sdgb} file: the drawing as {@code docs/sdg.md} §4.2 lists it, one record per
 * message. These records are the schema, and the generated {@code *Intrabuf} codecs read and write them;
 * {@link dev.vexelray.fields.sdg.SdgFile} turns them into {@link dev.vexelray.fields.sdg.Sdg} and back.
 *
 * <p><b>They only ever grow.</b> Every message is intrabuf FULL: a field is added at the end with
 * {@code @Field(since = n)} and its message's minor bumped, never removed, reordered or retyped. So a newer reader
 * opens an older file, the new fields at their defaults, and an older reader opens a newer one, skipping fields it does
 * not know. A new kind of stencil or paint is a name in {@code kind}; a new paint's own fields go in a new message on
 * the end of {@link Paint}, which an older reader skips, reading the paint as one it does not know.
 */
package dev.vexelray.fields.sdg.schema;

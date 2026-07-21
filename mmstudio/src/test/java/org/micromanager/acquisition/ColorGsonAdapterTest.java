package org.micromanager.acquisition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Color;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that {@link ColorGsonAdapter} is behavior-preserving relative to the
 * pre-existing (reflective) Gson serialization of {@link Color}.
 *
 * <p>Background: before this adapter, ChannelSpec/SequenceSettings serialized
 * their {@code Color} field via Gson's reflective fallback, which reaches into
 * Color's private fields. The Java module system blocks that on JDK 17+ unless
 * launched with {@code --add-opens java.desktop/java.awt} (and java.awt.color).
 * The adapter removes that reflection so those add-opens flags can be dropped.
 *
 * <p>CONCLUSION (verified here and by a codebase audit):
 * <ul>
 *   <li>NOT byte-for-byte identical JSON. The old reflective output also
 *       emitted Color's derived float fields ({@code falpha}, and for
 *       float-constructed colors {@code frgbvalue}/{@code fvalue}); the adapter
 *       emits only {@code {"value": <packed ARGB int>}}.</li>
 *   <li>BEHAVIOR-PRESERVING for every real usage. The packed ARGB {@code value}
 *       - the only semantically meaningful field, and all any consumer of
 *       ChannelSpec.color() reads (via getRGB() or Swing) - round-trips
 *       identically.</li>
 *   <li>FILE-COMPATIBLE BOTH WAYS. Legacy JSON (with the extra float fields)
 *       reads correctly through the adapter, and adapter-written JSON reads
 *       correctly through the old reflective reader, because the "value" key
 *       matches Color's own field name and the absent float fields are ignored.</li>
 *   <li>ONLY LATENT DIFFERENCE: a float-constructed Color's sub-8-bit float
 *       precision (frgbvalue/fvalue) is not preserved. No code path in this repo
 *       reads those back off a deserialized ChannelSpec color (audited: the only
 *       getColorComponents/getRGBColorComponents calls are in ColorPalettes on
 *       locally-built colors and in PropertyMapJSONSerializer, a separate
 *       non-Gson path), so this is unobservable here.</li>
 * </ul>
 */
public class ColorGsonAdapterTest {

   // The old, pre-adapter behavior: plain reflective Gson.
   private static final Gson REFLECTIVE = new Gson();

   // The new behavior: Gson with the ColorGsonAdapter registered (matches how
   // ChannelSpec/SequenceSettings build their Gson instances).
   private static final Gson ADAPTED = new GsonBuilder()
         .registerTypeAdapter(Color.class, new ColorGsonAdapter())
         .create();

   // Color occurs as a field inside ChannelSpec, never as a top-level value, so
   // exercise it in field context.
   private static final class Holder {
      private Color c;

      private Holder(Color c) {
         this.c = c;
      }
   }

   private static Color[] sampleColors() {
      return new Color[] {
            Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.BLACK, Color.GRAY,
            new Color(12, 34, 56),
            new Color(1, 2, 3, 4),                // with alpha
            new Color(0x12345678, true),          // ARGB, non-opaque
            new Color(0x00abcdef, true),          // zero alpha
            new Color(0.25f, 0.5f, 0.75f),        // float-constructed (frgbvalue populated)
            new Color(0.1f, 0.2f, 0.3f, 0.4f),    // float-constructed with alpha
      };
   }

   /** Adapter output is exactly {"value": <getRGB()>}, and round-trips its ARGB. */
   @Test
   public void adapterRoundTripsArgb() {
      for (Color c : sampleColors()) {
         String json = ADAPTED.toJson(new Holder(c));
         Assert.assertEquals("{\"c\":{\"value\":" + c.getRGB() + "}}", json);
         Holder back = ADAPTED.fromJson(json, Holder.class);
         Assert.assertEquals(c.getRGB(), back.c.getRGB());
      }
   }

   /** Legacy (reflective) JSON still deserializes correctly through the adapter. */
   @Test
   public void adapterReadsLegacyJson() {
      for (Color c : sampleColors()) {
         String legacyJson = REFLECTIVE.toJson(new Holder(c));
         Holder back = ADAPTED.fromJson(legacyJson, Holder.class);
         Assert.assertEquals(c.getRGB(), back.c.getRGB());
      }
   }

   /** Adapter-written JSON still deserializes correctly through the old reader. */
   @Test
   public void oldReaderReadsAdapterJson() {
      for (Color c : sampleColors()) {
         String adapterJson = ADAPTED.toJson(new Holder(c));
         Holder back = REFLECTIVE.fromJson(adapterJson, Holder.class);
         Assert.assertEquals(c.getRGB(), back.c.getRGB());
      }
   }

   /** End-to-end: ChannelSpec's public JSON API preserves the channel color. */
   @Test
   public void channelSpecJsonPreservesColor() {
      for (Color c : sampleColors()) {
         ChannelSpec cs = new ChannelSpec.Builder().color(c).build();
         ChannelSpec back = ChannelSpec.fromJSONStream(ChannelSpec.toJSONStream(cs));
         Assert.assertEquals(c.getRGB(), back.color().getRGB());
      }
   }
}

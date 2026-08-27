package org.micromanager.display.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.ChannelDisplaySettings;

/**
 * Tests persistence of the float histogram axis range on ChannelDisplaySettings.
 *
 * <p>Float images have no bit depth to derive a histogram axis from, so the axis range is
 * recorded per channel and must survive closing and reopening a dataset.
 */
public class DefaultChannelDisplaySettingsFloatRangeTest {

   private static final double DELTA = 1e-9;

   @Test
   public void testDefaultHasNoFloatHistoRange() {
      ChannelDisplaySettings s = ChannelDisplaySettings.builder().build();
      assertFalse(s.hasFloatHistoRange());
      assertFalse(s.isFloatHistoRangePinned());
   }

   @Test
   public void testSetFloatHistoRange() {
      ChannelDisplaySettings s = ChannelDisplaySettings.builder()
            .floatHistoRange(-0.6484375, 1.0390625)
            .floatHistoRangePinned(true)
            .build();
      assertTrue(s.hasFloatHistoRange());
      assertEquals(-0.6484375, s.getFloatHistoRangeMinimum(), DELTA);
      assertEquals(1.0390625, s.getFloatHistoRangeMaximum(), DELTA);
      assertTrue(s.isFloatHistoRangePinned());
   }

   @Test
   public void testInvertedRangeIsNotUsable() {
      ChannelDisplaySettings s = ChannelDisplaySettings.builder()
            .floatHistoRange(5.0, 1.0)
            .build();
      assertFalse(s.hasFloatHistoRange());
   }

   @Test
   public void testCopyBuilderPreservesRange() {
      ChannelDisplaySettings s = ChannelDisplaySettings.builder()
            .floatHistoRange(-2.0, 3.0)
            .floatHistoRangePinned(true)
            .build()
            .copyBuilder()
            .build();
      assertTrue(s.hasFloatHistoRange());
      assertEquals(-2.0, s.getFloatHistoRangeMinimum(), DELTA);
      assertEquals(3.0, s.getFloatHistoRangeMaximum(), DELTA);
      assertTrue(s.isFloatHistoRangePinned());
   }

   /** The NaN sentinel must compare equal to itself or CAS loops never converge. */
   @Test
   public void testUnsetRangesAreEqual() {
      ChannelDisplaySettings a = ChannelDisplaySettings.builder().build();
      ChannelDisplaySettings b = ChannelDisplaySettings.builder().build();
      assertTrue(a.equals(b));
      assertEquals(a.hashCode(), b.hashCode());
   }

   @Test
   public void testEqualsDistinguishesRangeAndPin() {
      ChannelDisplaySettings a = ChannelDisplaySettings.builder()
            .floatHistoRange(0.0, 1.0).build();
      ChannelDisplaySettings b = ChannelDisplaySettings.builder()
            .floatHistoRange(0.0, 2.0).build();
      ChannelDisplaySettings c = ChannelDisplaySettings.builder()
            .floatHistoRange(0.0, 1.0).floatHistoRangePinned(true).build();
      assertFalse(a.equals(b));
      assertFalse(a.equals(c));
   }

   @Test
   public void testPropertyMapRoundTrip() {
      ChannelDisplaySettings orig = ChannelDisplaySettings.builder()
            .floatHistoRange(-0.6484375, 1.0390625)
            .floatHistoRangePinned(true)
            .build();
      PropertyMap pmap = ((DefaultChannelDisplaySettings) orig).toPropertyMap();
      ChannelDisplaySettings read =
            DefaultChannelDisplaySettings.fromPropertyMap(pmap);
      assertTrue(read.hasFloatHistoRange());
      assertEquals(-0.6484375, read.getFloatHistoRangeMinimum(), DELTA);
      assertEquals(1.0390625, read.getFloatHistoRangeMaximum(), DELTA);
      assertTrue(read.isFloatHistoRangePinned());
   }

   @Test
   public void testUnpinnedRangeRoundTrips() {
      ChannelDisplaySettings orig = ChannelDisplaySettings.builder()
            .floatHistoRange(-9.0, 25.0)
            .floatHistoRangePinned(false)
            .build();
      PropertyMap pmap = ((DefaultChannelDisplaySettings) orig).toPropertyMap();
      ChannelDisplaySettings read =
            DefaultChannelDisplaySettings.fromPropertyMap(pmap);
      assertTrue(read.hasFloatHistoRange());
      assertFalse(read.isFloatHistoRangePinned());
   }

   /** Settings for integer data must not write the float keys at all. */
   @Test
   public void testUnsetRangeIsNotWritten() {
      ChannelDisplaySettings orig = ChannelDisplaySettings.builder().build();
      PropertyMap pmap = ((DefaultChannelDisplaySettings) orig).toPropertyMap();
      assertFalse(pmap.containsKey(PropertyKey.FLOAT_HISTO_RANGE_MIN.key()));
      assertFalse(pmap.containsKey(PropertyKey.FLOAT_HISTO_RANGE_MAX.key()));
      assertFalse(pmap.containsKey(PropertyKey.FLOAT_HISTO_RANGE_PINNED.key()));
   }

   /** Files written before this existed must still load. */
   @Test
   public void testLegacyPropertyMapWithoutFloatKeys() {
      PropertyMap legacy = PropertyMaps.builder()
            .putInteger(PropertyKey.HISTOGRAM_BIT_DEPTH.key(), 12)
            .putBoolean(PropertyKey.USE_CAMERA_BIT_DEPTH.key(), false)
            .build();
      ChannelDisplaySettings read =
            DefaultChannelDisplaySettings.fromPropertyMap(legacy);
      assertFalse(read.hasFloatHistoRange());
      assertFalse(read.isFloatHistoRangePinned());
      assertEquals(12, read.getHistoRangeBits());
   }
}

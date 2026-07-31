package org.micromanager.display.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.ComponentDisplaySettings;

/**
 * Tests the floating point scaling range of ComponentDisplaySettings.
 *
 * <p>Float images store their scaling range as actual pixel values rather than histogram
 * bin indices, so that the range keeps its meaning across images with different intensity
 * distributions.
 */
public class DefaultComponentDisplaySettingsFloatTest {

   private static final double DELTA = 1e-9;

   // --- Defaults ---

   @Test
   public void testDefaultHasNoFloatScaling() {
      ComponentDisplaySettings s = ComponentDisplaySettings.builder().build();
      assertFalse(s.hasFloatScaling());
   }

   @Test
   public void testDefaultLongScalingUnaffected() {
      ComponentDisplaySettings s = ComponentDisplaySettings.builder().build();
      assertEquals(0L, s.getScalingMinimum());
      assertEquals(Long.MAX_VALUE, s.getScalingMaximum());
   }

   // --- Setting the float range ---

   @Test
   public void testSetFloatRange() {
      ComponentDisplaySettings s = ComponentDisplaySettings.builder()
            .scalingRangeDouble(-2.5, 17.25)
            .build();
      assertTrue(s.hasFloatScaling());
      assertEquals(-2.5, s.getScalingMinimumDouble(), DELTA);
      assertEquals(17.25, s.getScalingMaximumDouble(), DELTA);
   }

   @Test
   public void testOnlyOneBoundSetIsNotFloatScaling() {
      ComponentDisplaySettings s = ComponentDisplaySettings.builder()
            .scalingMinimumDouble(1.0)
            .build();
      assertFalse(s.hasFloatScaling());
   }

   @Test
   public void testNaNClearsFloatScaling() {
      ComponentDisplaySettings s = ComponentDisplaySettings.builder()
            .scalingRangeDouble(1.0, 2.0)
            .scalingMaximumDouble(Double.NaN)
            .build();
      assertFalse(s.hasFloatScaling());
   }

   @Test
   public void testCopyBuilderPreservesFloatRange() {
      ComponentDisplaySettings s = ComponentDisplaySettings.builder()
            .scalingRangeDouble(0.125, 4.5)
            .build()
            .copyBuilder()
            .build();
      assertTrue(s.hasFloatScaling());
      assertEquals(0.125, s.getScalingMinimumDouble(), DELTA);
      assertEquals(4.5, s.getScalingMaximumDouble(), DELTA);
   }

   // --- equals / hashCode ---

   @Test
   public void testEqualsDistinguishesFloatRange() {
      ComponentDisplaySettings a = ComponentDisplaySettings.builder()
            .scalingRangeDouble(0.0, 1.0).build();
      ComponentDisplaySettings b = ComponentDisplaySettings.builder()
            .scalingRangeDouble(0.0, 2.0).build();
      assertFalse(a.equals(b));
   }

   /**
    * The NaN sentinel must compare equal to itself, otherwise
    * compareAndSetDisplaySettings would never converge for integer images.
    */
   @Test
   public void testUnsetFloatRangesAreEqual() {
      ComponentDisplaySettings a = ComponentDisplaySettings.builder().build();
      ComponentDisplaySettings b = ComponentDisplaySettings.builder().build();
      assertTrue(a.equals(b));
      assertEquals(a.hashCode(), b.hashCode());
   }

   @Test
   public void testEqualFloatRangesHaveEqualHashCodes() {
      ComponentDisplaySettings a = ComponentDisplaySettings.builder()
            .scalingRangeDouble(-1.5, 3.5).build();
      ComponentDisplaySettings b = ComponentDisplaySettings.builder()
            .scalingRangeDouble(-1.5, 3.5).build();
      assertTrue(a.equals(b));
      assertEquals(a.hashCode(), b.hashCode());
   }

   // --- Persistence ---

   @Test
   public void testPropertyMapRoundTrip() {
      ComponentDisplaySettings orig = ComponentDisplaySettings.builder()
            .scalingRange(3L, 900L)
            .scalingRangeDouble(-0.75, 12.5)
            .scalingGamma(1.5)
            .build();
      PropertyMap pmap = ((DefaultComponentDisplaySettings) orig).toPropertyMap();
      ComponentDisplaySettings read =
            DefaultComponentDisplaySettings.fromPropertyMap(pmap);
      assertTrue(read.hasFloatScaling());
      assertEquals(-0.75, read.getScalingMinimumDouble(), DELTA);
      assertEquals(12.5, read.getScalingMaximumDouble(), DELTA);
      assertEquals(3L, read.getScalingMinimum());
      assertEquals(900L, read.getScalingMaximum());
      assertEquals(1.5, read.getScalingGamma(), DELTA);
   }

   /** Settings for integer data must not write the float keys at all. */
   @Test
   public void testUnsetFloatRangeIsNotWritten() {
      ComponentDisplaySettings orig = ComponentDisplaySettings.builder()
            .scalingRange(0L, 255L)
            .build();
      PropertyMap pmap = ((DefaultComponentDisplaySettings) orig).toPropertyMap();
      assertFalse(pmap.containsKey(PropertyKey.SCALING_MIN_FLOAT.key()));
      assertFalse(pmap.containsKey(PropertyKey.SCALING_MAX_FLOAT.key()));
   }

   /** Files written before float scaling existed must still load. */
   @Test
   public void testLegacyPropertyMapWithoutFloatKeys() {
      PropertyMap legacy = PropertyMaps.builder()
            .putLong(PropertyKey.SCALING_MIN.key(), 10L)
            .putLong(PropertyKey.SCALING_MAX.key(), 200L)
            .putDouble(PropertyKey.GAMMA.key(), 1.0)
            .build();
      ComponentDisplaySettings read =
            DefaultComponentDisplaySettings.fromPropertyMap(legacy);
      assertFalse(read.hasFloatScaling());
      assertEquals(10L, read.getScalingMinimum());
      assertEquals(200L, read.getScalingMaximum());
   }
}

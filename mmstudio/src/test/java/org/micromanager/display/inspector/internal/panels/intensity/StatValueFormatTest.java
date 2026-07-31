package org.micromanager.display.inspector.internal.panels.intensity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests formatting of floating point intensities in the Inspector stats readout.
 */
public class StatValueFormatTest {

   @Test
   public void testTypicalValues() {
      assertEquals("-2.238", ChannelIntensityController.formatStatValue(-2.238095283508301));
      assertEquals("1.032", ChannelIntensityController.formatStatValue(1.0322580337524414));
      assertEquals("-0.1622", ChannelIntensityController.formatStatValue(-0.16218741610646248));
   }

   @Test
   public void testZero() {
      assertEquals("0.000", ChannelIntensityController.formatStatValue(0.0));
   }

   @Test
   public void testVerySmallUsesScientificNotation() {
      String s = ChannelIntensityController.formatStatValue(1.23e-4);
      assertTrue("expected scientific notation, got " + s, s.contains("e"));
   }

   @Test
   public void testVeryLargeUsesScientificNotation() {
      String s = ChannelIntensityController.formatStatValue(1.0e7);
      assertTrue("expected scientific notation, got " + s, s.contains("e"));
   }

   @Test
   public void testSmallBoundaryStaysDecimal() {
      assertEquals("0.001000", ChannelIntensityController.formatStatValue(0.001));
   }

   /**
    * Note %.4g itself switches to scientific notation beyond four significant digits, so
    * values in the thousands are already exponential regardless of the explicit threshold.
    */
   @Test
   public void testFourSignificantDigitsStayDecimal() {
      assertEquals("9999", ChannelIntensityController.formatStatValue(9999.0));
      assertEquals("1235", ChannelIntensityController.formatStatValue(1234.5));
   }

   @Test
   public void testNaNIsNull() {
      assertNull(ChannelIntensityController.formatStatValue(Double.NaN));
   }

   @Test
   public void testNegativeValuesAreShown() {
      // The readout used to hide negatives entirely; they must now render.
      String s = ChannelIntensityController.formatStatValue(-0.5);
      assertTrue(s.startsWith("-"));
   }
}

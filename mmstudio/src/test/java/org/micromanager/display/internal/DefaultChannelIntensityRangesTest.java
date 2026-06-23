package org.micromanager.display.internal;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.micromanager.display.ChannelIntensityRanges;
import org.micromanager.display.ComponentIntensityRange;

import static org.junit.Assert.*;

public class DefaultChannelIntensityRangesTest {

   // --- Default construction ---

   @Test
   public void testDefaultBuildHasZeroComponents() {
      ChannelIntensityRanges r = ChannelIntensityRanges.builder().build();
      assertEquals(0, r.getNumberOfComponents());
   }

   @Test
   public void testDefaultBuildOutOfRangeAccessReturnsDefaults() {
      ChannelIntensityRanges r = ChannelIntensityRanges.builder().build();
      assertEquals(0L, r.getComponentMinimum(0));
      assertEquals(Long.MAX_VALUE, r.getComponentMaximum(0));
      assertNotNull(r.getComponentRange(0));
   }

   // --- Setting component ranges ---

   @Test
   public void testSetSingleComponent() {
      ChannelIntensityRanges r = ChannelIntensityRanges.builder()
            .componentRange(0, 10L, 200L)
            .build();
      assertEquals(1, r.getNumberOfComponents());
      assertEquals(10L, r.getComponentMinimum(0));
      assertEquals(200L, r.getComponentMaximum(0));
   }

   @Test
   public void testSetNonContiguousComponentExpandsDefaults() {
      // Setting component 2 should expand to 3 components,
      // with components 0 and 1 at their defaults.
      ChannelIntensityRanges r = ChannelIntensityRanges.builder()
            .componentRange(2, 5L, 100L)
            .build();
      assertEquals(3, r.getNumberOfComponents());
      assertEquals(0L, r.getComponentMinimum(0));
      assertEquals(Long.MAX_VALUE, r.getComponentMaximum(0));
      assertEquals(0L, r.getComponentMinimum(1));
      assertEquals(Long.MAX_VALUE, r.getComponentMaximum(1));
      assertEquals(5L, r.getComponentMinimum(2));
      assertEquals(100L, r.getComponentMaximum(2));
   }

   @Test
   public void testGrowingArrayPreservesExistingComponents() {
      // Set component 0, then extend to component 2.
      // Component 0's values must not be overwritten by the expansion.
      ChannelIntensityRanges r = ChannelIntensityRanges.builder()
            .componentRange(0, 42L, 84L)
            .componentRange(2, 1L, 99L)
            .build();
      assertEquals(42L, r.getComponentMinimum(0));
      assertEquals(84L, r.getComponentMaximum(0));
   }

   // --- Normalization (trailing default components are stripped) ---

   @Test
   public void testNormalizationStripsTrailingDefaults() {
      // Component 0 non-default, component 1 default → should be stored as 1 component.
      ChannelIntensityRanges r = ChannelIntensityRanges.builder()
            .componentRange(0, 0L, 255L)
            .componentMaximum(1, Long.MAX_VALUE) // explicitly default value
            .build();
      assertEquals(1, r.getNumberOfComponents());
   }

   @Test
   public void testNormalizationAllDefaultsYieldsZeroComponents() {
      // Explicitly writing the default values should normalize to 0 components.
      ChannelIntensityRanges r = ChannelIntensityRanges.builder()
            .componentRange(0, 0L, Long.MAX_VALUE)
            .componentRange(1, 0L, Long.MAX_VALUE)
            .build();
      assertEquals(0, r.getNumberOfComponents());
   }

   @Test
   public void testNormalizationKeepsLeadingDefaultBeforeNonDefault() {
      // Component 0 is default, component 1 is non-default → both must be kept.
      ChannelIntensityRanges r = ChannelIntensityRanges.builder()
            .componentRange(1, 10L, 200L)
            .build();
      assertEquals(2, r.getNumberOfComponents());
      assertEquals(0L, r.getComponentMinimum(0));
      assertEquals(Long.MAX_VALUE, r.getComponentMaximum(0));
      assertEquals(10L, r.getComponentMinimum(1));
      assertEquals(200L, r.getComponentMaximum(1));
   }

   // --- componentRanges(List) resizes and trims ---

   @Test
   public void testComponentRangesListSetsAll() {
      List<ComponentIntensityRange> list = Arrays.asList(
            ComponentIntensityRange.builder().range(1L, 10L).build(),
            ComponentIntensityRange.builder().range(2L, 20L).build()
      );
      ChannelIntensityRanges r = ChannelIntensityRanges.builder()
            .componentRanges(list)
            .build();
      assertEquals(2, r.getNumberOfComponents());
      assertEquals(1L, r.getComponentMinimum(0));
      assertEquals(10L, r.getComponentMaximum(0));
      assertEquals(2L, r.getComponentMinimum(1));
      assertEquals(20L, r.getComponentMaximum(1));
   }

   @Test
   public void testComponentRangesListTrimsExcess() {
      // First set 3 components, then replace with a 1-element list.
      List<ComponentIntensityRange> list = Arrays.asList(
            ComponentIntensityRange.builder().range(5L, 50L).build()
      );
      ChannelIntensityRanges r = ChannelIntensityRanges.builder()
            .componentRange(2, 99L, 999L)
            .componentRanges(list)
            .build();
      assertEquals(1, r.getNumberOfComponents());
      assertEquals(5L, r.getComponentMinimum(0));
      assertEquals(50L, r.getComponentMaximum(0));
   }

   // --- Out-of-range access returns defaults ---

   @Test
   public void testOutOfRangeAccessNegativeIndex() {
      ChannelIntensityRanges r = ChannelIntensityRanges.builder()
            .componentRange(0, 10L, 200L)
            .build();
      assertEquals(0L, r.getComponentMinimum(-1));
      assertEquals(Long.MAX_VALUE, r.getComponentMaximum(-1));
   }

   @Test
   public void testOutOfRangeAccessBeyondSize() {
      ChannelIntensityRanges r = ChannelIntensityRanges.builder()
            .componentRange(0, 10L, 200L)
            .build();
      assertEquals(1, r.getNumberOfComponents());
      // component 1 is out of range
      assertEquals(0L, r.getComponentMinimum(1));
      assertEquals(Long.MAX_VALUE, r.getComponentMaximum(1));
   }

   // --- copyBuilder round-trips ---

   @Test
   public void testCopyBuilderPreservesValues() {
      ChannelIntensityRanges original = ChannelIntensityRanges.builder()
            .componentRange(0, 3L, 97L)
            .componentRange(1, 7L, 200L)
            .build();
      ChannelIntensityRanges copy = original.copyBuilder().build();
      assertEquals(original.getNumberOfComponents(), copy.getNumberOfComponents());
      assertEquals(original.getComponentMinimum(0), copy.getComponentMinimum(0));
      assertEquals(original.getComponentMaximum(0), copy.getComponentMaximum(0));
      assertEquals(original.getComponentMinimum(1), copy.getComponentMinimum(1));
      assertEquals(original.getComponentMaximum(1), copy.getComponentMaximum(1));
   }

   @Test
   public void testCopyBuilderIsIndependent() {
      ChannelIntensityRanges original = ChannelIntensityRanges.builder()
            .componentRange(0, 3L, 97L)
            .build();
      ChannelIntensityRanges modified = original.copyBuilder()
            .componentRange(0, 0L, 255L)
            .build();
      // Original must be unchanged
      assertEquals(3L, original.getComponentMinimum(0));
      assertEquals(97L, original.getComponentMaximum(0));
      assertEquals(0L, modified.getComponentMinimum(0));
      assertEquals(255L, modified.getComponentMaximum(0));
   }
}

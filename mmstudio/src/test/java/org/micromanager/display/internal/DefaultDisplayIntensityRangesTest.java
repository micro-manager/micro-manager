package org.micromanager.display.internal;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.micromanager.display.ChannelIntensityRanges;
import org.micromanager.display.ComponentIntensityRange;
import org.micromanager.display.DisplayIntensityRanges;

import static org.junit.Assert.*;

public class DefaultDisplayIntensityRangesTest {

   // --- Default construction ---

   @Test
   public void testDefaultBuildHasZeroChannels() {
      DisplayIntensityRanges r = DisplayIntensityRanges.builder().build();
      assertEquals(0, r.getNumberOfChannels());
   }

   @Test
   public void testDefaultBuildOutOfRangeAccessReturnsDefaults() {
      DisplayIntensityRanges r = DisplayIntensityRanges.builder().build();
      assertEquals(0, r.getChannelNumberOfComponents(0));
      assertEquals(0L, r.getComponentMinimum(0, 0));
      assertEquals(Long.MAX_VALUE, r.getComponentMaximum(0, 0));
      assertNotNull(r.getComponentRange(0, 0));
      assertNotNull(r.getChannelRanges(0));
   }

   // --- Setting component ranges ---

   @Test
   public void testSetSingleChannelSingleComponent() {
      DisplayIntensityRanges r = DisplayIntensityRanges.builder()
            .componentRange(0, 0, 10L, 200L)
            .build();
      assertEquals(1, r.getNumberOfChannels());
      assertEquals(1, r.getChannelNumberOfComponents(0));
      assertEquals(10L, r.getComponentMinimum(0, 0));
      assertEquals(200L, r.getComponentMaximum(0, 0));
   }

   @Test
   public void testSetMultipleChannels() {
      DisplayIntensityRanges r = DisplayIntensityRanges.builder()
            .componentRange(0, 0, 1L, 10L)
            .componentRange(1, 0, 2L, 20L)
            .build();
      assertEquals(2, r.getNumberOfChannels());
      assertEquals(1L, r.getComponentMinimum(0, 0));
      assertEquals(10L, r.getComponentMaximum(0, 0));
      assertEquals(2L, r.getComponentMinimum(1, 0));
      assertEquals(20L, r.getComponentMaximum(1, 0));
   }

   @Test
   public void testSetNonContiguousChannelExpandsWithDefaults() {
      DisplayIntensityRanges r = DisplayIntensityRanges.builder()
            .componentRange(2, 0, 5L, 100L)
            .build();
      // channels 0 and 1 should exist but have zero components (default/normalized away)
      assertEquals(3, r.getNumberOfChannels());
      assertEquals(5L, r.getComponentMinimum(2, 0));
      assertEquals(100L, r.getComponentMaximum(2, 0));
   }

   // --- Normalization (trailing all-default channels are stripped) ---

   @Test
   public void testNormalizationStripsTrailingDefaultChannels() {
      // Channel 0 non-default, channel 1 all-default → stored as 1 channel.
      DisplayIntensityRanges r = DisplayIntensityRanges.builder()
            .componentRange(0, 0, 0L, 255L)
            .componentRange(1, 0, 0L, Long.MAX_VALUE) // default
            .build();
      assertEquals(1, r.getNumberOfChannels());
   }

   @Test
   public void testNormalizationAllDefaultChannelsYieldsZero() {
      DisplayIntensityRanges r = DisplayIntensityRanges.builder()
            .componentRange(0, 0, 0L, Long.MAX_VALUE)
            .componentRange(1, 0, 0L, Long.MAX_VALUE)
            .build();
      assertEquals(0, r.getNumberOfChannels());
   }

   // --- componentRanges(int, List) ---

   @Test
   public void testComponentRangesListSetsChannel() {
      List<ComponentIntensityRange> list = Arrays.asList(
            ComponentIntensityRange.builder().range(1L, 10L).build(),
            ComponentIntensityRange.builder().range(2L, 20L).build()
      );
      DisplayIntensityRanges r = DisplayIntensityRanges.builder()
            .componentRanges(0, list)
            .build();
      assertEquals(2, r.getChannelNumberOfComponents(0));
      assertEquals(1L, r.getComponentMinimum(0, 0));
      assertEquals(10L, r.getComponentMaximum(0, 0));
      assertEquals(2L, r.getComponentMinimum(0, 1));
      assertEquals(20L, r.getComponentMaximum(0, 1));
   }

   // --- channelRanges(List) replaces all channels ---

   @Test
   public void testChannelRangesListReplacesAll() {
      ChannelIntensityRanges ch0 = ChannelIntensityRanges.builder()
            .componentRange(0, 3L, 30L).build();
      ChannelIntensityRanges ch1 = ChannelIntensityRanges.builder()
            .componentRange(0, 7L, 70L).build();
      DisplayIntensityRanges r = DisplayIntensityRanges.builder()
            .channelRanges(Arrays.asList(ch0, ch1))
            .build();
      assertEquals(2, r.getNumberOfChannels());
      assertEquals(3L, r.getComponentMinimum(0, 0));
      assertEquals(30L, r.getComponentMaximum(0, 0));
      assertEquals(7L, r.getComponentMinimum(1, 0));
      assertEquals(70L, r.getComponentMaximum(1, 0));
   }

   // --- Out-of-range access returns defaults ---

   @Test
   public void testOutOfRangeNegativeChannelReturnsDefaults() {
      DisplayIntensityRanges r = DisplayIntensityRanges.builder()
            .componentRange(0, 0, 10L, 200L)
            .build();
      assertEquals(0, r.getChannelNumberOfComponents(-1));
      assertEquals(0L, r.getComponentMinimum(-1, 0));
      assertEquals(Long.MAX_VALUE, r.getComponentMaximum(-1, 0));
      assertTrue(r.getAllComponentRanges(-1).isEmpty());
      assertTrue(r.getComponentMinima(-1).isEmpty());
      assertTrue(r.getComponentMaxima(-1).isEmpty());
   }

   @Test
   public void testOutOfRangeBeyondSizeReturnsDefaults() {
      DisplayIntensityRanges r = DisplayIntensityRanges.builder()
            .componentRange(0, 0, 10L, 200L)
            .build();
      assertEquals(1, r.getNumberOfChannels());
      assertEquals(0, r.getChannelNumberOfComponents(1));
      assertEquals(0L, r.getComponentMinimum(1, 0));
      assertEquals(Long.MAX_VALUE, r.getComponentMaximum(1, 0));
   }

   // --- copyBuilder round-trips ---

   @Test
   public void testCopyBuilderPreservesValues() {
      DisplayIntensityRanges original = DisplayIntensityRanges.builder()
            .componentRange(0, 0, 3L, 97L)
            .componentRange(1, 0, 7L, 200L)
            .build();
      DisplayIntensityRanges copy = original.copyBuilder().build();
      assertEquals(original.getNumberOfChannels(), copy.getNumberOfChannels());
      assertEquals(original.getComponentMinimum(0, 0), copy.getComponentMinimum(0, 0));
      assertEquals(original.getComponentMaximum(0, 0), copy.getComponentMaximum(0, 0));
      assertEquals(original.getComponentMinimum(1, 0), copy.getComponentMinimum(1, 0));
      assertEquals(original.getComponentMaximum(1, 0), copy.getComponentMaximum(1, 0));
   }

   @Test
   public void testCopyBuilderIsIndependent() {
      DisplayIntensityRanges original = DisplayIntensityRanges.builder()
            .componentRange(0, 0, 3L, 97L)
            .build();
      DisplayIntensityRanges modified = original.copyBuilder()
            .componentRange(0, 0, 0L, 255L)
            .build();
      assertEquals(3L, original.getComponentMinimum(0, 0));
      assertEquals(97L, original.getComponentMaximum(0, 0));
      assertEquals(0L, modified.getComponentMinimum(0, 0));
      assertEquals(255L, modified.getComponentMaximum(0, 0));
   }
}

package org.micromanager.internal.jacque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ManageShutterTest {

   private static AcqChannel makeChannel(String name) {
      AcqChannel ch = new AcqChannel();
      ch.name = name;
      ch.exposure = 100;
      ch.properties = Collections.emptyMap();
      return ch;
   }

   private static AcqChannel makeChannel(String name,
         String coreShutter) {
      AcqChannel ch = new AcqChannel();
      ch.name = name;
      ch.exposure = 100;
      ch.properties = Collections.singletonMap(
            Collections.unmodifiableList(Arrays.asList("Core", "Shutter")),
            coreShutter);
      return ch;
   }

   private static AcqEvent event(int frame, int slice, int pos,
         AcqChannel ch) {
      AcqEvent e = new AcqEvent();
      e.frameIndex = frame;
      e.sliceIndex = slice;
      e.slice = (double) slice;
      e.positionIndex = pos;
      e.channel = ch;
      return e;
   }

   @Test
   public void testLastEventAlwaysCloses() {
      AcqChannel ch = makeChannel("DAPI");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch));
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), false, false).toList();
      assertTrue(result.get(0).closeShutter);
   }

   @Test
   public void testSameChannelSameSliceSameFrame() {
      AcqChannel ch = makeChannel("DAPI");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch));
      events.add(event(0, 1, 0, ch));
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), true, true).toList();
      assertFalse("Same frame, keepOpenSlices=true -> don't close",
            result.get(0).closeShutter);
      assertTrue("Last event always closes",
            result.get(1).closeShutter);
   }

   @Test
   public void testDifferentChannelNotKeepOpen() {
      AcqChannel ch1 = makeChannel("DAPI");
      AcqChannel ch2 = makeChannel("GFP");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch1));
      events.add(event(0, 0, 0, ch2));
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), false, true).toList();
      assertTrue("Different channel, keepOpenChannels=false -> close",
            result.get(0).closeShutter);
   }

   @Test
   public void testDifferentChannelKeepOpen() {
      AcqChannel ch1 = makeChannel("DAPI");
      AcqChannel ch2 = makeChannel("GFP");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch1));
      events.add(event(0, 0, 0, ch2));
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), true, true).toList();
      assertFalse("Different channel, keepOpenChannels=true -> don't close",
            result.get(0).closeShutter);
   }

   @Test
   public void testDifferentSliceNotKeepOpen() {
      AcqChannel ch = makeChannel("DAPI");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch));
      events.add(event(0, 1, 0, ch));
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), true, false).toList();
      assertTrue("Different slice, keepOpenSlices=false -> close",
            result.get(0).closeShutter);
   }

   @Test
   public void testDifferentSliceKeepOpen() {
      AcqChannel ch = makeChannel("DAPI");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch));
      events.add(event(0, 1, 0, ch));
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), true, true).toList();
      assertFalse("Different slice, keepOpenSlices=true -> don't close",
            result.get(0).closeShutter);
   }

   @Test
   public void testDifferentFrameCloses() {
      AcqChannel ch = makeChannel("DAPI");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch));
      events.add(event(1, 0, 0, ch));
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), false, false).toList();
      assertTrue("Different frame -> close",
            result.get(0).closeShutter);
   }

   @Test
   public void testRapidChannelCycleSpecialCase() {
      // Rapid channel cycling: frame changes but same slice, same position,
      // keepShutterOpenChannels=true, and no/zero wait time.
      AcqChannel ch1 = makeChannel("DAPI");
      AcqChannel ch2 = makeChannel("GFP");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch2));
      AcqEvent e2 = event(1, 0, 0, ch1);
      e2.waitTimeMs = 0.0;
      events.add(e2);
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), true, false).toList();
      assertFalse("Rapid channel cycling special case -> don't close",
            result.get(0).closeShutter);
   }

   @Test
   public void testRapidCycleNotAppliedWithWaitTime() {
      AcqChannel ch1 = makeChannel("DAPI");
      AcqChannel ch2 = makeChannel("GFP");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch2));
      AcqEvent e2 = event(1, 0, 0, ch1);
      e2.waitTimeMs = 5000.0;
      events.add(e2);
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), true, false).toList();
      assertTrue("Wait time > 0 breaks rapid cycle -> close",
            result.get(0).closeShutter);
   }

   @Test
   public void testRapidCycleNotAppliedWithDifferentPosition() {
      AcqChannel ch1 = makeChannel("DAPI");
      AcqChannel ch2 = makeChannel("GFP");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch2));
      AcqEvent e2 = event(1, 0, 1, ch1);
      e2.waitTimeMs = 0.0;
      events.add(e2);
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), true, false).toList();
      assertTrue("Different position breaks rapid cycle -> close",
            result.get(0).closeShutter);
   }

   @Test
   public void testDifferentPositionCloses() {
      AcqChannel ch = makeChannel("DAPI");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch));
      events.add(event(0, 0, 1, ch));
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), true, true).toList();
      assertTrue("Different position -> close",
            result.get(0).closeShutter);
   }

   @Test
   public void testAutofocusOnNextEventCloses() {
      AcqChannel ch = makeChannel("DAPI");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch));
      AcqEvent e2 = event(0, 0, 0, ch);
      e2.autofocus = true;
      events.add(e2);
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), true, true).toList();
      assertTrue("Autofocus on next event -> close",
            result.get(0).closeShutter);
   }

   @Test
   public void testDifferentCoreShutterCloses() {
      AcqChannel ch1 = makeChannel("DAPI", "ShutterA");
      AcqChannel ch2 = makeChannel("GFP", "ShutterB");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch1));
      events.add(event(0, 0, 0, ch2));
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), true, true).toList();
      assertTrue("Different Core-Shutter property -> close",
            result.get(0).closeShutter);
   }

   @Test
   public void testSameCoreShutterStaysOpen() {
      AcqChannel ch1 = makeChannel("DAPI", "ShutterA");
      AcqChannel ch2 = makeChannel("GFP", "ShutterA");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch1));
      events.add(event(0, 0, 0, ch2));
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), true, true).toList();
      assertFalse("Same Core-Shutter, keepOpen=true -> don't close",
            result.get(0).closeShutter);
   }

   @Test
   public void testNullWaitTimeIsRapidCycle() {
      AcqChannel ch1 = makeChannel("DAPI");
      AcqChannel ch2 = makeChannel("GFP");
      List<AcqEvent> events = new ArrayList<>();
      events.add(event(0, 0, 0, ch2));
      AcqEvent e2 = event(1, 0, 0, ch1);
      e2.waitTimeMs = null;
      events.add(e2);
      List<AcqEvent> result = SequenceGenerator.manageShutter(
            Seq.fromList(events), true, false).toList();
      assertFalse("Null wait time counts as rapid cycle -> don't close",
            result.get(0).closeShutter);
   }
}

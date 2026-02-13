package org.micromanager.internal.jacque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SequenceGeneratorTest {

   // --- allEqual tests ---

   @Test
   public void testAllEqualEmpty() {
      assertTrue(SequenceGenerator.allEqual(Collections.emptyList()));
   }

   @Test
   public void testAllEqualSame() {
      assertTrue(SequenceGenerator.allEqual(Arrays.asList(1, 1, 1)));
   }

   @Test
   public void testAllEqualDifferent() {
      assertFalse(SequenceGenerator.allEqual(Arrays.asList(1, 2, 1)));
   }

   @Test
   public void testAllEqualWithValTrue() {
      assertTrue(SequenceGenerator.allEqual(0,
            Arrays.asList(0, 0, 0)));
   }

   @Test
   public void testAllEqualWithValFalse() {
      assertFalse(SequenceGenerator.allEqual(0,
            Arrays.asList(0, 1, 0)));
   }

   // --- makePropertySequences tests ---

   @Test
   public void testMakePropertySequencesEmpty() {
      Map<List<String>, List<String>> result =
            SequenceGenerator.makePropertySequences(
                  Collections.emptyList());
      assertTrue(result.isEmpty());
   }

   @Test
   public void testMakePropertySequencesSingleChannel() {
      Map<List<String>, String> props = new HashMap<>();
      List<String> key = Arrays.asList("Dev1", "Prop1");
      props.put(key, "val1");
      Map<List<String>, List<String>> result =
            SequenceGenerator.makePropertySequences(
                  Collections.singletonList(props));
      assertEquals(1, result.size());
      assertEquals(Collections.singletonList("val1"), result.get(key));
   }

   @Test
   public void testMakePropertySequencesTwoChannels() {
      List<String> key1 = Arrays.asList("Dev1", "Prop1");
      List<String> key2 = Arrays.asList("Dev1", "Prop2");
      Map<List<String>, String> ch1Props = new HashMap<>();
      ch1Props.put(key1, "a");
      ch1Props.put(key2, "x");
      Map<List<String>, String> ch2Props = new HashMap<>();
      ch2Props.put(key1, "b");
      // key2 absent in ch2
      List<Map<List<String>, String>> channelProps = Arrays.asList(
            ch1Props, ch2Props);
      Map<List<String>, List<String>> result =
            SequenceGenerator.makePropertySequences(channelProps);
      assertEquals(2, result.size());
      assertEquals(Arrays.asList("a", "b"), result.get(key1));
      assertEquals(Arrays.asList("x", null), result.get(key2));
   }

   // --- makeMainLoops tests ---

   private AcqSettings makeSettings(int nFrames, int nSlices,
         int nChannels, int nPositions,
         boolean slicesFirst, boolean timeFirst) {
      AcqSettings s = new AcqSettings();
      s.frames = new ArrayList<>();
      for (int i = 0; i < nFrames; i++) {
         s.frames.add(i);
      }
      s.slices = new ArrayList<>();
      for (int i = 0; i < nSlices; i++) {
         s.slices.add((double) i);
      }
      s.channels = new ArrayList<>();
      for (int i = 0; i < nChannels; i++) {
         AcqChannel ch = new AcqChannel();
         ch.name = "Ch" + i;
         ch.exposure = 100;
         ch.useZStack = true;
         ch.skipFrames = 0;
         ch.properties = Collections.emptyMap();
         s.channels.add(ch);
      }
      if (nPositions > 0) {
         s.positions = new ArrayList<>();
         for (int i = 0; i < nPositions; i++) {
            s.positions.add(i);
         }
      }
      s.slicesFirst = slicesFirst;
      s.timeFirst = timeFirst;
      s.numFrames = nFrames;
      s.defaultExposure = 10;
      s.intervalMs = 0;
      s.relativeSlices = false;
      return s;
   }

   @Test
   public void testMakeMainLoopsSingleEvent() {
      AcqSettings s = makeSettings(1, 0, 0, 0, false, false);
      List<AcqEvent> events = SequenceGenerator.makeMainLoops(s);
      assertEquals(1, events.size());
      assertEquals(0, events.get(0).frameIndex);
   }

   @Test
   public void testMakeMainLoopsFramesOnly() {
      AcqSettings s = makeSettings(3, 0, 0, 0, false, false);
      List<AcqEvent> events = SequenceGenerator.makeMainLoops(s);
      assertEquals(3, events.size());
      for (int i = 0; i < 3; i++) {
         assertEquals(i, events.get(i).frameIndex);
      }
   }

   @Test
   public void testMakeMainLoopsSlicesFirst() {
      // slices-first=true: inner order is [slice, channel]
      // 2 slices, 2 channels, 1 frame, 1 position
      AcqSettings s = makeSettings(1, 2, 2, 0, true, false);
      List<AcqEvent> events = SequenceGenerator.makeMainLoops(s);
      assertEquals(4, events.size());
      // slices-first: slice varies fastest
      assertEquals(0, events.get(0).sliceIndex);
      assertEquals(0, events.get(0).channelIndex);
      assertEquals(1, events.get(1).sliceIndex);
      assertEquals(0, events.get(1).channelIndex);
      assertEquals(0, events.get(2).sliceIndex);
      assertEquals(1, events.get(2).channelIndex);
      assertEquals(1, events.get(3).sliceIndex);
      assertEquals(1, events.get(3).channelIndex);
   }

   @Test
   public void testMakeMainLoopsChannelsFirst() {
      // slices-first=false: inner order is [channel, slice]
      AcqSettings s = makeSettings(1, 2, 2, 0, false, false);
      List<AcqEvent> events = SequenceGenerator.makeMainLoops(s);
      assertEquals(4, events.size());
      // channels-first: channel varies fastest
      assertEquals(0, events.get(0).channelIndex);
      assertEquals(0, events.get(0).sliceIndex);
      assertEquals(1, events.get(1).channelIndex);
      assertEquals(0, events.get(1).sliceIndex);
      assertEquals(0, events.get(2).channelIndex);
      assertEquals(1, events.get(2).sliceIndex);
      assertEquals(1, events.get(3).channelIndex);
      assertEquals(1, events.get(3).sliceIndex);
   }

   @Test
   public void testMakeMainLoopsTimeFirst() {
      // time-first=true: outer order is [frame, position]
      AcqSettings s = makeSettings(2, 0, 0, 2, false, true);
      List<AcqEvent> events = SequenceGenerator.makeMainLoops(s);
      assertEquals(4, events.size());
      // frame varies before position
      assertEquals(0, events.get(0).frameIndex);
      assertEquals(0, events.get(0).positionIndex);
      assertEquals(1, events.get(1).frameIndex);
      assertEquals(0, events.get(1).positionIndex);
      assertEquals(0, events.get(2).frameIndex);
      assertEquals(1, events.get(2).positionIndex);
      assertEquals(1, events.get(3).frameIndex);
      assertEquals(1, events.get(3).positionIndex);
   }

   @Test
   public void testMakeMainLoopsPositionFirst() {
      // time-first=false: outer order is [position, frame]
      AcqSettings s = makeSettings(2, 0, 0, 2, false, false);
      List<AcqEvent> events = SequenceGenerator.makeMainLoops(s);
      assertEquals(4, events.size());
      // position varies before frame
      assertEquals(0, events.get(0).positionIndex);
      assertEquals(0, events.get(0).frameIndex);
      assertEquals(1, events.get(1).positionIndex);
      assertEquals(0, events.get(1).frameIndex);
      assertEquals(0, events.get(2).positionIndex);
      assertEquals(1, events.get(2).frameIndex);
      assertEquals(1, events.get(3).positionIndex);
      assertEquals(1, events.get(3).frameIndex);
   }

   // --- buildEvent tests ---

   @Test
   public void testBuildEventWithChannel() {
      AcqSettings s = new AcqSettings();
      s.defaultExposure = 50;
      s.relativeSlices = true;
      AcqChannel ch = new AcqChannel();
      ch.exposure = 200;
      AcqEvent e = new AcqEvent();
      e.channel = ch;
      SequenceGenerator.buildEvent(s, e);
      assertEquals(200.0, e.exposure, 0.0);
      assertTrue(e.relativeZ);
   }

   @Test
   public void testBuildEventWithoutChannel() {
      AcqSettings s = new AcqSettings();
      s.defaultExposure = 50;
      s.relativeSlices = false;
      AcqEvent e = new AcqEvent();
      SequenceGenerator.buildEvent(s, e);
      assertEquals(50.0, e.exposure, 0.0);
      assertFalse(e.relativeZ);
   }

   // --- processSkipZStack tests ---

   @Test
   public void testProcessSkipZStackEmptySlices() {
      AcqEvent e = new AcqEvent();
      List<AcqEvent> events = Collections.singletonList(e);
      List<AcqEvent> result = SequenceGenerator.processSkipZStack(
            events, Collections.emptyList());
      assertEquals(1, result.size());
   }

   @Test
   public void testProcessSkipZStackKeepsMiddle() {
      List<Double> slices = Arrays.asList(-1.0, 0.0, 1.0);
      AcqChannel noZ = new AcqChannel();
      noZ.useZStack = false;
      noZ.properties = Collections.emptyMap();
      AcqEvent e1 = new AcqEvent();
      e1.slice = -1.0;
      e1.channel = noZ;
      AcqEvent e2 = new AcqEvent();
      e2.slice = 0.0;
      e2.channel = noZ;
      AcqEvent e3 = new AcqEvent();
      e3.slice = 1.0;
      e3.channel = noZ;
      List<AcqEvent> events = Arrays.asList(e1, e2, e3);
      List<AcqEvent> result = SequenceGenerator.processSkipZStack(
            events, slices);
      // Middle slice is 0.0 (index 1), noZ channel → only middle kept
      assertEquals(1, result.size());
      assertEquals(Double.valueOf(0.0), result.get(0).slice);
   }

   @Test
   public void testProcessSkipZStackKeepsUseZStack() {
      List<Double> slices = Arrays.asList(-1.0, 0.0, 1.0);
      AcqChannel yesZ = new AcqChannel();
      yesZ.useZStack = true;
      yesZ.properties = Collections.emptyMap();
      AcqEvent e1 = new AcqEvent();
      e1.slice = -1.0;
      e1.channel = yesZ;
      AcqEvent e2 = new AcqEvent();
      e2.slice = 0.0;
      e2.channel = yesZ;
      AcqEvent e3 = new AcqEvent();
      e3.slice = 1.0;
      e3.channel = yesZ;
      List<AcqEvent> events = Arrays.asList(e1, e2, e3);
      List<AcqEvent> result = SequenceGenerator.processSkipZStack(
            events, slices);
      assertEquals(3, result.size());
   }

   // --- processChannelSkipFrames tests ---

   @Test
   public void testProcessChannelSkipFrames() {
      AcqChannel ch = new AcqChannel();
      ch.skipFrames = 2;
      ch.properties = Collections.emptyMap();
      List<AcqEvent> events = new ArrayList<>();
      for (int f = 0; f < 6; f++) {
         AcqEvent e = new AcqEvent();
         e.frameIndex = f;
         e.channel = ch;
         events.add(e);
      }
      List<AcqEvent> result =
            SequenceGenerator.processChannelSkipFrames(events);
      // skipFrames=2: keep frame 0, 3 (every 3rd frame)
      assertEquals(2, result.size());
      assertEquals(0, result.get(0).frameIndex);
      assertEquals(3, result.get(1).frameIndex);
   }

   @Test
   public void testProcessChannelSkipFramesZero() {
      AcqChannel ch = new AcqChannel();
      ch.skipFrames = 0;
      ch.properties = Collections.emptyMap();
      List<AcqEvent> events = new ArrayList<>();
      for (int f = 0; f < 3; f++) {
         AcqEvent e = new AcqEvent();
         e.frameIndex = f;
         e.channel = ch;
         events.add(e);
      }
      List<AcqEvent> result =
            SequenceGenerator.processChannelSkipFrames(events);
      assertEquals(3, result.size());
   }

   // --- processUseAutofocus tests ---

   @Test
   public void testProcessUseAutofocusOff() {
      AcqEvent e = new AcqEvent();
      List<AcqEvent> events = Collections.singletonList(e);
      SequenceGenerator.processUseAutofocus(events, false, 0);
      assertFalse(e.autofocus);
   }

   @Test
   public void testProcessUseAutofocusFirstEvent() {
      AcqEvent e = new AcqEvent();
      List<AcqEvent> events = Collections.singletonList(e);
      SequenceGenerator.processUseAutofocus(events, true, 0);
      assertTrue(e.autofocus);
   }

   @Test
   public void testProcessUseAutofocusSkip() {
      List<AcqEvent> events = new ArrayList<>();
      for (int f = 0; f < 4; f++) {
         AcqEvent e = new AcqEvent();
         e.frameIndex = f;
         events.add(e);
      }
      SequenceGenerator.processUseAutofocus(events, true, 1);
      assertTrue(events.get(0).autofocus);
      assertFalse("Skip=1: skip frame 1", events.get(1).autofocus);
      assertTrue("Skip=1: autofocus at frame 2", events.get(2).autofocus);
      assertFalse("Skip=1: skip frame 3", events.get(3).autofocus);
   }

   // --- processNewPosition tests ---

   @Test
   public void testProcessNewPosition() {
      List<AcqEvent> events = new ArrayList<>();
      for (int p = 0; p < 2; p++) {
         for (int f = 0; f < 2; f++) {
            AcqEvent e = new AcqEvent();
            e.positionIndex = p;
            e.frameIndex = f;
            events.add(e);
         }
      }
      SequenceGenerator.processNewPosition(events);
      assertTrue(events.get(0).newPosition);
      assertFalse(events.get(1).newPosition);
      assertTrue(events.get(2).newPosition);
      assertFalse(events.get(3).newPosition);
   }

   // --- processWaitTime tests ---

   @Test
   public void testProcessWaitTimeFixed() {
      List<AcqEvent> events = new ArrayList<>();
      for (int f = 0; f < 3; f++) {
         AcqEvent e = new AcqEvent();
         e.frameIndex = f;
         events.add(e);
      }
      SequenceGenerator.processWaitTime(events, null, 1000.0);
      assertEquals(0.0, events.get(0).waitTimeMs, 0.0);
      assertEquals(1000.0, events.get(1).waitTimeMs, 0.0);
      assertEquals(1000.0, events.get(2).waitTimeMs, 0.0);
   }

   @Test
   public void testProcessWaitTimeCustom() {
      List<AcqEvent> events = new ArrayList<>();
      for (int f = 0; f < 3; f++) {
         AcqEvent e = new AcqEvent();
         e.frameIndex = f;
         events.add(e);
      }
      List<Double> custom = Arrays.asList(100.0, 200.0, 300.0);
      SequenceGenerator.processWaitTime(events, custom, 0);
      assertEquals(100.0, events.get(0).waitTimeMs, 0.0);
      assertEquals(200.0, events.get(1).waitTimeMs, 0.0);
      assertEquals(300.0, events.get(2).waitTimeMs, 0.0);
   }

   @Test
   public void testProcessWaitTimeNoChangeNoWait() {
      // Same frame → no wait time set
      AcqChannel ch1 = new AcqChannel();
      ch1.name = "Ch1";
      ch1.properties = Collections.emptyMap();
      AcqChannel ch2 = new AcqChannel();
      ch2.name = "Ch2";
      ch2.properties = Collections.emptyMap();
      AcqEvent e1 = new AcqEvent();
      e1.frameIndex = 0;
      e1.channel = ch1;
      AcqEvent e2 = new AcqEvent();
      e2.frameIndex = 0;
      e2.channel = ch2;
      List<AcqEvent> events = Arrays.asList(e1, e2);
      SequenceGenerator.processWaitTime(events, null, 1000.0);
      assertEquals(0.0, e1.waitTimeMs, 0.0);
      assertNull("Same frame → no wait time", e2.waitTimeMs);
   }

   // --- attachRunnables tests ---

   @Test
   public void testAttachRunnablesMatchAll() {
      List<AcqEvent> events = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
         AcqEvent e = new AcqEvent();
         e.frameIndex = i;
         events.add(e);
      }
      Runnable r = () -> {};
      List<SequenceGenerator.AttachedRunnable> runnables =
            Collections.singletonList(
                  new SequenceGenerator.AttachedRunnable(-1, -1, -1, -1, r));
      SequenceGenerator.attachRunnables(events, runnables);
      for (AcqEvent e : events) {
         assertNotNull(e.runnables);
         assertEquals(1, e.runnables.size());
      }
   }

   @Test
   public void testAttachRunnablesMatchSpecific() {
      List<AcqEvent> events = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
         AcqEvent e = new AcqEvent();
         e.frameIndex = i;
         events.add(e);
      }
      Runnable r = () -> {};
      List<SequenceGenerator.AttachedRunnable> runnables =
            Collections.singletonList(
                  new SequenceGenerator.AttachedRunnable(1, -1, -1, -1, r));
      SequenceGenerator.attachRunnables(events, runnables);
      assertNull(events.get(0).runnables);
      assertEquals(1, events.get(1).runnables.size());
      assertNull(events.get(2).runnables);
   }

   // --- addNextTaskTags tests ---

   @Test
   public void testAddNextTaskTags() {
      List<AcqEvent> events = new ArrayList<>();
      for (int f = 0; f < 3; f++) {
         AcqEvent e = new AcqEvent();
         e.frameIndex = f;
         e.task = "snap";
         events.add(e);
      }
      SequenceGenerator.addNextTaskTags(events);
      assertEquals(Integer.valueOf(1), events.get(0).nextFrameIndex);
      assertEquals(Integer.valueOf(2), events.get(1).nextFrameIndex);
      assertNull(events.get(2).nextFrameIndex);
   }

   // --- burstValid tests ---

   @Test
   public void testBurstValidNull() {
      AcqEvent e = new AcqEvent();
      assertFalse(SequenceGenerator.burstValid(e, null));
   }

   @Test
   public void testBurstValidBasic() {
      AcqEvent e1 = new AcqEvent();
      e1.exposure = 100;
      e1.position = 0;
      AcqEvent e2 = new AcqEvent();
      e2.exposure = 100;
      e2.position = 0;
      assertTrue(SequenceGenerator.burstValid(e1, e2));
   }

   @Test
   public void testBurstValidDifferentExposure() {
      AcqEvent e1 = new AcqEvent();
      e1.exposure = 100;
      e1.position = 0;
      AcqEvent e2 = new AcqEvent();
      e2.exposure = 200;
      e2.position = 0;
      assertFalse(SequenceGenerator.burstValid(e1, e2));
   }

   @Test
   public void testBurstValidWithAutofocus() {
      AcqEvent e1 = new AcqEvent();
      e1.exposure = 100;
      e1.position = 0;
      AcqEvent e2 = new AcqEvent();
      e2.exposure = 100;
      e2.position = 0;
      e2.autofocus = true;
      assertFalse(SequenceGenerator.burstValid(e1, e2));
   }

   @Test
   public void testBurstValidWaitTimeExceedsExposure() {
      AcqEvent e1 = new AcqEvent();
      e1.exposure = 100;
      e1.position = 0;
      AcqEvent e2 = new AcqEvent();
      e2.exposure = 100;
      e2.position = 0;
      e2.waitTimeMs = 200.0;
      assertFalse(SequenceGenerator.burstValid(e1, e2));
   }

   @Test
   public void testBurstValidNullWaitTime() {
      AcqEvent e1 = new AcqEvent();
      e1.exposure = 100;
      e1.position = 0;
      AcqEvent e2 = new AcqEvent();
      e2.exposure = 100;
      e2.position = 0;
      e2.waitTimeMs = null;
      assertTrue(SequenceGenerator.burstValid(e1, e2));
   }

   // --- makeChannelMetadata tests ---

   @Test
   public void testMakeChannelMetadataNull() {
      assertNull(SequenceGenerator.makeChannelMetadata(null));
   }

   @Test
   public void testMakeChannelMetadata() {
      AcqChannel ch = new AcqChannel();
      Map<List<String>, String> props = new HashMap<>();
      props.put(Arrays.asList("Dev1", "Prop1"), "val1");
      props.put(Arrays.asList("Dev2", "Prop2"), "val2");
      ch.properties = props;
      Map<String, String> meta =
            SequenceGenerator.makeChannelMetadata(ch);
      assertNotNull(meta);
      assertEquals("val1", meta.get("Dev1-Prop1"));
      assertEquals("val2", meta.get("Dev2-Prop2"));
   }

   // --- Full pipeline integration test (with fake CoreOps) ---

   @Test
   public void testGenerateDefaultSequenceSimple() {
      CoreOps noBurst = new CoreOps() {
         @Override
         public boolean isPropertySequenceable(String d, String p) {
            return false;
         }
         @Override
         public int getPropertySequenceMaxLength(String d, String p) {
            return 0;
         }
         @Override
         public String getFocusDevice() { return ""; }
         @Override
         public boolean isStageSequenceable(String d) { return false; }
         @Override
         public int getStageSequenceMaxLength(String d) { return 0; }
      };

      AcqSettings s = makeSettings(2, 2, 1, 0, true, false);
      s.keepShutterOpenSlices = true;
      s.keepShutterOpenChannels = false;
      List<AcqEvent> events =
            SequenceGenerator.generateDefaultAcqSequence(
                  s, null, noBurst);
      assertNotNull(events);
      assertFalse(events.isEmpty());
      // With no bursting, all events should be snaps
      for (AcqEvent e : events) {
         assertEquals("snap", e.task);
      }
      // 2 frames * 2 slices * 1 channel = 4 events
      assertEquals(4, events.size());
   }
}

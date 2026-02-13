package org.micromanager.internal.jacque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentVector;
import clojure.lang.RT;
import clojure.lang.Symbol;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Compares Java SequenceGenerator output against Clojure
 * sequence_generator for identical inputs.
 */
public class SequenceGeneratorComparisonTest {

   // Settings keywords
   private static final Keyword KW_NUM_FRAMES =
         Keyword.intern("numFrames");
   private static final Keyword KW_FRAMES = Keyword.intern("frames");
   private static final Keyword KW_POSITIONS =
         Keyword.intern("positions");
   private static final Keyword KW_CHANNELS =
         Keyword.intern("channels");
   private static final Keyword KW_SLICES = Keyword.intern("slices");
   private static final Keyword KW_SLICES_FIRST =
         Keyword.intern("slices-first");
   private static final Keyword KW_TIME_FIRST =
         Keyword.intern("time-first");
   private static final Keyword KW_KEEP_SHUTTER_OPEN_SLICES =
         Keyword.intern("keep-shutter-open-slices");
   private static final Keyword KW_KEEP_SHUTTER_OPEN_CHANNELS =
         Keyword.intern("keep-shutter-open-channels");
   private static final Keyword KW_USE_AUTOFOCUS =
         Keyword.intern("use-autofocus");
   private static final Keyword KW_AUTOFOCUS_SKIP =
         Keyword.intern("autofocus-skip");
   private static final Keyword KW_RELATIVE_SLICES =
         Keyword.intern("relative-slices");
   private static final Keyword KW_INTERVAL_MS =
         Keyword.intern("interval-ms");
   private static final Keyword KW_DEFAULT_EXPOSURE =
         Keyword.intern("default-exposure");
   private static final Keyword KW_CUSTOM_INTERVALS_MS =
         Keyword.intern("custom-intervals-ms");

   // Channel keywords
   private static final Keyword KW_NAME = Keyword.intern("name");
   private static final Keyword KW_EXPOSURE =
         Keyword.intern("exposure");
   private static final Keyword KW_Z_OFFSET =
         Keyword.intern("z-offset");
   private static final Keyword KW_USE_Z_STACK =
         Keyword.intern("use-z-stack");
   private static final Keyword KW_SKIP_FRAMES =
         Keyword.intern("skip-frames");
   private static final Keyword KW_USE_CHANNEL =
         Keyword.intern("use-channel");
   private static final Keyword KW_COLOR = Keyword.intern("color");
   private static final Keyword KW_PROPERTIES =
         Keyword.intern("properties");

   // Event keywords
   private static final Keyword KW_FRAME_INDEX =
         Keyword.intern("frame-index");
   private static final Keyword KW_SLICE_INDEX =
         Keyword.intern("slice-index");
   private static final Keyword KW_CHANNEL_INDEX =
         Keyword.intern("channel-index");
   private static final Keyword KW_POSITION_INDEX =
         Keyword.intern("position-index");
   private static final Keyword KW_SLICE = Keyword.intern("slice");
   private static final Keyword KW_WAIT_TIME_MS =
         Keyword.intern("wait-time-ms");
   private static final Keyword KW_AUTOFOCUS =
         Keyword.intern("autofocus");
   private static final Keyword KW_NEW_POSITION =
         Keyword.intern("new-position");
   private static final Keyword KW_CLOSE_SHUTTER =
         Keyword.intern("close-shutter");
   private static final Keyword KW_RELATIVE_Z =
         Keyword.intern("relative-z");
   private static final Keyword KW_TASK = Keyword.intern("task");
   private static final Keyword KW_NEXT_FRAME_INDEX =
         Keyword.intern("next-frame-index");
   private static final Keyword KW_CHANNEL = Keyword.intern("channel");
   private static final Keyword KW_BURST_LENGTH =
         Keyword.intern("burst-length");

   private static Object cljGenerate;
   private static CoreOps noBurstCore;

   @BeforeClass
   public static void setup() throws Exception {
      Object mmc = new mmcorej.CMMCore();
      Object require = RT.var("clojure.core", "require");
      ((clojure.lang.IFn) require).invoke(
            Symbol.intern("org.micromanager.mm"));
      ((clojure.lang.IFn) RT.var("org.micromanager.mm",
            "store-mmcore")).invoke(mmc);
      ((clojure.lang.IFn) require).invoke(
            Symbol.intern("org.micromanager.sequence-generator"));
      cljGenerate = RT.var("org.micromanager.sequence-generator",
            "generate-acq-sequence");
      noBurstCore = new CoreOps() {
         @Override
         public boolean isPropertySequenceable(String d, String p) {
            return false;
         }

         @Override
         public int getPropertySequenceMaxLength(String d, String p) {
            return 0;
         }

         @Override
         public String getFocusDevice() {
            return "";
         }

         @Override
         public boolean isStageSequenceable(String d) {
            return false;
         }

         @Override
         public int getStageSequenceMaxLength(String d) {
            return 0;
         }
      };
   }

   // --- Helpers ---

   private static Object toClojureChannel(AcqChannel ch) {
      return RT.map(
            KW_NAME, ch.name,
            KW_EXPOSURE, ch.exposure,
            KW_Z_OFFSET, ch.zOffset,
            KW_USE_Z_STACK, ch.useZStack,
            KW_SKIP_FRAMES, ch.skipFrames,
            KW_USE_CHANNEL, ch.useChannel,
            KW_COLOR, ch.color,
            KW_PROPERTIES, PersistentArrayMap.EMPTY);
   }

   private static Object toClojureSettings(AcqSettings s) {
      List<Object> cljChannels = new ArrayList<>();
      for (AcqChannel ch : s.channels) {
         cljChannels.add(toClojureChannel(ch));
      }
      Object frames = s.frames != null
            ? PersistentVector.create(s.frames) : null;
      Object slices = s.slices != null
            ? PersistentVector.create(s.slices) : null;
      Object positions = s.positions != null
            ? PersistentVector.create(s.positions) : null;
      Object channels = PersistentVector.create(cljChannels);
      Object customIntervals = (s.customIntervalsMs != null
            && !s.customIntervalsMs.isEmpty())
            ? PersistentVector.create(s.customIntervalsMs) : null;
      return RT.map(
            KW_NUM_FRAMES, s.numFrames,
            KW_FRAMES, frames,
            KW_POSITIONS, positions,
            KW_CHANNELS, channels,
            KW_SLICES, slices,
            KW_SLICES_FIRST, s.slicesFirst,
            KW_TIME_FIRST, s.timeFirst,
            KW_KEEP_SHUTTER_OPEN_SLICES, s.keepShutterOpenSlices,
            KW_KEEP_SHUTTER_OPEN_CHANNELS, s.keepShutterOpenChannels,
            KW_USE_AUTOFOCUS, s.useAutofocus,
            KW_AUTOFOCUS_SKIP, s.autofocusSkip,
            KW_RELATIVE_SLICES, s.relativeSlices,
            KW_INTERVAL_MS, s.intervalMs,
            KW_DEFAULT_EXPOSURE, s.defaultExposure,
            KW_CUSTOM_INTERVALS_MS, customIntervals);
   }

   private static int intVal(Object o) {
      return o != null ? ((Number) o).intValue() : 0;
   }

   private static Double doubleOrNull(Object o) {
      if (o instanceof Number) {
         return ((Number) o).doubleValue();
      }
      return null;
   }

   private static void compareEvent(IPersistentMap clj, AcqEvent java,
         int idx) {
      String at = "Event[" + idx + "] ";
      assertEquals(at + "frameIndex",
            intVal(clj.valAt(KW_FRAME_INDEX)), java.frameIndex);
      assertEquals(at + "sliceIndex",
            intVal(clj.valAt(KW_SLICE_INDEX)), java.sliceIndex);
      assertEquals(at + "channelIndex",
            intVal(clj.valAt(KW_CHANNEL_INDEX)), java.channelIndex);
      assertEquals(at + "positionIndex",
            intVal(clj.valAt(KW_POSITION_INDEX)), java.positionIndex);
      assertEquals(at + "exposure",
            ((Number) clj.valAt(KW_EXPOSURE)).doubleValue(),
            java.exposure, 0.0001);
      assertEquals(at + "slice",
            doubleOrNull(clj.valAt(KW_SLICE)), java.slice);

      Object cljWait = clj.valAt(KW_WAIT_TIME_MS);
      if (cljWait == null) {
         assertNull(at + "waitTimeMs", java.waitTimeMs);
      } else {
         assertNotNull(at + "waitTimeMs", java.waitTimeMs);
         assertEquals(at + "waitTimeMs",
               ((Number) cljWait).doubleValue(), java.waitTimeMs,
               0.0001);
      }

      assertEquals(at + "autofocus",
            Boolean.TRUE.equals(clj.valAt(KW_AUTOFOCUS)),
            java.autofocus);
      assertEquals(at + "newPosition",
            Boolean.TRUE.equals(clj.valAt(KW_NEW_POSITION)),
            java.newPosition);
      assertEquals(at + "closeShutter",
            Boolean.TRUE.equals(clj.valAt(KW_CLOSE_SHUTTER)),
            java.closeShutter);
      assertEquals(at + "relativeZ",
            Boolean.TRUE.equals(clj.valAt(KW_RELATIVE_Z)),
            java.relativeZ);
      Object cljTask = clj.valAt(KW_TASK);
      assertEquals(at + "task",
            cljTask instanceof Keyword
                  ? ((Keyword) cljTask).getName() : null,
            java.task);

      Object cljNext = clj.valAt(KW_NEXT_FRAME_INDEX);
      if (cljNext == null) {
         assertNull(at + "nextFrameIndex", java.nextFrameIndex);
      } else {
         assertEquals(at + "nextFrameIndex",
               Integer.valueOf(intVal(cljNext)), java.nextFrameIndex);
      }

      Object cljChannel = clj.valAt(KW_CHANNEL);
      if (cljChannel == null) {
         assertNull(at + "channel", java.channel);
      } else {
         assertNotNull(at + "channel", java.channel);
         assertEquals(at + "channel.name",
               ((IPersistentMap) cljChannel).valAt(KW_NAME),
               java.channel.name);
      }

      Object cljBurst = clj.valAt(KW_BURST_LENGTH);
      if (cljBurst != null) {
         assertEquals(at + "burstLength",
               intVal(cljBurst), java.burstLength);
      }
   }

   private static List<Object> realizeCljSeq(Object cljResult) {
      List<Object> result = new ArrayList<>();
      Object seq = ((clojure.lang.IFn) RT.var("clojure.core", "seq"))
            .invoke(cljResult);
      if (seq == null) {
         return result;
      }
      ISeq s = (ISeq) seq;
      while (s != null) {
         result.add(s.first());
         s = s.next();
      }
      return result;
   }

   private void runComparison(AcqSettings settings) {
      Object cljSettings = toClojureSettings(settings);
      Object cljResult = ((clojure.lang.IFn) cljGenerate)
            .invoke(cljSettings, null);
      List<Object> cljEvents = realizeCljSeq(cljResult);

      List<AcqEvent> javaResult = SequenceGenerator.generateAcqSequence(
            settings, null, noBurstCore);

      assertEquals("Event count", cljEvents.size(), javaResult.size());
      for (int i = 0; i < cljEvents.size(); i++) {
         compareEvent((IPersistentMap) cljEvents.get(i),
               javaResult.get(i), i);
      }
   }

   private static AcqSettings makeSettings(int nFrames, int nSlices,
         int nChannels, int nPositions,
         boolean slicesFirst, boolean timeFirst) {
      AcqSettings s = new AcqSettings();
      s.numFrames = nFrames;
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
         ch.useChannel = true;
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
      s.defaultExposure = 10;
      s.intervalMs = 0;
      s.relativeSlices = false;
      s.keepShutterOpenSlices = false;
      s.keepShutterOpenChannels = false;
      s.useAutofocus = false;
      s.autofocusSkip = 0;
      s.customIntervalsMs = new ArrayList<>();
      return s;
   }

   // --- Test cases covering the settings matrix ---

   @Test
   public void testSingleFrame() {
      runComparison(makeSettings(1, 0, 0, 0, false, false));
   }

   @Test
   public void testFramesOnly() {
      runComparison(makeSettings(3, 0, 0, 0, false, false));
   }

   @Test
   public void testSlicesFirstTimeFirst() {
      runComparison(makeSettings(2, 3, 2, 0, true, true));
   }

   @Test
   public void testSlicesFirstPositionFirst() {
      runComparison(makeSettings(2, 3, 2, 2, true, false));
   }

   @Test
   public void testChannelsFirstTimeFirst() {
      runComparison(makeSettings(2, 3, 2, 0, false, true));
   }

   @Test
   public void testChannelsFirstPositionFirst() {
      runComparison(makeSettings(2, 3, 2, 2, false, false));
   }

   @Test
   public void testMultiplePositionsTimeFirst() {
      runComparison(makeSettings(2, 2, 1, 3, false, true));
   }

   @Test
   public void testMultiplePositionsPositionFirst() {
      runComparison(makeSettings(2, 2, 1, 3, false, false));
   }

   @Test
   public void testKeepShutterOpenSlices() {
      AcqSettings s = makeSettings(2, 3, 1, 0, true, false);
      s.keepShutterOpenSlices = true;
      runComparison(s);
   }

   @Test
   public void testKeepShutterOpenChannels() {
      AcqSettings s = makeSettings(1, 2, 2, 0, false, false);
      s.keepShutterOpenChannels = true;
      runComparison(s);
   }

   @Test
   public void testKeepShutterOpenBoth() {
      AcqSettings s = makeSettings(2, 3, 2, 0, true, false);
      s.keepShutterOpenSlices = true;
      s.keepShutterOpenChannels = true;
      runComparison(s);
   }

   @Test
   public void testAutofocus() {
      AcqSettings s = makeSettings(4, 2, 1, 0, true, false);
      s.useAutofocus = true;
      s.autofocusSkip = 1;
      runComparison(s);
   }

   @Test
   public void testAutofocusWithPositions() {
      AcqSettings s = makeSettings(3, 0, 1, 2, false, true);
      s.useAutofocus = true;
      s.autofocusSkip = 0;
      runComparison(s);
   }

   @Test
   public void testSkipFrames() {
      AcqSettings s = makeSettings(6, 0, 0, 0, false, false);
      s.channels = new ArrayList<>();
      AcqChannel ch = new AcqChannel();
      ch.name = "DAPI";
      ch.exposure = 100;
      ch.useZStack = true;
      ch.useChannel = true;
      ch.skipFrames = 2;
      ch.properties = Collections.emptyMap();
      s.channels.add(ch);
      runComparison(s);
   }

   @Test
   public void testUseZStackFalse() {
      AcqSettings s = makeSettings(1, 3, 0, 0, true, false);
      s.channels = new ArrayList<>();
      AcqChannel ch = new AcqChannel();
      ch.name = "BF";
      ch.exposure = 50;
      ch.useZStack = false;
      ch.useChannel = true;
      ch.skipFrames = 0;
      ch.properties = Collections.emptyMap();
      s.channels.add(ch);
      runComparison(s);
   }

   @Test
   public void testMixedZStack() {
      AcqSettings s = makeSettings(1, 3, 0, 0, true, false);
      s.channels = new ArrayList<>();
      AcqChannel ch1 = new AcqChannel();
      ch1.name = "DAPI";
      ch1.exposure = 100;
      ch1.useZStack = true;
      ch1.useChannel = true;
      ch1.skipFrames = 0;
      ch1.properties = Collections.emptyMap();
      AcqChannel ch2 = new AcqChannel();
      ch2.name = "BF";
      ch2.exposure = 100;
      ch2.useZStack = false;
      ch2.useChannel = true;
      ch2.skipFrames = 0;
      ch2.properties = Collections.emptyMap();
      s.channels.add(ch1);
      s.channels.add(ch2);
      runComparison(s);
   }

   @Test
   public void testCustomIntervals() {
      AcqSettings s = makeSettings(3, 2, 1, 0, true, false);
      s.customIntervalsMs = Arrays.asList(500.0, 1000.0, 1500.0);
      runComparison(s);
   }

   @Test
   public void testFixedInterval() {
      AcqSettings s = makeSettings(3, 2, 1, 0, true, false);
      s.intervalMs = 5000.0;
      runComparison(s);
   }

   @Test
   public void testRelativeSlices() {
      AcqSettings s = makeSettings(1, 3, 1, 0, true, false);
      s.relativeSlices = true;
      runComparison(s);
   }

   @Test
   public void testLargeAcquisition() {
      runComparison(makeSettings(5, 3, 3, 3, true, true));
   }
}

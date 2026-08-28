package org.micromanager.internal.jacque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import mmcorej.CMMCore;
import org.micromanager.PositionList;
import org.micromanager.acquisition.SequenceSettings;
import org.junit.BeforeClass;
import org.junit.Test;

public class AcqSettingsTest {

   private static ExecutionCoreOps core;

   @BeforeClass
   public static void setup() throws Exception {
      core = ExecutionCoreOps.fromCMMCore(new CMMCore());
   }

   @Test
   public void testBasicFields() throws Exception {
      SequenceSettings ss = new SequenceSettings.Builder()
            .numFrames(5)
            .intervalMs(2000.0)
            .slicesFirst(true)
            .timeFirst(true)
            .keepShutterOpenSlices(true)
            .keepShutterOpenChannels(true)
            .useAutofocus(true)
            .skipAutofocusCount(3)
            .relativeZSlice(true)
            .cameraTimeout(30000)
            .save(true)
            .root("/tmp/data")
            .prefix("test_acq")
            .comment("A test")
            .channelGroup("Channel")
            .build();

      AcqSettings s = AcqSettings.fromSequenceSettings(ss, null, core);

      assertEquals(5, s.numFrames);
      assertEquals(5, s.frames.size());
      for (int i = 0; i < 5; i++) {
         assertEquals(Integer.valueOf(i), s.frames.get(i));
      }
      assertEquals(2000.0, s.intervalMs, 0.0);
      assertTrue(s.slicesFirst);
      assertTrue(s.timeFirst);
      assertTrue(s.keepShutterOpenSlices);
      assertTrue(s.keepShutterOpenChannels);
      assertTrue(s.useAutofocus);
      assertEquals(3, s.autofocusSkip);
      assertTrue(s.relativeSlices);
      assertEquals(30000, s.cameraTimeout);
      assertTrue(s.save);
      assertEquals("/tmp/data", s.root);
      assertEquals("test_acq", s.prefix);
      assertEquals("A test", s.comment);
      assertEquals("Channel", s.channelGroup);
   }

   @Test
   public void testSlices() throws Exception {
      ArrayList<Double> slices = new ArrayList<>(
            Arrays.asList(-2.0, 0.0, 2.0, 4.0));
      SequenceSettings ss = new SequenceSettings.Builder()
            .slices(slices)
            .build();

      AcqSettings s = AcqSettings.fromSequenceSettings(ss, null, core);

      assertEquals(4, s.slices.size());
      assertEquals(-2.0, s.slices.get(0), 0.0);
      assertEquals(0.0, s.slices.get(1), 0.0);
      assertEquals(2.0, s.slices.get(2), 0.0);
      assertEquals(4.0, s.slices.get(3), 0.0);
   }

   @Test
   public void testPositionsNull() throws Exception {
      SequenceSettings ss = new SequenceSettings.Builder()
            .usePositionList(false)
            .build();

      AcqSettings s = AcqSettings.fromSequenceSettings(ss, null, core);

      assertNull(s.positions);
      assertFalse(s.usePositionList);
   }

   @Test
   public void testPositionsWithList() throws Exception {
      PositionList pl = new PositionList();
      pl.addPosition(new org.micromanager.MultiStagePosition());
      pl.addPosition(new org.micromanager.MultiStagePosition());
      pl.addPosition(new org.micromanager.MultiStagePosition());

      SequenceSettings ss = new SequenceSettings.Builder()
            .usePositionList(true)
            .build();

      AcqSettings s = AcqSettings.fromSequenceSettings(ss, pl, core);

      assertNotNull(s.positions);
      assertEquals(3, s.positions.size());
      assertEquals(Integer.valueOf(0), s.positions.get(0));
      assertEquals(Integer.valueOf(1), s.positions.get(1));
      assertEquals(Integer.valueOf(2), s.positions.get(2));
      assertTrue(s.usePositionList);
   }

   @Test
   public void testCustomIntervals() throws Exception {
      ArrayList<Double> custom = new ArrayList<>(
            Arrays.asList(0.0, 500.0, 1500.0));
      SequenceSettings ss = new SequenceSettings.Builder()
            .customIntervalsMs(custom)
            .build();

      AcqSettings s = AcqSettings.fromSequenceSettings(ss, null, core);

      assertEquals(3, s.customIntervalsMs.size());
      assertEquals(0.0, s.customIntervalsMs.get(0), 0.0);
      assertEquals(500.0, s.customIntervalsMs.get(1), 0.0);
      assertEquals(1500.0, s.customIntervalsMs.get(2), 0.0);
   }

   @Test
   public void testCustomIntervalsNull() throws Exception {
      SequenceSettings ss = new SequenceSettings.Builder().build();

      AcqSettings s = AcqSettings.fromSequenceSettings(ss, null, core);

      assertNotNull(s.customIntervalsMs);
      assertTrue(s.customIntervalsMs.isEmpty());
   }

   @Test
   public void testNoChannels() throws Exception {
      SequenceSettings ss = new SequenceSettings.Builder().build();

      AcqSettings s = AcqSettings.fromSequenceSettings(ss, null, core);

      assertNotNull(s.channels);
      assertTrue(s.channels.isEmpty());
   }

   @Test
   public void testDefaultExposureFromCore() throws Exception {
      SequenceSettings ss = new SequenceSettings.Builder().build();

      AcqSettings s = AcqSettings.fromSequenceSettings(ss, null, core);

      assertEquals(core.getExposure(), s.defaultExposure, 0.0);
   }

   @Test
   public void testDefaults() throws Exception {
      SequenceSettings ss = new SequenceSettings.Builder().build();

      AcqSettings s = AcqSettings.fromSequenceSettings(ss, null, core);

      assertEquals(1, s.numFrames);
      assertFalse(s.slicesFirst);
      assertFalse(s.timeFirst);
      assertFalse(s.keepShutterOpenSlices);
      assertFalse(s.keepShutterOpenChannels);
      assertFalse(s.useAutofocus);
      assertEquals(0, s.autofocusSkip);
      assertFalse(s.save);
      assertFalse(s.usePositionList);
   }
}

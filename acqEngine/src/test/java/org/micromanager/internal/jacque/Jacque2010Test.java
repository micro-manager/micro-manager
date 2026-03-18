package org.micromanager.internal.jacque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import mmcorej.CMMCore;
import org.junit.BeforeClass;
import org.junit.Test;
import org.micromanager.PositionList;
import org.micromanager.acquisition.SequenceSettings;

public class Jacque2010Test {

   private static ExecutionCoreOps core;

   @BeforeClass
   public static void setup() throws Exception {
      core = ExecutionCoreOps.fromCMMCore(new CMMCore());
   }

   @Test
   public void testConvertSettings() throws Exception {
      SequenceSettings ss = new SequenceSettings.Builder()
            .numFrames(3)
            .slicesFirst(true)
            .timeFirst(false)
            .intervalMs(1000)
            .save(true)
            .root("/tmp/claude/test")
            .prefix("acq")
            .build();

      AcqSettings settings = AcqSettings.fromSequenceSettings(
            ss, new PositionList(), core);

      assertEquals(3, settings.numFrames);
      assertEquals(3, settings.frames.size());
      assertTrue(settings.slicesFirst);
      assertFalse(settings.timeFirst);
      assertEquals(1000.0, settings.intervalMs, 0.0);
      assertTrue(settings.save);
      assertEquals("/tmp/claude/test", settings.root);
      assertEquals("acq", settings.prefix);
   }
}

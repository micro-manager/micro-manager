package org.micromanager.acqenginetests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.internal.MMStudio;

/**
 * Time-lapse acquisition tests, run through both engines.
 */
@RunWith(Parameterized.class)
public class TimeLapseTest {
   private static StudioTestFixture fixture_;

   @Parameterized.Parameter
   public boolean useAcqEngJ_;

   @Parameterized.Parameters(name = "acqEngJ={0}")
   public static List<Object[]> engines() {
      return Arrays.asList(new Object[]{false}, new Object[]{true});
   }

   @BeforeClass
   public static void boot() {
      fixture_ = StudioTestFixture.getInstance();
   }

   @Before
   public void setUp() throws Exception {
      fixture_.reset();
      fixture_.useAcqEngJ(useAcqEngJ_);
   }

   @Test
   public void fixedIntervalProducesOneImagePerFrame() throws Exception {
      MMStudio studio = fixture_.getStudio();

      final int numFrames = 5;
      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useFrames(true)
            .numFrames(numFrames)
            .intervalMs(1.0)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);

      result.assertHasAllCoords(numFrames, 1, 1, 1);
      assertEquals("time axis length", numFrames, result.axisLength(Coords.T));
   }

   @Test
   public void customIntervalsProduceOneImagePerInterval() throws Exception {
      MMStudio studio = fixture_.getStudio();

      ArrayList<Double> intervals = new ArrayList<>(Arrays.asList(0.0, 5.0, 10.0));
      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useFrames(true)
            .numFrames(intervals.size())
            .useCustomIntervals(true)
            .customIntervalsMs(intervals)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);

      result.assertHasAllCoords(intervals.size(), 1, 1, 1);
      assertEquals("time axis length",
            intervals.size(), result.axisLength(Coords.T));
   }

   @Test
   public void framesAreCapturedInOrder() throws Exception {
      MMStudio studio = fixture_.getStudio();

      final int numFrames = 4;
      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useFrames(true)
            .numFrames(numFrames)
            .intervalMs(1.0)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);
      result.assertHasAllCoords(numFrames, 1, 1, 1);

      // The camera's cumulative image counter must be monotonically increasing
      // with the time index (frame t was produced after frame t-1).
      long[] cumulativeByFrame = new long[numFrames];
      Arrays.fill(cumulativeByFrame, -1L);
      for (Coords c : store.getUnorderedImageCoords()) {
         int t = c.hasAxis(Coords.T) ? c.getIndex(Coords.T) : 0;
         cumulativeByFrame[t] =
               result.infoPacketAt(c).camera.cumulativeImageNr;
      }
      for (int t = 1; t < numFrames; t++) {
         assertTrue("frame " + t + " should be captured after frame " + (t - 1),
               cumulativeByFrame[t] > cumulativeByFrame[t - 1]);
      }
   }
}

package org.micromanager.acqenginetests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.AcqOrderMode;
import org.micromanager.testing.TestImageDecoder.InfoPacket;

/**
 * Multi-position acquisition tests, run through both engines.
 */
@RunWith(Parameterized.class)
public class MultiPositionTest {
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

   /** Builds a position list with the given XY coordinates on TXYStage. */
   private PositionList makePositionList(double[][] xys) {
      PositionList pl = new PositionList();
      for (int i = 0; i < xys.length; i++) {
         MultiStagePosition msp = new MultiStagePosition();
         msp.setLabel("Pos" + i);
         msp.add(StagePosition.create2D("TXYStage", xys[i][0], xys[i][1]));
         pl.addPosition(msp);
      }
      return pl;
   }

   @Test
   public void onePositionPerListEntry() throws Exception {
      MMStudio studio = fixture_.getStudio();
      PositionList pl = makePositionList(new double[][]{
            {0.0, 0.0}, {100.0, 0.0}, {100.0, 100.0}});
      fixture_.setPositionList(pl);

      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .usePositionList(true)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);

      // frames=1, channels=1, slices=1, positions=3
      result.assertHasAllCoords(1, 1, 1, 3);
      assertEquals("position axis length", 3, result.axisLength(Coords.P));
   }

   @Test
   public void xyStageMovedForEachPosition() throws Exception {
      MMStudio studio = fixture_.getStudio();
      PositionList pl = makePositionList(new double[][]{
            {0.0, 0.0}, {100.0, 200.0}});
      fixture_.setPositionList(pl);

      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .usePositionList(true)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);
      result.assertHasAllCoords(1, 1, 1, 2);

      // The two position images must have been captured at two distinct XY
      // stage positions.
      Set<String> xyByPosition = new HashSet<>();
      for (Coords c : store.getUnorderedImageCoords()) {
         InfoPacket p = result.infoPacketAt(c);
         String x = AcqResult.currentStateString(p, "TXYStage", "XPositionSteps");
         String y = AcqResult.currentStateString(p, "TXYStage", "YPositionSteps");
         xyByPosition.add(x + "," + y);
      }
      assertEquals("each position should have a distinct XY stage position",
            2, xyByPosition.size());
   }

   @Test
   public void positionTimeOrderingProducesAllImages() throws Exception {
      MMStudio studio = fixture_.getStudio();
      PositionList pl = makePositionList(new double[][]{
            {0.0, 0.0}, {50.0, 50.0}});
      fixture_.setPositionList(pl);

      // 2 positions x 2 frames, position-major ordering.
      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .usePositionList(true)
            .useFrames(true)
            .numFrames(2)
            .intervalMs(1.0)
            .acqOrderMode(AcqOrderMode.POS_TIME_CHANNEL_SLICE)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);

      // frames=2, channels=1, slices=1, positions=2 -> 4 images
      result.assertHasAllCoords(2, 1, 1, 2);
      assertTrue("position axis present", result.axisLength(Coords.P) == 2);
      assertTrue("time axis present", result.axisLength(Coords.T) == 2);
   }
}

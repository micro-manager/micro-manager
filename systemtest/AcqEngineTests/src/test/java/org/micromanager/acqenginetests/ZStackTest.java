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
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.internal.MMStudio;
import org.micromanager.testing.TestImageDecoder.InfoPacket;

/**
 * Z-stack acquisition tests, run through both engines.
 *
 * <p>Also ports the spirit of SequenceTests' RegressionSpuriousZPositionSets to
 * the high-level path: the focus drive must be set when (and only when) a Z
 * stack is requested.
 */
@RunWith(Parameterized.class)
public class ZStackTest {
   private static final String GROUP = "Channel";
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
   public void zStackProducesOneImagePerSlice() throws Exception {
      MMStudio studio = fixture_.getStudio();

      // Bottom 0, top 4, step 1 -> 5 slices.
      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useSlices(true)
            .sliceZBottomUm(0.0)
            .sliceZTopUm(4.0)
            .sliceZStepUm(1.0)
            .relativeZSlice(true)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);

      // frames=1, channels=1, slices=5, positions=1
      result.assertHasAllCoords(1, 1, 5, 1);
      assertEquals("z axis length", 5, result.axisLength(Coords.Z));

      // The focus drive should have moved (Z set) for the stack.
      boolean zMoved = false;
      for (InfoPacket p : result.infoPackets()) {
         if (p.hasBeenSetSincePreviousPacket("TZStage", "ZPositionUm")) {
            zMoved = true;
         }
      }
      assertTrue("TZStage ZPositionUm should be set during a Z stack", zMoved);
   }

   @Test
   public void zNotSetForChannelOnlyAcquisition() throws Exception {
      MMStudio studio = fixture_.getStudio();
      fixture_.defineChannelGroup(GROUP, "Ch0", "Ch1");

      ArrayList<ChannelSpec> channels = new ArrayList<>(Arrays.asList(
            fixture_.channel(GROUP, "Ch0", 1.0),
            fixture_.channel(GROUP, "Ch1", 1.0)));

      // Channels, no slices, no frames -- the focus drive must not be touched.
      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useChannels(true)
            .channelGroup(GROUP)
            .channels(channels)
            .useSlices(false)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);
      result.assertHasAllCoords(1, 2, 1, 1);

      // KNOWN ENGINE DIFFERENCE:
      // - The legacy engine leaves the focus drive untouched for a channel-only
      //   MDA (this is what RegressionSpuriousZPositionSets guards against).
      // - AcqEngJ deliberately inserts a single-slice "fake z stack" at the
      //   current/relative origin so that per-channel z-offsets and autofocus
      //   are handled uniformly (see AcqEngJAdapter.createAcqEventIterator).
      //   It therefore sets Z once, to a constant value. It must NOT, however,
      //   perform a multi-slice stack.
      int zSets = 0;
      for (InfoPacket p : result.infoPackets()) {
         if (p.hasBeenSetSincePreviousPacket("TZStage", "ZPositionUm")) {
            zSets++;
         }
      }
      if (useAcqEngJ_) {
         // The images all live on a single Z index (no multi-slice stack)...
         assertEquals("AcqEngJ should not produce a multi-slice Z axis",
               1, result.axisLength(Coords.Z));
         // ...and Z is set at most once (to the single fake-stack origin). This
         // guards against AcqEngJ regressing into redundant Z sets per image.
         assertTrue("AcqEngJ should set Z at most once for a channel-only MDA "
               + "(was " + zSets + ")", zSets <= 1);
      } else {
         assertEquals("legacy engine must not touch Z for a channel-only MDA",
               0, zSets);
      }
   }
}

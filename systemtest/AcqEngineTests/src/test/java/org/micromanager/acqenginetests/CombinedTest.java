package org.micromanager.acqenginetests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.AcqOrderMode;

/**
 * Full multi-axis MDAs combining channels, Z, time and positions, run through
 * both engines. These catch cross-axis interaction bugs (the class of issue
 * behind #2352), where the number/coordinates of images come out wrong only
 * when several axes are combined.
 */
@RunWith(Parameterized.class)
public class CombinedTest {
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
      fixture_.defineChannelGroup(GROUP, "Ch0", "Ch1");
      fixture_.useAcqEngJ(useAcqEngJ_);
   }

   private ArrayList<ChannelSpec> twoChannels() {
      return new ArrayList<>(Arrays.asList(
            fixture_.channel(GROUP, "Ch0", 1.0),
            fixture_.channel(GROUP, "Ch1", 1.0)));
   }

   @Test
   public void channelsTimeZStack() throws Exception {
      MMStudio studio = fixture_.getStudio();

      final int numFrames = 2;
      final int numChannels = 2;
      final int numSlices = 3;  // bottom 0, top 2, step 1

      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useFrames(true)
            .numFrames(numFrames)
            .intervalMs(1.0)
            .useChannels(true)
            .channelGroup(GROUP)
            .channels(twoChannels())
            .useSlices(true)
            .sliceZBottomUm(0.0)
            .sliceZTopUm(2.0)
            .sliceZStepUm(1.0)
            .relativeZSlice(true)
            .acqOrderMode(AcqOrderMode.TIME_POS_SLICE_CHANNEL)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      AcqResult result = new AcqResult(
            studio.acquisitions().runAcquisitionWithSettings(settings, true));

      result.assertHasAllCoords(numFrames, numChannels, numSlices, 1);
   }

   @Test
   public void channelsTimeZStackPositions() throws Exception {
      MMStudio studio = fixture_.getStudio();

      PositionList pl = new PositionList();
      for (int i = 0; i < 2; i++) {
         MultiStagePosition msp = new MultiStagePosition();
         msp.setLabel("Pos" + i);
         msp.add(StagePosition.create2D("TXYStage", 100.0 * i, 0.0));
         pl.addPosition(msp);
      }
      fixture_.setPositionList(pl);

      final int numFrames = 2;
      final int numChannels = 2;
      final int numSlices = 2;  // bottom 0, top 1, step 1
      final int numPositions = 2;

      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useFrames(true)
            .numFrames(numFrames)
            .intervalMs(1.0)
            .useChannels(true)
            .channelGroup(GROUP)
            .channels(twoChannels())
            .useSlices(true)
            .sliceZBottomUm(0.0)
            .sliceZTopUm(1.0)
            .sliceZStepUm(1.0)
            .relativeZSlice(true)
            .usePositionList(true)
            .acqOrderMode(AcqOrderMode.POS_TIME_SLICE_CHANNEL)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      AcqResult result = new AcqResult(
            studio.acquisitions().runAcquisitionWithSettings(settings, true));

      result.assertHasAllCoords(numFrames, numChannels, numSlices, numPositions);
   }
}

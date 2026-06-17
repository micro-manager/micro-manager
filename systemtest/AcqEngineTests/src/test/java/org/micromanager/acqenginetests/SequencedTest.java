package org.micromanager.acqenginetests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.micromanager.data.Datastore;
import org.micromanager.internal.MMStudio;
import org.micromanager.testing.TestImageDecoder.InfoPacket;

/**
 * Hardware-sequenced (triggered) vs. software (non-sequenced) channel
 * acquisitions, run through both engines.
 *
 * <p>When the channel switcher is wired to be triggered by the camera, the
 * engine should run the channels as a hardware sequence (burst), which the
 * SequenceTester camera records as {@code isSequence == true}. Without that
 * wiring, each image is acquired individually ({@code isSequence == false}).
 */
@RunWith(Parameterized.class)
public class SequencedTest {
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

   private ArrayList<ChannelSpec> twoChannels(double exposureMs) {
      return new ArrayList<>(Arrays.asList(
            fixture_.channel(GROUP, "Ch0", exposureMs),
            fixture_.channel(GROUP, "Ch1", exposureMs)));
   }

   @Test
   public void nonSequencedChannelsAreNotABurst() throws Exception {
      MMStudio studio = fixture_.getStudio();

      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useChannels(true)
            .channelGroup(GROUP)
            .channels(twoChannels(1.0))
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);
      result.assertHasAllCoords(1, 2, 1, 1);

      for (InfoPacket p : result.infoPackets()) {
         assertFalse("channels without trigger wiring should not be a sequence",
               p.camera.isSequence);
      }
   }

   @Test
   public void triggeredChannelsRunAsHardwareSequence() throws Exception {
      MMStudio studio = fixture_.getStudio();

      // Wire the switcher to be triggered by the camera's exposure, so the
      // engine can drive the channel change as a hardware sequence.
      studio.core().setProperty("TSwitcher", "TriggerSourceDevice", "TCamera");
      studio.core().setProperty("TSwitcher", "TriggerSourcePort",
            "ExposureStartEdge");
      studio.core().setProperty("TSwitcher", "TriggerSequenceMaxLength", 2);
      fixture_.drainEdt();

      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useChannels(true)
            .channelGroup(GROUP)
            // Long enough interval that the engine is free to sequence channels.
            .channels(twoChannels(1.0))
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);
      result.assertHasAllCoords(1, 2, 1, 1);

      // At least one image should report being part of a camera sequence, and
      // the per-frame numbers within the burst should be 0 and 1.
      boolean anySequence = false;
      for (InfoPacket p : result.infoPackets()) {
         if (p.camera.isSequence) {
            anySequence = true;
            assertTrue("sequence frame number in range",
                  p.camera.frameNr == 0 || p.camera.frameNr == 1);
         }
      }
      assertTrue("triggered channels should run as a hardware sequence on at "
            + "least one engine path", anySequence);
   }
}

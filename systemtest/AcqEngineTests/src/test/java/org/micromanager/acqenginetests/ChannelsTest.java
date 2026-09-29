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
 * Multi-channel acquisition tests, run through both engines.
 *
 * <p>Parameterized over {@code useAcqEngJ = {false, true}} so every scenario is
 * exercised on both the legacy Clojure engine and AcqEngJ.
 */
@RunWith(Parameterized.class)
public class ChannelsTest {
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
      fixture_.defineChannelGroup(GROUP, "Ch0", "Ch1", "Ch2");
      fixture_.useAcqEngJ(useAcqEngJ_);
   }

   @Test
   public void threeChannelsProduceThreeImages() throws Exception {
      MMStudio studio = fixture_.getStudio();

      ArrayList<ChannelSpec> channels = new ArrayList<>(Arrays.asList(
            fixture_.channel(GROUP, "Ch0", 1.0),
            fixture_.channel(GROUP, "Ch1", 1.0),
            fixture_.channel(GROUP, "Ch2", 1.0)));

      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useChannels(true)
            .channelGroup(GROUP)
            .channels(channels)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);

      // frames=1, channels=3, slices=1, positions=1
      result.assertHasAllCoords(1, 3, 1, 1);
      assertEquals("channel axis length", 3, result.axisLength(Coords.CHANNEL));

      // Every image should carry a decodable InfoPacket from TCamera.
      for (InfoPacket p : result.infoPackets()) {
         assertEquals("TCamera", p.camera.name);
      }
   }

   @Test
   public void channelSwitcherIsSetForEachChannel() throws Exception {
      MMStudio studio = fixture_.getStudio();

      ArrayList<ChannelSpec> channels = new ArrayList<>(Arrays.asList(
            fixture_.channel(GROUP, "Ch0", 1.0),
            fixture_.channel(GROUP, "Ch1", 1.0)));

      SequenceSettings settings = studio.acquisitions().sequenceSettingsBuilder()
            .useChannels(true)
            .channelGroup(GROUP)
            .channels(channels)
            .save(false)
            .shouldDisplayImages(false)
            .build();

      Datastore store =
            studio.acquisitions().runAcquisitionWithSettings(settings, true);
      AcqResult result = new AcqResult(store);
      result.assertHasAllCoords(1, 2, 1, 1);

      // The two channel images must have been captured with the TSwitcher in
      // two different positions (Ch0 -> 0, Ch1 -> 1). The MM "State" property of
      // TSwitcher is backed by the internal "Position" setting (see
      // TesterSwitcher::Initialize), so that is the key recorded in the packet.
      String ch0Pos = AcqResult.currentStateString(
            channelPacket(result, 0), "TSwitcher", "Position");
      String ch1Pos = AcqResult.currentStateString(
            channelPacket(result, 1), "TSwitcher", "Position");
      assertEquals("0", ch0Pos);
      assertEquals("1", ch1Pos);
   }

   /** The InfoPacket of the image at the given channel index (z=t=p=0). */
   private static InfoPacket channelPacket(AcqResult result, int channel)
         throws java.io.IOException {
      for (Coords c : result.store().getUnorderedImageCoords()) {
         int ch = c.hasAxis(Coords.CHANNEL) ? c.getIndex(Coords.CHANNEL) : 0;
         if (ch == channel) {
            return result.infoPacketAt(c);
         }
      }
      throw new IllegalStateException("no image for channel " + channel);
   }
}

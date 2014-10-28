package org.micromanager.ae2010tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mmcorej.CMMCore;
import org.junit.Rule;
import org.junit.Test;
import org.micromanager.AcquisitionEngine2010;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.SequenceSettings;
import org.micromanager.testing.AE2010ImageDecoder;
import org.micromanager.testing.MMCoreWithTestHubResource;
import org.micromanager.testing.TaggedImageDecoder;
import org.micromanager.utils.ChannelSpec;
import static org.junit.Assert.*;
import static org.micromanager.testing.TestImageDecoder.InfoPacket;


public class RegressionSpuriousZPositionSets {
   @Rule
   public MMCoreWithTestHubResource coreResource =
      new MMCoreWithTestHubResource();

   @Test
   public void zPositionNotSetForTimeChannelMDA() throws Exception {
      String camera = "TCamera";
      String zStage = "TZStage";
      String wheel = "TSwitcher";
      coreResource.prepareTestDevices(camera, zStage, wheel);

      CMMCore mmc = coreResource.getMMCore();
      mmc.setCameraDevice(camera);
      mmc.setFocusDevice(zStage);

      String channelGroup = "Channel";
      String ch0Preset = "Ch0", ch1Preset = "Ch1";
      mmc.defineConfig(channelGroup, ch0Preset, wheel, "State", "1");
      mmc.defineConfig(channelGroup, ch1Preset, wheel, "State", "2");

      IAcquisitionEngine2010 ae2010 = new AcquisitionEngine2010(mmc);

      double exposure = 1.0;

      SequenceSettings mdaSeq = new SequenceSettings();
      mdaSeq.numFrames = 2;
      mdaSeq.intervalMs = exposure + 1.0;
      mdaSeq.channelGroup = channelGroup;
      mdaSeq.channels = new ArrayList<ChannelSpec>(Arrays.asList(
               new ChannelSpec[] {
                  new ChannelSpec(),
                  new ChannelSpec()
               }));
      mdaSeq.channels.get(0).config = ch0Preset;
      mdaSeq.channels.get(0).exposure = exposure;
      mdaSeq.channels.get(1).config = ch1Preset;
      mdaSeq.channels.get(1).exposure = exposure;

      List<InfoPacket> packets = AE2010ImageDecoder.collectImages(
            ae2010.run(mdaSeq, true, null, null));

      assertEquals(4, packets.size());

      for (InfoPacket packet : packets) {
         assertFalse(packet.hasBeenSetSincePreviousPacket(zStage,
                  "ZPositionUm"));
      }

      // Check also that Z is not set after the last image
      InfoPacket afterLast = TaggedImageDecoder.snapAndDecode(mmc);
      assertFalse(afterLast.hasBeenSetSincePreviousPacket(zStage,
               "ZPositionUm"));
   }

   @Test
   public void zPositionNotSetForTimeOnlyMDA() throws Exception {
      String camera = "TCamera";
      String zStage = "TZStage";
      coreResource.prepareTestDevices(camera, zStage);

      CMMCore mmc = coreResource.getMMCore();
      mmc.setCameraDevice(camera);
      mmc.setFocusDevice(zStage);

      IAcquisitionEngine2010 ae2010 = new AcquisitionEngine2010(mmc);

      double exposure = 1.0;
      mmc.setExposure(exposure);

      SequenceSettings mdaSeq = new SequenceSettings();
      mdaSeq.numFrames = 2;
      mdaSeq.intervalMs = exposure + 1.0;

      List<InfoPacket> packets = AE2010ImageDecoder.collectImages(
            ae2010.run(mdaSeq, true, null, null));

      assertEquals(2, packets.size());

      for (InfoPacket packet : packets) {
         assertFalse(packet.hasBeenSetSincePreviousPacket(zStage,
                  "ZPositionUm"));
      }

      // Check also that Z is not set after the last image
      InfoPacket afterLast = TaggedImageDecoder.snapAndDecode(mmc);
      assertFalse(afterLast.hasBeenSetSincePreviousPacket(zStage,
               "ZPositionUm"));
   }

   @Test
   public void zPositionNotSetForTimeChannelMDAWithTriggeredChannels() throws Exception {
      String camera = "TCamera";
      String zStage = "TZStage";
      String wheel = "TSwitcher";
      coreResource.prepareTestDevices(camera, zStage, wheel);

      CMMCore mmc = coreResource.getMMCore();
      mmc.setCameraDevice(camera);
      mmc.setFocusDevice(zStage);

      String channelGroup = "Channel";
      String ch0Preset = "Ch0", ch1Preset = "Ch1";
      mmc.defineConfig(channelGroup, ch0Preset, wheel, "State", "1");
      mmc.defineConfig(channelGroup, ch1Preset, wheel, "State", "2");

      mmc.setProperty(wheel, "TriggerSourceDevice", camera);
      mmc.setProperty(wheel, "TriggerSourcePort", "ExposureStartEdge");
      mmc.setProperty(wheel, "TriggerSequenceMaxLength", 2);

      IAcquisitionEngine2010 ae2010 = new AcquisitionEngine2010(mmc);

      double exposure = 1.0;

      SequenceSettings mdaSeq = new SequenceSettings();
      mdaSeq.numFrames = 2;
      mdaSeq.intervalMs = 2 * exposure + 100.0;
      mdaSeq.channelGroup = channelGroup;
      mdaSeq.channels = new ArrayList<ChannelSpec>(Arrays.asList(
               new ChannelSpec[] {
                  new ChannelSpec(),
                  new ChannelSpec()
               }));
      mdaSeq.channels.get(0).config = ch0Preset;
      mdaSeq.channels.get(0).exposure = exposure;
      mdaSeq.channels.get(1).config = ch1Preset;
      mdaSeq.channels.get(1).exposure = exposure;

      List<InfoPacket> packets = AE2010ImageDecoder.collectImages(
            ae2010.run(mdaSeq, true, null, null));

      assertEquals(4, packets.size());

      int i = 0;
      for (InfoPacket packet : packets) {
         // Make sure we ran the expected hardware-triggered acquisition
         assertTrue(packet.camera.isSequence);
         assertEquals(i % 2, packet.camera.frameNr);

         assertFalse(packet.hasBeenSetSincePreviousPacket(zStage,
                  "ZPositionUm"));

         i++;
      }

      // Check also that Z is not set after the last image
      InfoPacket afterLast = TaggedImageDecoder.snapAndDecode(mmc);
      assertFalse(afterLast.hasBeenSetSincePreviousPacket(zStage,
               "ZPositionUm"));
   }
}

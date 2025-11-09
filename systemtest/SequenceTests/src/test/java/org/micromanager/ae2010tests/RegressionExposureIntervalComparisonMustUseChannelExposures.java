package org.micromanager.ae2010tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import mmcorej.CMMCore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.micromanager.internal.AcquisitionEngine2010;
import org.micromanager.acquisition.internal.IAcquisitionEngine2010;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.testing.AE2010ImageDecoder;
import org.micromanager.testing.MMCoreWithTestHubResource;
import org.micromanager.acquisition.ChannelSpec;
import static org.junit.Assert.*;
import static org.micromanager.testing.TestImageDecoder.InfoPacket;


/*
 * When deciding whether an acquisition should be run as a sequence
 * acquisition, and there is one or more channels enabled (trigger-sequenceable
 * channels if multiple), then the decision should not depend on the camera's
 * current exposure.
 *
 * (In an ideal world it would compute the total time for the P, Z, and C axes,
 * but that is very complex. Just ensure that an interval greater than the
 * total of the channel exposures results in proper waits, and that an interval
 * of zero always has the expected behavior.)
 *
 * The effect of the position and Z axes is not tested yet.
 */
@RunWith(Parameterized.class)
public class RegressionExposureIntervalComparisonMustUseChannelExposures {
   @Parameterized.Parameters
   public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][] {
         { 0.0, 1.0, 0, true },
         { 2.0, 1.0, 0, false },
         { 0.0, 0.5, 1, true },
         { 0.0, 1.5, 1, true },
         { 1.5, 0.5, 1, false },
         { 1.5, 2.0, 1, false },
         { 0.0, 0.5, 2, true },
         { 0.0, 2.5, 2, true },
         { 2.5, 0.5, 2, false },
         { 2.5, 3.0, 2, false },
      });
   }

   static final double CHANNEL_EXPOSURE = 1.0;
   final double interval_;
   final double initialCameraExposure_;
   final int nrChannels_;
   final boolean shouldCombineBursts_;

   public RegressionExposureIntervalComparisonMustUseChannelExposures(
         double interval, double initialCameraExposure,
         int nrChannels, boolean shouldCombineBursts)
   {
      interval_ = interval;
      initialCameraExposure_ = initialCameraExposure;
      nrChannels_ = nrChannels;
      shouldCombineBursts_ = shouldCombineBursts;
   }

   @Rule
   public MMCoreWithTestHubResource coreResource =
      new MMCoreWithTestHubResource();

   @Test
   public void burstCombiningIsAppropriate()
      throws Exception
   {
      String camera = "TCamera";
      String wheel = "TSwitcher";
      coreResource.prepareTestDevices(camera, wheel);

      CMMCore mmc = coreResource.getMMCore();
      mmc.setCameraDevice(camera);

      String channelGroup = "Channel";
      String[] channels = new String[nrChannels_];
      for (int i = 0; i < nrChannels_; i++) {
         channels[i] = "Ch" + i;
         mmc.defineConfig(channelGroup, channels[i], wheel, "State",
               Integer.toString(i));
      }

      // Set up triggering with large enough max seq len so that all bursts
      // will be combined unless otherwise prevented.
      mmc.setProperty(wheel, "TriggerSourceDevice", camera);
      mmc.setProperty(wheel, "TriggerSourcePort", "ExposureStartEdge");
      mmc.setProperty(wheel, "TriggerSequenceMaxLength", 1000);

      mmc.setExposure(initialCameraExposure_);

      IAcquisitionEngine2010 ae2010 = new AcquisitionEngine2010(mmc);

      ArrayList<ChannelSpec> channelSpecs = new ArrayList<>();
      for (int i = 0; i < nrChannels_; i++) {
         channelSpecs.add(new ChannelSpec.Builder()
               .config(channels[i])
               .exposure(CHANNEL_EXPOSURE)
               .build());
      }

      SequenceSettings.Builder builder = new SequenceSettings.Builder()
            .numFrames(2)
            .intervalMs(interval_);
      if (nrChannels_ > 0) {
         builder.channelGroup(channelGroup)
               .channels(channelSpecs);
      }
      SequenceSettings mdaSeq = builder.build();

      List<InfoPacket> packets = AE2010ImageDecoder.collectImages(
            ae2010.run(mdaSeq, true, null, null));

      int imagesPerFrame = Math.max(1, nrChannels_);

      assertEquals(2 * imagesPerFrame, packets.size());

      int i = 0;
      for (InfoPacket packet : packets) {
         if (shouldCombineBursts_) {
            assertEquals(i, packet.camera.frameNr);
         }
         else {
            assertEquals(i % imagesPerFrame, packet.camera.frameNr);
         }
         i++;
      }
   }
}


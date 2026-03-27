package org.micromanager.data.internal;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.StagePosition;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;


public class DefaultSummaryMetadataTest {

   private DefaultSummaryMetadata construct() {
      DefaultSummaryMetadata.Builder builder = new DefaultSummaryMetadata.Builder();
      PositionList positionList = new PositionList();
      for (int i = 0; i < 3; i++) {
         MultiStagePosition msp = new MultiStagePosition();
         msp.setDefaultXYStage("XY");
         msp.setDefaultZStage("Z");
         msp.setLabel("Pos" + i);
         msp.setProperty("Key", "Val" + i);
         msp.add(StagePosition.create2D("XY", 0.0, 2.0 * i));
         msp.add(StagePosition.create1D("z", -3.0 * i));
         positionList.addPosition(msp);
      }

      builder.axisOrder("c,t,z").channelGroup("Channels").computerName("test").profileName("default").prefix("test")
             .stagePositions(positionList.getPositions());
      return builder.build();
   }

   public void compareMultiStagePositions(MultiStagePosition inPos, MultiStagePosition outPos) {
      Assert.assertEquals(inPos.getDefaultXYStage(), outPos.getDefaultXYStage());
      Assert.assertEquals(inPos.getDefaultZStage(), outPos.getDefaultZStage());
      Assert.assertEquals(inPos.getLabel(), outPos.getLabel());
      Assert.assertEquals(inPos.size(), outPos.size());
   }

   @Test
   public void roundTripWithAcquisitionSettings() throws IOException {
      // Build a SequenceSettings with channels and z-slices
      SequenceSettings settings = new SequenceSettings.Builder()
            .useChannels(true)
            .useSlices(true)
            .sliceZStepUm(0.5)
            .numFrames(3)
            .useFrames(true)
            .build();

      // Build a flat PropertyMap simulating scope device state
      PropertyMap scopeData = PropertyMaps.builder()
            .putString("Camera-Exposure", "100.0")
            .putString("Objective-Label", "10x")
            .putString("ZStage-Position", "250.5")
            .build();

      DefaultSummaryMetadata.Builder builder = new DefaultSummaryMetadata.Builder();
      builder.sequenceSettings(settings)
             .initialScopeData(scopeData)
             .channelGroup("Channel")
             .userName("testUser")
             .profileName("testProfile")
             .startDate("2026-01-01T00:00:00")
             .imageWidth(512)
             .imageHeight(512);
      DefaultSummaryMetadata input = builder.build();

      // Serialize to JSON (as Explorer does when writing to NDTiff)
      String asJson = NonPropertyMapJSONFormats.summaryMetadata().toJSON(input.toPropertyMap());

      // Deserialize back (as Explorer does when re-opening stored data)
      SummaryMetadata output = DefaultSummaryMetadata.fromPropertyMap(
            NonPropertyMapJSONFormats.summaryMetadata().fromJSON(asJson));

      // Verify SequenceSettings survived
      SequenceSettings outSettings = output.getSequenceSettings();
      Assert.assertNotNull("SequenceSettings should survive round-trip", outSettings);
      Assert.assertTrue("useChannels should survive", outSettings.useChannels());
      Assert.assertTrue("useSlices should survive", outSettings.useSlices());
      Assert.assertEquals("sliceZStepUm should survive", 0.5, outSettings.sliceZStepUm(), 1e-9);
      Assert.assertEquals("numFrames should survive", 3, outSettings.numFrames());

      // Verify initialScopeData survived
      PropertyMap outScopeData = output.getInitialScopeData();
      Assert.assertNotNull("InitialScopeData should survive round-trip", outScopeData);
      Assert.assertFalse("InitialScopeData should not be empty", outScopeData.isEmpty());
      Assert.assertEquals("Camera-Exposure should survive",
            "100.0", outScopeData.getString("Camera-Exposure", null));
      Assert.assertEquals("Objective-Label should survive",
            "10x", outScopeData.getString("Objective-Label", null));

      // Verify other fields
      Assert.assertEquals("userName should survive", "testUser", output.getUserName());
      Assert.assertEquals("profileName should survive", "testProfile", output.getProfileName());
      Assert.assertEquals("startDate should survive",
            "2026-01-01T00:00:00", output.getStartDate());
   }

   @Test
   public void roundTrip() {
      DefaultSummaryMetadata input = construct();
      String asJson = NonPropertyMapJSONFormats.summaryMetadata().toJSON(input.toPropertyMap());
      System.out.println(asJson);
      SummaryMetadata output = null;
      try {
         output = DefaultSummaryMetadata.fromPropertyMap(
               NonPropertyMapJSONFormats.summaryMetadata().fromJSON(asJson));
      } catch (IOException ex) {
         System.out.println("cATASTROPHIC FAILURE");
      }
      Assert.assertEquals(input.getChannelGroup(), output.getChannelGroup());
      Assert.assertEquals(input.getOrderedAxes().size(), output.getOrderedAxes().size());
      Assert.assertEquals(input.getStagePositionList().size(), output.getStagePositionList().size());
      for (int i = 0; i < input.getStagePositionList().size(); i++) {
         MultiStagePosition inPos = input.getStagePositionList().get(i);
         MultiStagePosition outPos = output.getStagePositionList().get(i);
         compareMultiStagePositions(inPos, outPos);
      }
      System.out.println("Success!");
   }
}

package org.micromanager.data.internal;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
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

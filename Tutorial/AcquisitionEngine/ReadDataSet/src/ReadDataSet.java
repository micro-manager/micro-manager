import mmcorej.TaggedImage;

import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.api.TaggedImageStorage;


public class ReadDataSet {

   public static void main(String[] args) {
      //String rootName = "C:/AcqusitionData/Tests/Test-A_1";
      String rootName = "C:/AcqusitionData/20130813 Oxidative Stress Test 3/NIH3T3_1";
      
      try {
         TaggedImageStorage storage = new TaggedImageStorageDiskDefault(rootName);
         JSONObject summary = storage.getSummaryMetadata();
         System.out.println(summary.toString(3));
         
         int numChannels = summary.getInt("Channels");
         int numSlices = summary.getInt("Slices");
         int numFrames = summary.getInt("Frames");
         int numPositions = summary.getInt("Positions");
         
         for (int pos=0; pos < numPositions; pos++) {
            for (int fr=0; fr<numFrames; fr++) {
               TaggedImage img = storage.getImage(0, 0, fr, pos);
               System.out.println(img.tags.toString(3));
            }
         }
         
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }

   }

}

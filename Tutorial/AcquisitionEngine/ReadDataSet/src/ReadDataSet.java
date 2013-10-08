import mmcorej.TaggedImage;

import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.api.MMTags;
import org.micromanager.api.TaggedImageStorage;


public class ReadDataSet {

   public static void main(String[] args) {
      String rootName = "C:/AcqusitionData/Tests/Test-A_1";
      
      try {
         TaggedImageStorage storage = new TaggedImageStorageDiskDefault(rootName);
         JSONObject summary = storage.getSummaryMetadata();
         System.out.println(summary.toString(3));
         
         int numChannels = summary.getInt(MMTags.Summary.CHANNELS);
         int numSlices = summary.getInt(MMTags.Summary.SLICES);
         int numFrames = summary.getInt(MMTags.Summary.FRAMES);
         int numPositions = summary.getInt(MMTags.Summary.POSITIONS);
         
         for (int pos=0; pos < numPositions; pos++) {
            for (int t=0; t<numFrames; t++) {
               for (int z=0; z<numSlices; z++) {
                  for (int ch=0; ch<numChannels; ch++) {
                     TaggedImage img = storage.getImage(ch, z, t, pos);
                     Object pixels = img.pix;
                     // TODO: do something with pixels
                     
                     System.out.println(img.tags.toString(3));
                  }
               }
            }
         }
         
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }

   }

}

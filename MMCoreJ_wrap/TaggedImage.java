package mmcorej;

import java.util.Map;

 /*
 * @author arthur
 */

public class TaggedImage {
   public final Object pix;
   public Map<String,String> tags;

   public TaggedImage(Object pix, Map<String,String> tags) {
      this.pix = pix;
      this.tags = tags;
   }
}

package mmcorej;

import org.json.JSONObject;

 /*
 * @author arthur
 */

public class TaggedImage {
   public final Object pix;
   public JSONObject tags;

   public TaggedImage(Object pix, JSONObject tags) {
      this.pix = pix;
      this.tags = tags;
   }
}

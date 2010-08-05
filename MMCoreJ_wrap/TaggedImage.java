package mmcorej;

import java.util.Map;

 /*
 * @author arthur
 */

public class TaggedImage {
   public Object pix;
   public Map<String,String> md;

   public TaggedImage(Object pix, Map<String,String> md) {
      this.pix = pix;
      this.md = md;
   }
}


package org.micromanager.acquisition;

import java.util.concurrent.LinkedBlockingQueue;
import mmcorej.TaggedImage;

/**
 *
 * @author arthur
 */
public class TaggedImageQueue extends LinkedBlockingQueue<TaggedImage>
{
   // Poison in the sense of an end-of-stream object. (See http://bit.ly/c1Vgju)
   public static TaggedImage POISON = new TaggedImage(null, null);

   public static boolean isPoison(TaggedImage image) {
      return ((image.pix == null) && (image.tags == null));
   }
}

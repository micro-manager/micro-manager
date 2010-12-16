/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import mmcorej.TaggedImage;
import org.micromanager.utils.GentleLinkedBlockingQueue;

/**
 *
 * @author arthur
 */
public class TaggedImageQueue extends GentleLinkedBlockingQueue<TaggedImage>
{
   // Poison in the sense of an end-of-stream object. (See http://bit.ly/c1Vgju)
   public static TaggedImage POISON = new TaggedImage(null, null);

   public static boolean isPoison(TaggedImage image) {
      return ((image.pix == null) && (image.tags == null));
   }
}

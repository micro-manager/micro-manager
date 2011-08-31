/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.api;

import mmcorej.TaggedImage;
import org.micromanager.api.TaggedImageStorage;

/**
 *
 * @author arthur
 */
public interface ImageCacheListener {

   /*
    * Image or metadata has been added to the image storage object.
    * Called any number of times.
    */
   public void imageReceived(TaggedImage taggedImage);

   /* After this method is call, no more images or image metadata
    * will be added to the image storage object. Called once.
    */
   public void imagingFinished(String path);
}

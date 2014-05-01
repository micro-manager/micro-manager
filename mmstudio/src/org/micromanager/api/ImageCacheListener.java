///////////////////////////////////////////////////////////////////////////////
//FILE:          ImageCacheListener.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein
//
// COPYRIGHT:    University of California, San Francisco, 2012
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.api;

import mmcorej.TaggedImage;

/**
 * This interface provides a way to register callbacks to monitor the
 * state of an ImageCache. Used, for example, by the VirtualAcquisitionDisplay
 * to update the display of the image data set whenever a new image is
 * received during acquisition.
 */
public interface ImageCacheListener {

   /*
    * Implement this method to be informed when a TaggedImage or metadata
    * has been added to the image cache. May be called as many times as images
    * are received by the cache.
    */
   public void imageReceived(TaggedImage taggedImage);

   /*
    * Implement this method to be informed when no more TaggedImages are
    * expected to be added the image cache (i.e., that acquisition has
    * finished). After this method is call, no more images or image metadata
    * will be added to the image storage object. Called once.
    */
   public void imagingFinished(String path);
}

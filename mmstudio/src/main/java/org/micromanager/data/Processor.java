///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.data;

/**
 * Processors manipulate images before they are added to a Datastore.
 */
public abstract class Processor {
   /**
    * Process an Image. Instead of returning a result Image, the Image should
    * be "handed" to the provided ProcessorContext using its outputImage()
    * method. You are not required to output an Image every time this method
    * is called (for example if you want to do frame-averaging or stack
    * projections). However, it is not legal to output Images outside of this
    * function call (e.g. via thread-based systems).
    * @param image input Image
    * @param context ProcessorContext to be used to hand the processed image to
    */
   public abstract void processImage(Image image, ProcessorContext context);

   /**
    * Clean up when processing is finished. At this time no more images are
    * going to be sent to the processImage method. The ProcessorContext is
    * made available in case any final images need to be generated. If the
    * Processor creates any resources external to it (like displays or
    * datastores) then they should be cleaned up at this time.
    * @param context ProcessorContext that can be used to hand images to
    */
   public void cleanup(ProcessorContext context) {};
}

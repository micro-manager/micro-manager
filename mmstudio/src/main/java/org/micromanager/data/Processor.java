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
 * Processors manipulate images before they are added to a Datastore. They
 * are arranged into a sequence by a Pipeline.
 */
public interface Processor {
   /**
    * Update the SummaryMetadata of the Datastore that images will ultimately
    * be stored in. This method will be called at most once, prior to any
    * images being processed, and is the Processor's only chance to log what
    * manner of changes it expects to make to the data.
    * The default implementation of this method returns the passed-in
    * SummaryMetadata unmodified.
    *
    * @param source Source SummaryMetadata, as generated from the input to this
    *        Processor.
    * @return New SummaryMetadata, modified from the source by the Processor.
    */
   default SummaryMetadata processSummaryMetadata(SummaryMetadata source) {
      return source;
   }

   /**
    * Process an Image. Instead of returning a result Image, the Image should
    * be "handed" to the provided ProcessorContext using its outputImage()
    * method. You are not required to output an Image every time this method
    * is called (for example if you want to do frame-averaging or stack
    * projections). However, it is not legal to output Images outside of this
    * function call (e.g. via thread-based systems).
    *
    * @param image input Image
    * @param context ProcessorContext to be used to hand the processed image to
    */
   void processImage(Image image, ProcessorContext context);

   /**
    * Clean up when processing is finished. At this time no more images are
    * going to be sent to the processImage method. The ProcessorContext is
    * made available in case any final images need to be generated. If the
    * Processor creates any resources external to it (like displays or
    * datastores) then they should be cleaned up at this time.
    *
    * @param context ProcessorContext that can be used to hand images to
    */
   default void cleanup(ProcessorContext context) {}
}

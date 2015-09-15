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
 * A ProcessorContext is an object that allows Processors to communicate with
 * the ProcessorPipeline.
 */
public interface ProcessorContext {
   /**
    * Hand a newly-generated Image to the ProcessorContext. This method is the
    * only valid way for a Processor to "produce" an Image, and should only be
    * called from within the Processor's processImage() function.
    * @param image Image to be handed to the ProcessorContext
    */
   public void outputImage(Image image);

   /**
    * Access the SummaryMetadata of the Datastore that images will ultimately
    * be inserted into.
    * @return the SummaryMetadata of the Datastore of processed images.
    */
   public SummaryMetadata getSummaryMetadata();

   /**
    * Replace the SummaryMetadata of the Datastore that images will ultimately
    * be inserted into.
    * @param metadata New SummaryMetadata for the Datastore of processed images
    * @throws DatastoreFrozenException if the Datastore is frozen. In practice
    *         this should never happen.
    */
   public void setSummaryMetadata(SummaryMetadata metadata) throws DatastoreFrozenException;
}

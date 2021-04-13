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

import java.io.IOException;
import java.util.List;


/**
 * Pipelines are used to apply a sequence of Processors to Images prior to
 * those images being inserted into a Datastore. You can create a new Pipeline
 * by using the DataManager.createPipeline() or
 * DataManager.copyApplicationPipeline() methods.
 */
public interface Pipeline {
   /**
    * Pass a SummaryMetadata through the Pipeline to be modified by the
    * Processors in it. The resulting, updated SummaryMetadata will be set
    * into the Datastore. This method may only be called if the Datastore does
    * not already have SummaryMetadata set for it, and only prior to any images
    * being inserted into the Pipeline by the insertImage() method.
    * This method is synchronous even for asynchronous Pipelines.
    * @param source Source SummaryMetadata to be input into the Pipeline.
    * @throws org.micromanager.data.DatastoreFrozenException if the Datastore
    *         is frozen at the time this method is called.
    * @throws DatastoreRewriteException if the Datastore has already had
    *         SummaryMetadata set for it at the time this method is called.
    * @throws PipelineErrorException if processing of images has already
    *         started at the time this method is called.
    */
   void insertSummaryMetadata(SummaryMetadata source) throws IOException, PipelineErrorException;

   /**
    * Insert an Image into the Pipeline. The Image will be processed by the
    * first Processor in the Pipeline, any Images output by that Processor
    * will be processed by the second Processor, etc. until the Image sequence
    * reaches the end of the Pipeline, at which point those result Images
    * will be stored in any attached Datastores (see addDatastore, below).
    *
    * If the Pipeline is in synchronous mode, then this call will block until
    * any generated images are in the Datastore. If it is in asynchronous
    * mode, then the call will return as soon as the first processor in the
    * pipeline is ready to start processing (i.e. as soon as it is not busy
    * processing a different image).
    *
    * If the Pipeline has been halted, then the image will be silently
    * discarded, and no processing of it will occur.
    *
    * If any of the Processors in the Pipeline throws an exception during
    * processing, then the Pipeline will be put into an error state, and
    * attempts to call insertImage() will throw a PipelineErrorException.
    * You can retrieve the exception(s) that occurred by calling
    * getExceptions(). If you wish to continue inserting images into the
    * Pipeline (despite the fact that it may be in an inconsistent state,
    * resulting in incorrect processed images), then you must call
    * clearExceptions() first.
    *
    * @param image Image to be processed by the Pipeline.
    * @throws org.micromanager.data.DatastoreFrozenException if the Datastore
    *         is frozen at the time this method is called, or if this pipeline
    *         has no Processors in it and the Datastore is frozen. If the
    *         Datastore is frozen at some point after this method is called,
    *         then the exception will not be thrown.
    * @throws DatastoreRewriteException if this pipeline has no Processors in it and
    *         the inserted Image has coordinates that match an Image that is
    *         already in the Datastore.
    * @throws org.micromanager.data.PipelineErrorException if the pipeline is
    *         in an error state; see the getExceptions() and clearExceptions()
    *         methods.
    */
   void insertImage(Image image) throws IOException, PipelineErrorException;

   /**
    * Get the output Datastore for this Pipeline. This Datastore is the
    * ultimate recipient of Images that have been processed by the Pipeline.
    * @return output Datastore for this Pipeline
    */
   Datastore getDatastore();

   /**
    * Return whether the Pipeline is operating in synchronous or asynchronous
    * modes. See DataManager.createPipeline() for more information.
    * @return True if the Pipeline is synchronous, False otherwise.
    */
   boolean isSynchronous();

   /**
    * @deprecated Use {@link #isSynchronous()} instead.
    */
   @Deprecated
   default boolean getIsSynchronous() {
      return isSynchronous();
   }

   /**
    * Halt image processing, so that the Pipeline will not produce any more
    * images. Once halted, a Pipeline cannot be resumed; create a new one
    * instead. This method will block until all processors along the chain are
    * known to be done doing any processing (that is, their processImage()
    * calls have completed), regardless of whether or not the pipeline as a
    * whole is synchronous. Consequently, once this method returns, it should
    * be safe to freeze the Datastore and save it to disk.
    */
   void halt();

   /**
    * Returns true if the pipeline has been halted (that is, its halt() method
    * has been executed to completion), false otherwise. If this method
    * returns true, then the pipeline cannot generate any more images.
    */
   boolean isHalted();

   /**
    * Return a list containing any exceptions that have occurred during
    * processing of images. If this list has any elements in it, then calls
    * to insertImage() will provoke a PipelineErrorException.
    * @return list containing any exceptions that have occurred during
    * processing of images
    */
   List<Exception> getExceptions();

   /**
    * Clear the list of exceptions that the pipeline has encountered, allowing
    * insertImage() to be called again. Note that if exceptions have occurred,
    * then the pipeline may be in an inconsistent state, causing processed
    * image data to be incorrect.
    */
   void clearExceptions();

   /**
    * Return the list of Processors used by this Pipeline.
    */
   List<Processor> getProcessors();
}

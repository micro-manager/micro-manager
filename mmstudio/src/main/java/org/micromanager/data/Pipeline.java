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

import java.util.List;

import org.micromanager.PropertyMap;

/**
 * Pipelines are used to apply a sequence of Processors to Images prior to
 * those images being inserted into a Datastore. You can create a new Pipeline
 * by using the DataManager.createPipeline() or
 * DataManager.copyApplicationPipeline() methods.
 */
public interface Pipeline {
   /**
    * Insert an Image into the Pipeline. The Image will be processed by the
    * first Processor in the Pipeline, any Images output by that Processor
    * will be processed by the second Processor, etc. until the Image sequence
    * reaches the end of the Pipeline, at which point those result Images
    * will be stored in any attached Datastores (see addDatastore, below).
    * If the Pipeline is in synchronous mode, then this call will block until
    * any generated images are in the Datastore; otherwise it will return
    * immediately.
    * @param image Image to be processed by the Pipeline.
    */
   public void insertImage(Image image);

   /**
    * Get the output Datastore for this Pipeline. This Datastore is the
    * ultimate recipient of Images that have been processed by the Pipeline.
    */
   public Datastore getDatastore();

   /**
    * Return whether the Pipeline is operating in synchronous or asynchronous
    * modes. See DataManager.createPipeline() for more information.
    * @return True if the Pipeline is synchronous, False otherwise.
    */
   public boolean getIsSynchronous();

   /**
    * Halt image processing, so that the Pipeline will not produce any more
    * images. Once halted, a Pipeline cannot be resumed; create a new one
    * instead.
    */
   public void halt();
}

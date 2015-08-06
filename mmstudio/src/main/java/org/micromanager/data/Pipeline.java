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
    * Add an output Datastore to this Pipeline. As images are output from the
    * last Processor in the chain, they will be inserted into this Datastore.
    * @param store Datastore that should receive images from the Pipeline.
    */
   public void addDatastore(Datastore store);

   /**
    * Remove the given Datastore from the list of output Datastores receiving
    * images from this Pipeline. If the Datastore is not a valid target then
    * nothing happens.
    * @param store Datastore that should no longer receive images from the
    *        Pipeline.
    */
   public void removeDatastore(Datastore store);

   /**
    * Halt image processing, so that the Pipeline will not produce any more
    * images. Once halted, a Pipeline cannot be resumed; create a new one
    * instead.
    */
   public void halt();
}

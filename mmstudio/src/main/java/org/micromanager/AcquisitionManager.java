///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    Open Imaging, Inc.
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
//

package org.micromanager;


import org.micromanager.data.Datastore;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;

// These ought not be part of the public API and methods that refer to them are
// deprecated.
import org.micromanager.internal.utils.MMScriptException;


/**
 * This interface provides access to methods related to the acquisition engine
 * for performing experiments. You can access it using Studio.acquisitions()
 * or Studio.getAcquisitionManager().
 */
public interface AcquisitionManager {
   /**
    * Executes Acquisition with settings as in the MDA dialog.
    * Will open the Acquisition Dialog when it is not open yet
    * Returns after Acquisition finishes
    * Note that this function should not be executed on the EDT (which is the
    * thread running the UI).
    * @return The Datastore containing the images from the acquisition.
    * @throws MMScriptException if the acquisition is started on the EDT
    */
   public Datastore runAcquisition() throws MMScriptException;

   /**
    * As runAcquisition, but will return as soon as the acquisition is set up
    * and started; useful for code that wants access to the Datastore for the
    * acquisition before it finishes.
    * @return The Datastore containing the images from the acquisition.
    * @throws MMScriptException if the acquisition is started on the EDT
    */
   public Datastore runAcquisitionNonblocking() throws MMScriptException;

   /**
    * Execute an acquisition using the provided SequenceSettings. This function
    * should not be called on the EDT.
    * @param settings SequenceSettings to use for the acquisition, or null
    *        to use the settings in the MDA dialog.
    * @param shouldBlock if true, the method will block until the acquisition
    *        is completed.
    * @return The Datastore containing the images from the acquisition.
    * @throws MMScriptException if the acquisition is started on the EDT.
    */
   public Datastore runAcquisitionWithSettings(SequenceSettings settings,
         boolean shouldBlock) throws MMScriptException;

   /**
    * Halt any ongoing acquisition as soon as possible.
    */
   public void haltAcquisition();

   /**
    * Executes Acquisition with current settings but allows for changing the data path.
    * Will open the Acquisition Dialog when it is not open yet.
    * Returns after Acquisition finishes.
    * Note that this function should not be executed on the EDT (which is the
    * thread running the UI).
    * @param name Name of this acquisition.
    * @param root Place in the file system where data can be stored.
    * @return The Datastore containing the images from the acquisition.
    * @throws MMScriptException
    */
   public Datastore runAcquisition(String name, String root) throws MMScriptException;

   /**
    * Loads setting for Acquisition Dialog from file
    * Will open Acquisition Dialog when it is not open yet
    * @param path file path from which setting for acquisition dialog should
    * be loaded
    * @throws MMScriptException
    */
   public void loadAcquisition(String path) throws MMScriptException;

   /**
    * Returns true when an acquisition is currently running (note: this
    * function will not return true if live mode, snap, or "Camera --&gt;
    * Album" is currently running
    * @return true when an acquisition is currently running
    */
   public boolean isAcquisitionRunning();

   /**
    * Pause/Unpause a running acquisition
    * @param state true if paused, false if no longer paused
    */
   public void setPause(boolean state);

   /**
    * Returns true if the acquisition is currently paused.
    * @return true if paused, false if not paused
    */
   public boolean isPaused();

   /**
    * Attach a runnable to the acquisition engine. Each index (f, p, c, s) can
    * be specified. Passing a value of -1 should result in the runnable being attached
    * at all values of that index. For example, if the first argument is -1,
    * then the runnable should execute at every frame.
    * @param frame 0-based frame number
    * @param position 0-based position number
    * @param channel 0-based channel number
    * @param slice 0-based (z) slice number
    * @param runnable code to be run
    */
   public void attachRunnable(int frame, int position, int channel, int slice,
           Runnable runnable);

   /**
    * Remove runnables from the acquisition engine
    */
   public void clearRunnables();

   /**
    * Return current acquisition settings
    * @return acquisition settings instance
    */
   public SequenceSettings getAcquisitionSettings();

   /**
    * Apply new acquisition settings
    * @param settings acquisition settings
    */
   public void setAcquisitionSettings(SequenceSettings settings);
}

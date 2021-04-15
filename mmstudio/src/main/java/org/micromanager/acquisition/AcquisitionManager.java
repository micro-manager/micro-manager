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

package org.micromanager.acquisition;

import java.io.IOException;
import java.util.List;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;


/**
 * This interface provides access to methods related to the acquisition engine
 * for performing experiments. You can access it using Studio.acquisitions()
 * or Studio.getAcquisitionManager().
 */
public interface AcquisitionManager {

   /**
    * Provides an empty Sequence Settings Builder
    *
    */
   SequenceSettings.Builder sequenceSettingsBuilder();

   /**
    * Provides and empty ChannelSpec Builder
    *
    */
   ChannelSpec.Builder channelSpecBuilder();

   /**
    * Executes Acquisition with settings as in the MDA dialog.
    * Will open the Acquisition Dialog if it is not open yet.
    * Returns after Acquisition finishes and the data processing pipeline has
    * finished processing all images.
    * Note that this function should not be executed on the EDT (which is the
    * thread running the UI).
    * @return The Datastore containing the images from the acquisition.
    * @throws IllegalThreadStateException if the acquisition is started on the EDT
    */
   Datastore runAcquisition() throws IllegalThreadStateException;

   /**
    * As runAcquisition, but will return as soon as the acquisition is set up
    * and started; useful for code that wants access to the Datastore for the
    * acquisition before it finishes.
    * @return The Datastore containing the images from the acquisition.
    * @throws IllegalThreadStateException if the acquisition is started on the EDT
    */
   Datastore runAcquisitionNonblocking() throws IllegalThreadStateException;

   /**
    * Execute an acquisition using the provided SequenceSettings. This function
    * should not be called on the
    * EDT.
    * @param settings SequenceSettings to use for the acquisition, or null
    *        to use the settings in the MDA dialog.
    * @param shouldBlock if true, the method will block until the acquisition
    *        is completed.
    * @return The Datastore containing the images from the acquisition.
    * @throws IllegalThreadStateException if the acquisition is started on the
    * EDT.
    */
   Datastore runAcquisitionWithSettings(SequenceSettings settings,
         boolean shouldBlock) throws IllegalThreadStateException;

   /**
    * Halt any ongoing acquisition as soon as possible.
    */
   void haltAcquisition();

   /**
    * Executes Acquisition with current settings but allows for changing the data path.
    * Will open the Acquisition Dialog when it is not open yet.
    * Returns after Acquisition finishes.
    * Note that this function should not be executed on the EDT (which is the
    * thread running the UI).
    * @param name Name of this acquisition.
    * @param root Place in the file system where data can be stored.
    * @return The Datastore containing the images from the acquisition.
    * @throws IllegalThreadStateException if the acquisition is started on the
    * EDT.
    */
   Datastore runAcquisition(String name, String root) throws IllegalThreadStateException;

   /**
    * Load a file containing a SequenceSettings object, and apply the settings
    * found in it to the Multi-D Acquisition (MDA) dialog. Will open the
    * dialog if it is not currently open.
    * @param path File path to the SequenceSettings object.
    * @throws IOException if the sequence settings cannot be loaded.
    */
   void loadAcquisition(String path) throws IOException;

   /**
    * Load a SequenceSettings file and return the SequenceSettings object. The
    * settings will *not* be applied to the Multi-D Acquisition dialog.
    * @param path File path to the SequenceSettings object.
    * @throws IOException if there was an error reading the file.
    */
   SequenceSettings loadSequenceSettings(String path) throws IOException;

   /**
    * Save the provided SequenceSettings object to disk at the specified path.
    * @param settings SequenceSettings object to save.
    * @param path Path to save the SequenceSettings to.
    * @throws IOException if there was an error writing the file to disk.
    */
   void saveSequenceSettings(SequenceSettings settings, String path) throws IOException;

   /**
    * Returns true when an acquisition is currently running (note: this
    * function will not return true if live mode, snap, or "Camera --&gt;
    * Album" is currently running
    * @return true when an acquisition is currently running
    */
   boolean isAcquisitionRunning();

   /**
    * Pause/Unpause a running acquisition
    * @param state true if paused, false if no longer paused
    */
   void setPause(boolean state);

   /**
    * Returns true if the acquisition is currently paused.
    * @return true if paused, false if not paused
    */
   boolean isPaused();

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
   void attachRunnable(int frame, int position, int channel, int slice,
           Runnable runnable);

   /**
    * Remove runnables from the acquisition engine
    */
   void clearRunnables();

   /**
    * Return current acquisition settings as shown in the Multi-D Acquisition
    * dialog.
    * @return acquisition settings instance
    */
   SequenceSettings getAcquisitionSettings();

   /**
    * Apply new acquisition settings
    * @param settings acquisition settings
    */
   void setAcquisitionSettings(SequenceSettings settings);

   /**
    * Snap images using the current camera(s) and return them. See also
    * SnapLiveManager.snap(), which can be safely called when live mode is on,
    * and may optionally display images in the Snap/Live View.
    * @return A list of images acquired by the snap.
    * @throws Exception If there is an error when communicating with the Core
    * (for example, because Live mode is on), or if the current hardware
    * configuration contains no cameras.
    */
   List<Image> snap() throws Exception;

   /**
    * Generate a new SummaryMetadata that contains pre-populated fields based
    * on the current state of the program. The following fields will be set:
    * userName, profileName, microManagerVersion, metadataVersion,
    * computerName.
    * @return A pre-populated SummaryMetadata.
    */
   SummaryMetadata generateSummaryMetadata();

   /**
    * Generate a new Metadata that contains pre-populated fields based on the
    * current state of the program and the provided Image. The Image's Metadata
    * will be used as a base, with the following fields overwritten based on
    * the image properties and current hardware state, if possible: binning,
    * bitDepth, camera, pixelType, receivedTime (to the current time), uuid,
    * xPositionUm, yPositionUm, zPositionUm. Note that stage positions are
    * based on cached values, and may be inaccurate if the cache has not been
    * refreshed since the last time the stage moved.
    *
    * Additionally, if the includeHardwareState boolean is set to true, then
    * the current state of the system state cache (i.e. Micro-Manager's
    * understanding of all device property values) will be included in the
    * scopeData property. As with the stage positions, these values do not
    * necessarily reflect reality; they are just the last-known values in the
    * cache.
    * @param image Image whose metadata should be populated.
    * @param includeHardwareState if true, then the scopeData field will be
    *        populated in the result Metadata.
    * @return A pre-populated Metadata.
    * @throws Exception If there were any errors communicating with the Core to
    * get required values.
    */
   Metadata generateMetadata(Image image, boolean includeHardwareState) throws Exception;

   /**
    * Test the provided Object, and return true if it corresponds to the
    * "source" object that Micro-Manager's acquisition system uses when
    * publishing AcquisitionStartedEvent and AcquisitionEndedEvent. In other
    * words, you can use this method to determine if an acquisition originated
    * from Micro-Manager.
    * @param source Result of calling getSource() on an AcquisitionStartedEvent
    *        or AcquisitionEndedEvent.
    * @return true if acquisition was started by Micro-Manager, false if it
    *         was started elsewhere (e.g. by a plugin or other third-party
    *         code).
    */
   boolean isOurAcquisition(Object source);
}

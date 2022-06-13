///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 1, 2005
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
// CVS:          $Id: AcquisitionEngine.java 318 2007-07-02 22:29:55Z nenad $
//

package org.micromanager.acquisition.internal;

import java.util.ArrayList;
import mmcorej.org.json.JSONObject;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Datastore;
import org.micromanager.internal.interfaces.AcqSettingsListener;
import org.micromanager.internal.utils.MMException;

/**
 * The original Acquisition engine interface. This interface is still used
 * by scripts and the AcqDialog and should be retained. The current
 * implementation of the interface is the AcquisitionWrapperEngine,
 * which simply adapters the old interface to an object implementing the
 * new interface, IAcquisitionEngine2010.
 */
public interface AcquisitionEngine {


   /**
    * Sets the global position list attached to the parent Micro-Manager gui.
    */
   void setPositionList(PositionList posList);

   /**
    * Provides the acquisition engine with the parent Micro-Manager gui.
    */
   void setParentGUI(Studio parent);

   /**
    * Sets which device will be used as the Z (focus) axis.
    */
   void setZStageDevice(String stageLabel);

   /**
    * Sets whether the Live window will be updated during acquisition.
    */
   void setUpdateLiveWindow(boolean b);

   // run-time control

   /**
    * Starts acquisition as defined in the Multi-Dimensional Acquisition Window.
    * Returns the Datastore for the acquisition.
    *
    * @throws MMException indicates an error during acquisitions
    */
   Datastore acquire() throws MMException;

   /**
    * Return Datastore for current or most recent acquisition, or null if no
    * acquisition has been run yet.
    */
   Datastore getAcquisitionDatastore();

   /**
    * Stops a running Acquisition.
    *
    * @param interrupted when set, multifield acquisition will also be stopped
    */
   void stop(boolean interrupted);


   /**
    * Request immediate abort of current task.
    */
   boolean abortRequest();

   /**
    * Signals that a running acquisition is done.
    */
   void setFinished();

   /**
    * Returns true when Acquisition is running.
    */
   boolean isAcquisitionRunning();

   boolean isZSliceSettingEnabled();

   /**
    * Determines if a multi-field acquisition is running.
    */
   boolean isMultiFieldRunning();

   /**
    * Unconditional shutdown.  Will stop acuiqistion and multi-field acquisition.
    */
   void shutdown();

   /**
    * Pause/Unpause a running acquisition.
    */
   void setPause(boolean state);


   /**
    * Find out which groups are available.
    */
   String getFirstConfigGroup();


   /**
    * Resets the engine.
    */
   @Deprecated
   void clear();

   SequenceSettings getSequenceSettings();

   void setSequenceSettings(SequenceSettings sequenceSettings);


   // utility
   String getVerboseSummary();

   boolean isConfigAvailable(String config);

   /**
    * Returns the available groups in Micro-Manager's configuration settings.
    */
   String[] getAvailableGroups();

   /**
    * Find out which channels are currently available for the selected channel group.
    *
    * @return - list of channel (preset) names
    */
   String[] getChannelConfigs();

   /**
    * Returns the configuration preset group currently selected in the Multi-Dimensional
    * Acquisition Window.
    */
   String getChannelGroup();

   /**
    * Set the channel group if the current hardware configuration permits.
    *
    * @param newGroup New channel group
    * @return - true if successful
    */
   boolean setChannelGroup(String newGroup);

   void setChannel(int row, ChannelSpec sp);

   void setChannels(ArrayList<ChannelSpec> channels);

   double getFrameIntervalMs();

   /**
    * Returns the current z position for the focus drive used by the
    * acquisition engine.
    */
   double getCurrentZPos();

   /**
    * Returns true if the acquisition is currently paused.
    */
   boolean isPaused();

   /**
    * Returns true if abortRequest() has been called -- the acquisition may
    * still be running.
    */
   boolean abortRequested();

   /**
    * Returns a time (in milliseconds) indicating when the next image is
    * expected to be acquired.
    */
   long getNextWakeTime();

   /**
    * Returns true if the acquisition has finished running and no more hardware
    * events will be run.
    */
   boolean isFinished();

   /**
    * Attach a runnable to the acquisition engine. Each index (f, p, c, s) can
    * be specified. Passing a value of -1 should result in the runnable being attached
    * at all values of that index. For example, if the first argument is -1,
    * then the runnable should execute at every frame.
    *
    * <p>Subject to change.
    */
   void attachRunnable(int frame, int position, int channel, int slice, Runnable runnable);


   /**
    * Remove runnables from the acquisition engine.
    */
   void clearRunnables();

   /**
    * Get the summary metadata for the most recent acquisition.
    */
   JSONObject getSummaryMetadata();

   String getComment();

   void addSettingsListener(AcqSettingsListener listener);

   void removeSettingsListener(AcqSettingsListener listener);

   void setShouldDisplayImages(boolean shouldDisplay);
}
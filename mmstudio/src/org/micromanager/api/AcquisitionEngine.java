///////////////////////////////////////////////////////////////////////////////
//FILE:          AcquisitionEngine.java
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
package org.micromanager.api;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.micromanager.navigation.PositionList;
import org.micromanager.utils.AutofocusManager;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.MMException;

/**
 * Acquisition engine interface.
 */
public interface AcquisitionEngine {
   

   public static final String cameraGroup_ = "Camera";
   public static final DecimalFormat FMT2 = new DecimalFormat("#0.00");
   public static final String DEFAULT_ROOT_NAME = "C:/AcquisitionData";
   
   // initialization
   public void setCore(CMMCore core_, AutofocusManager afMgr);

   
   public void setPositionList(PositionList posList);
   public void setParentGUI(ScriptInterface parent);

   /**
    * Sets which device will be used as the Z (focus) axis
    */
   public void setZStageDevice(String stageLabel_);

   /**
    * Sets whether the Live window will be updated during acquistion
    */
   public void setUpdateLiveWindow(boolean b);
   
   // run-time control

   /**
    * Starts acquisition as defined in the Multi-Dimensional Acquistion Window.
    * Returns the acquisition name.
    * @throws MMException
    * @throws MMAcqDataException
    */
   public String acquire() throws MMException;

   /**
    * Stops a running Acquisition
    * @param   interrupted when set, multifield acquisition will also be stopped
    */
   public void stop(boolean interrupted);


   /**
    * Request immediate abort of current task
    */
   public boolean abortRequest();

   /**
    * Signals that a running acquisition is done.
    */
   public void setFinished();

   /**
    * Returns true when Acquisition is running
    */
   public boolean isAcquisitionRunning();

   /**
    * Determines if a multi-field acquistion is running
    */
   public boolean isMultiFieldRunning();

   /**
    * Returns the number of frames acquired so far
    */
   public int getCurrentFrameCount();

   /**
    * enables/diasables the use of custom time points
    * @param enable 
    */
   public void enableCustomTimeIntervals(boolean enable);
   
   /*
    * returns true if acquisition engine is se p to use custom time intervals
    */
   public boolean customTimeIntervalsEnabled(); 
   
   /**
    * Used to provide acquisition with custom time intervals in between frames
    * passing null resets to default time points
    */
   public void setCustomTimeIntervals(double[] customTimeIntervalsMs);

   /*
    * returns list of custom time intervals, or null if none are specified   
    */
   public double[] getCustomTimeIntervals();

   /**
    * Unconditional shutdown.  Will stop acuiqistion and multi-field acquisition
    */
   public void shutdown();

   /**
    * Pause/Unpause a running acquistion
    */
   public void setPause(boolean state);
   
   // settings
   /**
    * Returns Frame Interval set by user in Multi-Dimensional Acquistion Windows
    */
   public double getFrameIntervalMs();

   /**
    * Returns Z slice Step Size set by user in Multi-Dimensional Acquistion Windows
    */
   public double getSliceZStepUm();

   /**
    * Returns Z slice bottom position set by user in Multi-Dimensional Acquistion Windows
    */
   public double getSliceZBottomUm();

   /**
    * Sets channel specification in the given row
    */
   public void setChannel(int row, ChannelSpec channel);

   /**
    * Find out which groups are available
    */
   public String getFirstConfigGroup();

   /**
    * Find out which channels are currently available for the selected channel group.
    * @return - list of channel (preset) names
    */
   public String[] getChannelConfigs();

   /**
    * Returns number of frames set by user in Multi-Dimensional Acquistion Window
    */
   public int getNumFrames();

   /**
    * Returns the configuration preset group currently selected in the Multi-Dimensional Acquistion Window
    */
   public String getChannelGroup();

   /**
    * Set the channel group if the current hardware configuration permits.
    * @param group
    * @return - true if successful
    */
   public boolean setChannelGroup(String newGroup);

   /**
    * Resets the engine
    */
   public void clear();
   public void setFrames(int numFrames, double interval);
   public double getMinZStepUm();
   public void setSlices(double bottom, double top, double step, boolean absolute);

   public boolean isFramesSettingEnabled();

   public void enableFramesSetting(boolean enable);

   public boolean isChannelsSettingEnabled();

   public void enableChannelsSetting(boolean enable);

   public boolean isZSliceSettingEnabled();

   /**
    * returns Z slice top position set by user in Multi-Dimensional Acquistion Windows
    */
   public double getZTopUm();

   /**
    * Flag indicating whether to override autoshutter behavior and keep the shutter
    * open for a Z-stack.  This only has an effect when autoshutter is on, and when 
    * mode "Slices First" has been chosen.
    */
   public void keepShutterOpenForStack(boolean open);

   /**
    * Returns flag indicating whether to override autoshutter behavior during z-stack
    */
   public boolean isShutterOpenForStack();

   /**
    * Flag indicating whether to override autoshutter behavior and keep the shutter
    * open for channel imaging.  This only has an effect when autoshutter is on, and when 
    * mode "Channels First" has been chosen.
    */
   public void keepShutterOpenForChannels(boolean open);

   /**
    * Returns flag indicating whether to override autoshutter behavior
    * during channel acquisition
    */
   public boolean isShutterOpenForChannels();

   /**
    * Sets a flag that signals whether a Z-stack will be acquired
    * @param boolean1 - acquires Z-stack when true
    */
   public void enableZSliceSetting(boolean boolean1);

   /**
    * Sets a flag that signals whether multiple positions will be acquired
    * @param selected - acquires at multiple stage positions when true
    */
   public void enableMultiPosition(boolean selected);

   /**
    * Returns true when multiple positions will be acquired
    * @return whether or not acquisition will be executed at multiple stage
    * positions
    */
   public boolean isMultiPositionEnabled();

   /**
    * Access to the channels used in this acquisition
    * @return - Channels used in this acquisition
    */
   public ArrayList<ChannelSpec> getChannels();

   /**
    * Setss the channels to be used in this acquistion
    * @param channels
    */
   public void setChannels(ArrayList<ChannelSpec> channels);

   /**
    * Returns path to the location where the acquisitions will be stored on
    * disk
    * @return path to the storage place on disk
    */
   public String getRootName();

   /**
    *
    * @param absolutePath
    */
   public void setRootName(String absolutePath);
   public void setCameraConfig(String config);
   public void setDirName(String text);
   public void setComment(String text);
   /**
    * @deprecated
    */
   public boolean addChannel(String name, double exp, double offset, 
           ContrastSettings s8, ContrastSettings s16, int skip, Color c);

   public boolean addChannel(String name, double exp, Boolean doZStack,
           double offset, ContrastSettings s8, ContrastSettings s16, int skip, Color c,
           boolean use);
   
   public boolean addChannel(String name, double exp, Boolean doZStack,
           double offset, ContrastSettings con, int skip, Color c,
           boolean use);
   public void setSaveFiles(boolean selected);
   public boolean getSaveFiles();
   public int getDisplayMode();
   public void setDisplayMode(int mode);
   public int getAcqOrderMode();
   public void setAcqOrderMode(int mode);
   public void enableAutoFocus(boolean enabled);
   public boolean isAutoFocusEnabled();
   public int getAfSkipInterval();
   public void setAfSkipInterval (int interval);
   public void setParameterPreferences(Preferences prefs);

   public void setSingleFrame(boolean selected); // @deprecated
   public void setSingleWindow(boolean selected); // @deprecated
   public String installAutofocusPlugin(String className); // @deprecated
   
   // utility
   public String getVerboseSummary();
   public boolean isConfigAvailable(String config_);
   public String[] getCameraConfigs();
   public String[] getAvailableGroups();
   public double getCurrentZPos();
   public boolean isPaused();
   public void restoreSystem();

   /**
    * @deprecated
    */
   public void addImageProcessor(Class processor);
   /**
    * @deprecated
    */
   public void removeImageProcessor(Class processor);
   
   
   public void addImageProcessor(DataProcessor<TaggedImage> processor);
   public void removeImageProcessor(DataProcessor<TaggedImage> taggedImageProcessor);
   
   public boolean abortRequested();

   public long getNextWakeTime();
   public boolean isFinished();
   public ImageCache getImageCache();

   
   /*
    * Attach a runnable to the acquisition engine. Each index (f, p, c, s) can
    * be specified. Passing a value of -1 should result in the runnable being attached
    * at all values of that index. For example, if the first argument is -1,
    * then the runnable should execute at every frame.
    *
    * Subject to change.
    */
   public void attachRunnable(int frame, int position, int channel, int slice, Runnable runnable);


   /*
    * Remove runnables from the acquisition engine
    */
   public void clearRunnables();
}

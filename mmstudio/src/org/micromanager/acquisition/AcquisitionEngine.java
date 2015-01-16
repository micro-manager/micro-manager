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
package org.micromanager.acquisition;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.json.JSONObject;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.internalinterfaces.AcqSettingsListener;
import org.micromanager.utils.AutofocusManager;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.MMException;

/**
 * The original Acquisition engine interface. This interface is still used
 * by scripts and the AcqDialog and should be retained. The current
 * implementation of the interface is the AcquisitionWrapperEngine,
 * which simply adapters the old interface to an object implementing the
 * new interface, IAcquisitionEngine2010.
 */
public interface AcquisitionEngine {
   

   public static final String cameraGroup_ = "Camera";
   public static final DecimalFormat FMT2 = new DecimalFormat("#0.00");
   public static final String DEFAULT_ROOT_NAME = "C:/AcquisitionData";
   
   // initialization
   public void setCore(CMMCore core_, AutofocusManager afMgr);

   /**
    * Sets the global position list attached to the parent Micro-Manager gui.
    */
   public void setPositionList(PositionList posList);

   /**
    * Provides the acquisition engine with the parent Micro-Manager gui.
    */
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
    * @param newGroup
    * @return - true if successful
    */
   public boolean setChannelGroup(String newGroup);

   /**
    * Resets the engine
    */
   public void clear();

   /**
    * Sets the number of frames and the time interval in milliseconds
    * between frames.
    */
   public void setFrames(int numFrames, double interval);

   /*
    * This value is not used.
    * @Deprecated
    */
   public double getMinZStepUm();

   /*
    * Sets up a z-stack of equally-spaced slices.
    * @param bottom - The first slice position, in microns.
    * @param top - The last slice position, in microns.
    * @param step - The distance between slices in microns.
    * @param absolute - Whether the provided positions are in absolute z-drive coordinates or coordinates relative to the current position.
    */
   public void setSlices(double bottom, double top, double step, boolean absolute);

   /*
    * Returns whether time points have been included in the settings.
    */
   public boolean isFramesSettingEnabled();

   /*
    * Sets whether time points are to be included in the settings. If this
    * value is set to false, then only a single time point is carried out.
    */
   public void enableFramesSetting(boolean enable);

   /*
    * Returns whether channels will be included in the acquired dimensions.
    */
   public boolean isChannelsSettingEnabled();

   /*
    * Sets whether channels are to be included in the settings. If this
    * value is set to false, then only a single channel is acquired, with
    * whatever the current device settings are.
    */
   public void enableChannelsSetting(boolean enable);

   /*
    * Sets whether z slices are to be included in the settings. If this
    * value is set to false, the only a single z position is acquired,
    * at whatever the current z position is.
    */
   public boolean isZSliceSettingEnabled();

   /**
    * returns Z slice top position set by user in Multi-Dimensional Acquisition Window
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
    * Sets the channels to be used in this acquisition
    * @param channels
    */
   public void setChannels(ArrayList<ChannelSpec> channels);

   /**
    * Returns path to the location where the acquisitions will be stored on
    * disk
    */
   public String getRootName();

   /**
    * Sets the absolute path for where the acquisitions will be stored on disk.
    * @param absolutePath
    */
   public void setRootName(String absolutePath);


   /**
    * @Deprecated
    */
   public void setCameraConfig(String config);

   /**
    * Sets the name for the directory in which the images and data are
    * contained. Also known as the "prefix". This dir will be nested inside the root
    * directory specified by setRootName.
    */
   public void setDirName(String text);

   /*
    * Sets the default comment to be included in the acquisition's summary metadata.
    * Equivalent to the comment box in the Multi-Dimensional Acquisition setup window.
    */
   public void setComment(String text);

   
   /**
    * @Deprecated
    */
   public boolean addChannel(String name, double exp, double offset, 
           ContrastSettings s8, ContrastSettings s16, int skip, Color c);


   /*
    * Adds a channel to the acquisition settings.
    * @param name - The name of the channel, matching a configuration preset in the channel group.
    * @param exp - The exposure time for this channel
    * @param doZStack - If false, then z stacks will be skipped for this channel
    * @param offset - If nonzero, offsets z positions for this channel by the provided amount, in microns.
    * @param s8 - Provides contrast settings for this channel for 8-bit images.
    * @param s16 - Provides contrast settings for this channel for 16-bit images.
    * @param skip - If nonzero, this channel is skipped for some frames.
    * @param c - Provides the preferred color for this channel
    * @param use - If false, this channel will not be included in the acquisition.
    */
   public boolean addChannel(String name, double exp, Boolean doZStack,
           double offset, ContrastSettings s8, ContrastSettings s16, int skip, Color c,
           boolean use);

   /*
    * Adds a channel to the acquisition settings.
    * @param name - The name of the channel, matching a configuration preset in the channel group.
    * @param exp - The exposure time for this channel
    * @param doZStack - If false, then z stacks will be skipped for this channel
    * @param offset - If nonzero, offsets z positions for this channel by the provided amount, in microns.
    * @param con - Provides contrast settings for this channel.
    * @param skip - If nonzero, this channel is skipped for some frames.
    * @param c - Provides the preferred color for this channel
    * @param use - If false, this channel will not be included in the acquisition.
    */
   public boolean addChannel(String name, double exp, Boolean doZStack,
           double offset, ContrastSettings con, int skip, Color c,
           boolean use);

   /*
    * Sets whether image data should be stored to disk or to RAM during
    * acquisition.
    * @param selected - If true, image data will be saved to disk during acquisition.
    *
    */
   public void setSaveFiles(boolean selected);

   /*
    * Returns the settings that if true, indicates images will be saved
    * to disk during acquisition.
    */
   public boolean getSaveFiles();

   /**
    * @Deprecated
    */
   public int getDisplayMode();

   /**
    * @Deprecated
    */
   public void setDisplayMode(int mode);

   /**
    * Returns the setting for the order of the four dimensions (P, T, C, Z).
    * Possible values are enumerated in org.micromanager.utils.AcqOrderMode
    */
   public int getAcqOrderMode();

   /**
    * Sets the value for the order of the four dimensions (P, T, C, Z).
    * Possible values are enumerated in org.micromanager.utils.AcqOrderMode
    */
   public void setAcqOrderMode(int mode);

   /*
    * If set to true, autofocus will be used during the acquisition.
    */
   public void enableAutoFocus(boolean enabled);

   /*
    * Returns true if autofocus is requested for the acquisition.
    */
   public boolean isAutoFocusEnabled();

   /*
    * Returns the number of frames acquired between autofocusing frames.
    * For example, if the interval is 1, then autofocus is run every other
    * frame, including the first frame.
    */
   public int getAfSkipInterval();

   /*
    * Sets the number of frames acquired when autofocusing is skipped. For
    * example, if the interval is set to 1, then autofocus is run for every
    * other frame, including the first frame.
    */
   public void setAfSkipInterval (int interval);

   /*
    * @Deprecated
    */
   public void setSingleFrame(boolean selected);

   /*
    * @Deprecated
    */
   public void setSingleWindow(boolean selected);

   /*
    * @Deprecated
    */
   public String installAutofocusPlugin(String className);
   
   // utility
   public String getVerboseSummary();

   
   public boolean isConfigAvailable(String config_);

   /*
    * @Deprecated
    * Returns available configurations for the camera group.
    */
   public String[] getCameraConfigs();

   /*
    * Returns the available groups in Micro-Manager's configuration settings.
    */
   public String[] getAvailableGroups();

   /*
    * Returns the current z position for the focus drive used by the
    * acquisition engine.
    */
   public double getCurrentZPos();

   /*
    * Returns true if the acquisition is currently paused.
    */
   public boolean isPaused();

   /**
    * Adds an image processor to the DataProcessor pipeline.
    */
   public void addImageProcessor(DataProcessor<TaggedImage> processor);

   /**
    * Removes an image processor from the DataProcessor pipeline.
    */
   public void removeImageProcessor(DataProcessor<TaggedImage> taggedImageProcessor);

   /**
    * Replace the current DataProcessor pipeline with the provided one.
    */
   public void setImageProcessorPipeline(List<DataProcessor<TaggedImage>> pipeline);

   /**
    * Return a copy of the entire DataProcessor pipeline.
    */
   public ArrayList<DataProcessor<TaggedImage>> getImageProcessorPipeline();

   /**
    * Register a DataProcessor class for later use under a unique name.
    */
   public void registerProcessorClass(Class<? extends DataProcessor<TaggedImage>> processorClass, String name);

   /**
    * Get a sorted list of registered DataProcessor names.
    */
   public List<String> getSortedDataProcessorNames();

   /**
    * Given a DataProcessor name (see above), create a new DataProcessor
    * and add it to the image processor pipeline.
    */
   public DataProcessor<TaggedImage> makeProcessor(String Name, ScriptInterface gui);

   /**
    * Return the first DataProcessor in the pipeline registered under the 
    * given name, or null if there is none.
    */
   public DataProcessor<TaggedImage> getProcessorRegisteredAs(String name);

   /**
    * Return the String under which the given DataProcessor type is 
    * registered, or null if there is none.
    */
   public String getNameForProcessorClass(Class<? extends DataProcessor<TaggedImage>> processor);

   /**
    * Dispose of the GUIs generated by all DataProcessors we know about.
    */
   public void disposeProcessors();

   /*
    * Returns true if abortRequest() has been called -- the acquisition may
    * still be running.
    */
   public boolean abortRequested();

   /*
    * Returns a time (in milliseconds) indicating when the next image is
    * expected to be acquired.
    */
   public long getNextWakeTime();

   /*
    * Returns true if the acquisition has finished running and no more hardware
    * events will be run.
    */
   public boolean isFinished();
   
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

   /*
    * Get the summary metadata for the most recent acquisition.
    */
   public JSONObject getSummaryMetadata();

   public Datastore getDatastore();

   public List<DataProcessor<TaggedImage>> getImageProcessors();

   public String getComment();

   public void addSettingsListener(AcqSettingsListener listener);

   public void removeSettingsListener(AcqSettingsListener listener);
   
   public boolean getZAbsoluteMode();
}

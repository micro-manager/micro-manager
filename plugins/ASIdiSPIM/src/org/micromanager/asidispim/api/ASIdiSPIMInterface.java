///////////////////////////////////////////////////////////////////////////////
//FILE:          ASIdiSPIMInterface.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2014
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

package org.micromanager.asidispim.api;

import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;

/**
 * This interface defines an API for interfacing with the ASIdiSPIM plugin.
 */

/**
 *
 * @author nico
 * @author Jon
 */
public interface ASIdiSPIMInterface {
   
   /**
    * Requests an acquisition using the current settings, i.e., the settings
    * as visible in the acquisition panel.  The definition of current
    * settings may change in the future.  Does nothing if an acquisition
    * is currently running or has been requested.
    * @see stopAcquisition()
    * @throws org.micromanager.asidispim.api.ASIdiSPIMException
    */
   public void runAcquisition() throws ASIdiSPIMException;
   
   /**
    * Stops an acquisition, if one is currently running.  Also cancels a 
    *   requested acquisition that is not yet running.
    * @see startAcquisition()
    * @throws ASIdiSPIMException
    */
   public void stopAcquisition() throws ASIdiSPIMException;
   
   /**
    * @return true if acquisition is currently running.
    *   "Downtime" during a multi-timepoint acquisition is considered running.
    *   There is a delay between requesting an acquisition and actually
    *     starting to run it due to time to perform sanity checks, initialize
    *     the controller and cameras, etc.
    * @see isAcquisitionRequested(), runAcquisition(), stopAcquisition()
    * @throws ASIdiSPIMException
    */
   public boolean isAcquisitionRunning() throws ASIdiSPIMException;
   
   /**
    * @return true if acquisition has been requested or is currently running.
    *   There is a delay between requesting an acquisition and actually
    *     starting to run it due to time to perform sanity checks, initialize
    *     the controller and cameras, etc.  It is possible for the acquisition
    *     to be stopped before being started during this interval when
    *     it has been requested but not actually started.
    * @see isAcquisitionRunning(), runAcquisition(), stopAcquisition()
    * @throws ASIdiSPIMException
    */
   public boolean isAcquisitionRequested() throws ASIdiSPIMException;

   /**
    * @return pathname on filesystem to last completed acquisition
    *   (even if it was stopped pre-maturely).  Will return an empty
    *   string if no acquisition has run, or null if the last acquisition
    *   was not saved to disk.
    * @throws ASIdiSPIMException
    */
   public String getLastAcquisitionPath() throws ASIdiSPIMException;

   /**
    * @return the internal Micro-Manager name for the last acquisition.
    *   Will start with the prefix specified previously, though may have
    *   appended characters.
    * @throws ASIdiSPIMException
    */
   public String getLastAcquisitionName() throws ASIdiSPIMException;
   
   /**
    * Closes the window associated with the last acquisition.
    * Equivalent to closeAcquisitionWindow(getLastAcquisitionName()).
    * @throws ASIdiSPIMException
    */
   public void closeLastAcquisitionWindow() throws ASIdiSPIMException;
   
   /**
    * Closes the acquisition window corresponding to the specified acquisition
    *   name.  Note that the acquisition name may be different from the
    *   prefix and also from the final field of the filesystem's pathname
    *   if it was saved to disk.
    * @param acquisitionName
    * @throws ASIdiSPIMException
    */
   public void closeAcquisitionWindow(String acquisitionName) throws ASIdiSPIMException;
   
   /**
    * @return directory is a string comprising the pathname on filesystem
    *   where the acquisition data is being saved
    * @throws ASIdiSPIMException
    */
   public String getSavingDirectoryRoot() throws ASIdiSPIMException;
   
   /**
    * Attempts to set the directory for saving acquisition data.
    * @param directory is a string comprising the pathname on filesystem
    * @throws ASIdiSPIMException
    */
   public void setSavingDirectoryRoot(String directory) throws ASIdiSPIMException;
   
   /**
    * @return the name of the acquisition, or more precisely the
    *   "prefix" which Micro-Manager uses (it may append numbers
    *   to avoid duplicate names)
    */
   public String getSavingNamePrefix() throws ASIdiSPIMException;
   
   /**
    * Changes the name of the next acquisition.  This is the "prefix" in
    *   Micro-Manager's terminology.  Numbers may be appended to avoid
    *   duplicate filenames.
    * @param acqPrefix
    * @throws ASIdiSPIMException
    */
   public void setSavingNamePrefix(String acqPrefix) throws ASIdiSPIMException;
   
   /**
    * Deprecated version of setSavingNamePrefix
    * @deprecated
    */
   public void setAcquisitionNamePrefix(String acqName) throws ASIdiSPIMException;
   
   /**
    * @return true if each timepoint gets its own file, false if all timepoints
    *   are in one file
    * @throws ASIdiSPIMException
    */
   public boolean getSavingSeparateFile() throws ASIdiSPIMException;
   
   /**
    * @param separate is a boolean, true means checkbox is checked so that 
    *   each timepoint gets its own file of images.
    * @throws ASIdiSPIMException
    */
   public void setSavingSeparateFile(boolean separate) throws ASIdiSPIMException;
   
   /**
    * @return true if data is being saved to disk as it is generated
    * @throws ASIdiSPIMException
    */
   public boolean getSavingSaveWhileAcquiring() throws ASIdiSPIMException;
   
   /**
    * @param save, true means checkbox is checked so that data
    *   is saved to disk as it is generated
    * @throws ASIdiSPIMException
    */
   public void setSavingSaveWhileAcquiring(boolean save) throws ASIdiSPIMException;
   
   /**
    * @return mode is which acquisition mode is current selected
    * @throws ASIdiSPIMException
    */
   public org.micromanager.asidispim.Data.AcquisitionModes.Keys getAcquisitionMode() throws ASIdiSPIMException;
   
   /**
    * @param mode determines whether the piezo/slice are moved synchronously,
    *   only one is moved, stage scanning is used, etc. 
    * @throws ASIdiSPIMException
    */
   public void setAcquisitionMode(org.micromanager.asidispim.Data.AcquisitionModes.Keys mode) throws ASIdiSPIMException;
   
   /**
    * @return true if time points have been enabled
    * @throws ASIdiSPIMException
    */
   public boolean getTimepointsEnabled() throws ASIdiSPIMException;
   
   /**
    * @param enabled, true means checkbox is checked so that multiple time
    *   points are collected
    * @throws ASIdiSPIMException
    */
   public void setTimepointsEnabled(boolean enabled) throws ASIdiSPIMException;
   
   /**
    * @return number of time points set.  Will return GUI selection even if time points
    *   have been disabled.
    * @throws ASIdiSPIMException
    */
   public int getTimepointsNumber() throws ASIdiSPIMException;
   
   /**
    * @param numTimepoints sets the number of time points.  Works even if time
    *   point have been disabled, though in that case the number is meaningless.
    *   If numTimepoints is not between 1 and 32000 then ASIdiSPIMException is thrown.
    * @throws ASIdiSPIMException
    */
   public void setTimepointsNumber(int numTimepoints) throws ASIdiSPIMException;
   
   /**
    * @return interval between time points in seconds.
    * @throws ASIdiSPIMException
    */
   public double getTimepointInterval() throws ASIdiSPIMException; 
   
   /**
    * @param intervalTimepoints sets the interval between time points in seconds.
    * If intervalTimepoints is not between 0.1 and 32000 then ASIdiSPIMException is thrown.
    * @throws ASIdiSPIMException
    */
   public void setTimepointInterval(double intervalTimepoints) throws ASIdiSPIMException; 
   
   /**
    * @return true if multiple positions have been enabled
    * @throws ASIdiSPIMException
    */
   public boolean getMultiplePositionsEnabled() throws ASIdiSPIMException;
   
   /**
    * @param enabled, true means multiple XY positions will be enabled
    * @throws ASIdiSPIMException
    */
   public void setMultiplePositionsEnabled(boolean enabled) throws ASIdiSPIMException;
   
   /**
    * @return delay additional time in milliseconds after move completes
    *   before imaging begins (e.g. to let system settle mechanically)
    * @throws ASIdiSPIMException
    */
   public double getMultiplePositionsPostMoveDelay() throws ASIdiSPIMException;
   
   /**
    * @param delayMs sets the additional delay in milliseconds after each move
    *   completes before imaging begins (e.g. to let system settle mechanically).
    *   If delayMs is not between 0 and 10000 then ASIdiSPIMException is thrown.
    * @throws ASIdiSPIMException
    */
   public void setMultiplePositionsPostMoveDelay(double delayMs) throws ASIdiSPIMException;
   
   
   /**
    * Convenience method to get the stage position list
    *   (which belongs to the main MM application)
    * @throws ASIdiSPIMException
    */
   public PositionList getPositionList() throws ASIdiSPIMException;
   
   /**
    * Convenience method to get the number of positions in the stage position list
    *   (which belongs to the main MM application)
    * @return
    * @throws ASIdiSPIMException
    */
   public int getNumberOfPositions() throws ASIdiSPIMException;
   
   /**
    * Convenience method to get the indicated index of the position list
    * @param idx integer index, zero-indexed
    * @return
    * @throws ASIdiSPIMException
    */
   public MultiStagePosition getPositionFromIndex(int idx) throws ASIdiSPIMException;
   
   /**
    * Convenience method to move to the specified position of the position list
    * @param idx integer index, zero-indexed
    * @throws ASIdiSPIMException
    */
   public void moveToPositionFromIndex(int idx) throws ASIdiSPIMException;

   /**
    * @return true if channels have been enabled
    * @throws ASIdiSPIMException
    */
   public boolean getChannelsEnabled() throws ASIdiSPIMException;
   
   /**
    * @param enabled, true means checkbox is checked so that channel selections
    *   are used (including multiple channels per acquisition)
    * @throws ASIdiSPIMException
    */
   public void setChannelsEnabled(boolean enabled) throws ASIdiSPIMException;
   
   /**
    * @return selected channel group from which presets will be selected
    *   for channel table
    * @throws ASIdiSPIMException
    */
   public String getChannelGroup() throws ASIdiSPIMException;
   
   /**
    * @param channelGroup, string to set the current channel group.
    * @throws ASIdiSPIMException
    */
   public void setChannelGroup(String channelGroup) throws ASIdiSPIMException;
   
   /**
    * @param channelPreset
    * @return true if the specified preset is present and selected to be used
    * @throws ASIdiSPIMException
    */
   public boolean getChannelPresetEnabled(String channelPreset) throws ASIdiSPIMException;
   
   /**
    * Use to enable/disable channels.  If the specified preset is not yet listed
    *   then it will be added to the table.
    * @param channelPreset
    * @param enabled
    * @throws ASIdiSPIMException
    */
   public void setChannelPresetEnabled(String channelPreset, boolean enabled) throws ASIdiSPIMException;
   
   /**
    * @return the channel change mode, VOLUME for (software) volume-by-volume,
    *   VOLUME_HW for PLogic-based volume-by-volume, and SLICE_HW for PLogic-based slice-by-slice 
    * @throws ASIdiSPIMException
    */
   public org.micromanager.asidispim.Data.MultichannelModes.Keys getChannelChangeMode() throws ASIdiSPIMException;
   
   /**
    * Sets the switching mode between the channels.
    * @param mode
    * @throws ASIdiSPIMException
    */
   public void setChannelChangeMode(org.micromanager.asidispim.Data.MultichannelModes.Keys mode) throws ASIdiSPIMException;
   
   /**
    * @return number of sides per volume, either 1 or 2
    * @throws ASIdiSPIMException
    */
   public int getVolumeNumberOfSides() throws ASIdiSPIMException;
   
   /**
    * Sets the number of sides acquired.
    * @param numSides should be 1 or 2.
    * @throws ASIdiSPIMException
    */
   public void setVolumeNumberOfSides(int numSides) throws ASIdiSPIMException;
   
   /**
    * @return either "A" or "B" depending on which side is selected to go first
    * @throws ASIdiSPIMException
    */
   public String getVolumeFirstSide() throws ASIdiSPIMException;
   
   /**
    * Sets the first side to be acquired (or only side if only 1 sided acquisition
    *   is done).
    * @param firstSide Should be "A" or "B".
    * @throws ASIdiSPIMException
    */
   public void setVolumeFirstSide(String firstSide) throws ASIdiSPIMException;

   /**
    * @return delay before each side during acquisition in milliseconds.
    * @throws ASIdiSPIMException
    */
   public double getVolumeDelayBeforeSide() throws ASIdiSPIMException;
   
   /**
    * @param delay the delay in milliseconds before each side.  Usually used
    *   to allow for mechanical settling, at least 50 ms.
    * @throws ASIdiSPIMException
    */
   public void setVolumeDelayBeforeSide(double delayMs) throws ASIdiSPIMException;
   
   /**
    * @return number of slices of acquired data per volume.  Note that in 
    *   overlap/sychronous camera mode more triggers are sent.
    * @throws ASIdiSPIMException
    */
   public int getVolumeSlicesPerVolume() throws ASIdiSPIMException;
   
   /**
    * @param slices number of slices to be acquired per side
    * @throws ASIdiSPIMException
    */
   public void setVolumeSlicesPerVolume(int slices) throws ASIdiSPIMException;
   
   /**
    * @return step size between successive slices in microns
    * @throws ASIdiSPIMException
    */
   public double getVolumeSliceStepSize() throws ASIdiSPIMException;
   
   /**
    * @param stepSizeUm step size in microns between successive slices
    * @throws ASIdiSPIMException
    */
   public void setVolumeSliceStepSize(double stepSizeUm) throws ASIdiSPIMException;
   
   /**
    * @return true if "minimize slice period" is selected
    * @throws ASIdiSPIMException
    */
   public boolean getVolumeMinimizeSlicePeriod() throws ASIdiSPIMException;
   
   /**
    * @param minimize true to have the plugin automatically minimize the slice period
    * @throws ASIdiSPIMException
    */
   public void setVolumeMinimizeSlicePeriod(boolean minimize) throws ASIdiSPIMException;
   
   /**
    * @return requested duration of each period in milliseconds.  Note this only
    *   applies when "minimize slice period" is unchecked/set to false.
    * @throws ASIdiSPIMException
    */
   public double getVolumeSlicePeriod() throws ASIdiSPIMException;
   
   /**
    * @param periodMs requested duration of each period, only used if the 
    *   "Minimize slice period" is unchecked
    * @throws ASIdiSPIMException
    */
   public void setVolumeSlicePeriod(double periodMs) throws ASIdiSPIMException;
   
   /**
    * @return exposure time in milliseconds that the laser will be on to
    *   expose the sample. The galvo sweep time is usually 0.5 ms longer,
    *   and this value is usually a half-integer (e.g. 3.5, 4.5, etc.)
    * @throws ASIdiSPIMException
    */
   public double getVolumeSampleExposure() throws ASIdiSPIMException;
   
   /**
    * @param exposureMs is the exposure time of the sample to the laser in
    *   milliseconds (related but not the same as the camera's exposure time).
    *   Currently will be rounded to the nearest half-integer (e.g. 3.5, 4.5, etc.)
    * @throws ASIdiSPIMException
    */
   public void setVolumeSampleExposure(double exposureMs) throws ASIdiSPIMException;
   
   /**
    * @return true if autofocus will be performed during acquisition.
    * @throws ASIdiSPIMException
    */
   public boolean getAutofocusDuringAcquisition() throws ASIdiSPIMException;
   
   /**
    * @param enable true to enable autofocus during acquisition.  Parameters for that
    *   are set on the autofocus tab or using this API.
    * @throws ASIdiSPIMException
    */
   public void setAutofocusDuringAcquisition(boolean enable) throws ASIdiSPIMException;
   
   /**
    * @return number of images in each autofocus stack
    * @throws ASIdiSPIMException
    */
   public int getAutofocusNumImages() throws ASIdiSPIMException;
   
   /**
    * @param numImages number of images to take in autofocus stack
    * @throws ASIdiSPIMException
    */
   public void setAutofocusNumImages(int numImages) throws ASIdiSPIMException;
   
   /**
    * @return step size in microns between images in autofocus stack
    * @throws ASIdiSPIMException
    */
   public double getAutofocusStepSize() throws ASIdiSPIMException; 
   
   /**
    * @param stepSizeUm step size between successive autofocus images
    * @throws ASIdiSPIMException
    */
   public void setAutofocusStepSize(double stepSizeUm) throws ASIdiSPIMException; 

   /**
    * @return mode used by autofocus, either FIX_PIEZO for fixing the piezo and sweeping the slice
    *   or FIX_SLICE for fixing the slice and sweeping the piezo
    * @throws ASIdiSPIMException
    */
   public org.micromanager.asidispim.AutofocusPanel.Modes getAutofocusMode() throws ASIdiSPIMException;
   
   /**
    * @param mode either FIX_PIEZO or FIX_SLICE depending on whether piezo is fixed and
    *   slice is swept or vice versa.
    * @throws ASIdiSPIMException
    */
   public void setAutofocusMode(org.micromanager.asidispim.AutofocusPanel.Modes mode) throws ASIdiSPIMException;
   
   /**
    * @return true if autofocus will be performed before starting the acquisition.
    *   Only applies if "autofocus during acquisition" has been enabled
    * @throws ASIdiSPIMException
    */
   public boolean getAutofocusBeforeAcquisition() throws ASIdiSPIMException;
   
   /**
    * @param enable true will run autofocus before the acquisition begins.
    *   Only applies if "autofocus during acquisition" has been enabled
    * @throws ASIdiSPIMException
    */
   public void setAutofocusBeforeAcquisition(boolean enable) throws ASIdiSPIMException;
   
   /**
    * @return how often (in time points) that the autofocus runs during acquisition.
    * @throws ASIdiSPIMException
    */
   public int getAutofocusInterval() throws ASIdiSPIMException;
   
   /**
    * @param numTimepoints will run autofocus after this many time points
    * @throws ASIdiSPIMException
    */
   public void setAutofocusInterval(int numTimepoints) throws ASIdiSPIMException;
   
   /**
    * @return which channel will be used for autofocus during acquisition
    * @throws ASIdiSPIMException
    */
   public String getAutofocusChannel() throws ASIdiSPIMException;
   
   /**
    * @param channel set the channel to be used for autofocus during acquisition
    * @throws ASIdiSPIMException
    */
   public void setAutofocusChannel(String channel) throws ASIdiSPIMException;
   
   /**
    * Convenience method to set the position of the XY stage.  Blocks until move
    *   complete.
    * @param x in microns
    * @param y in microns
    * @throws ASIdiSPIMException
    */
   public void setXYPosition(double x, double y) throws ASIdiSPIMException;

   /**
    * Convenience method.
    * @return XY position of the stage in microns
    * @throws ASIdiSPIMException
    */
   public java.awt.geom.Point2D.Double getXYPosition() throws ASIdiSPIMException;
   
   /**
    * Convenience method to set the position of the lower Z stage.
    * @param z in microns
    * @throws ASIdiSPIMException
    */
   public void setLowerZPosition(double z) throws ASIdiSPIMException;
   
   /**
    * Convenience method to set the position of the lower Z stage
    * @return position in microns
    * @throws ASIdiSPIMException
    */
   public double getLowerZPosition() throws ASIdiSPIMException;
   
   /**
    * Convenience method to set the position of the SPIM head.
    * @param z in microns
    * @throws ASIdiSPIMException
    */
   public void setSPIMHeadPosition(double z) throws ASIdiSPIMException;
   
   /**
    * Convenience method to set the position of the SPIM head.
    * @return position in microns
    * @throws ASIdiSPIMException
    */
   public double getSPIMHeadPosition() throws ASIdiSPIMException;
   
   /**
    * Raises the SPIM head to the position on the Navigation panel indicated
    *   by "load sample".
    * @throws ASIdiSPIMException
    */
   public void raiseSPIMHead() throws ASIdiSPIMException;

   /**
    * @param raised SPIM head position in microns when in raised state ("load sample")
    * @throws ASIdiSPIMException
    */
   public void setSPIMHeadRaisedPosition(double raised) throws ASIdiSPIMException; 
   
   /**
    * @return SPIM head position in microns when in raised state ("load sample")
    * @throws ASIdiSPIMException
    */
   public double getSPIMHeadRaisedPosition() throws ASIdiSPIMException; 
   
   /**
    * Lowers the SPIM head to the position on the Navigation panel indicated
    *   by "start hunting".
    * @throws ASIdiSPIMException
    */
   public void lowerSPIMHead() throws ASIdiSPIMException;
   
   /**
    * @param raised SPIM head position in microns when in lowered state ("start hunting")
    * @throws ASIdiSPIMException
    */
   public void setSPIMHeadLoweredPosition(double raised) throws ASIdiSPIMException; 
   
   /**
    * @return SPIM head position in microns when in lowered state ("start hunting")
    * @throws ASIdiSPIMException
    */
   public double getSPIMHeadLoweredPosition() throws ASIdiSPIMException;
   
   /**
    * @return object with all acquisition settings.  Intended for informational purposes
    *   only.  Contact the developers if you need to use a modified copy of this object
    *   to change the acquisition settings.  The fields of the AcquisitionSettings
    *   object may change in the future.
    * @throws ASIdiSPIMException
    */
   public org.micromanager.asidispim.Data.AcquisitionSettings getAcquisitionSettings() throws ASIdiSPIMException;
   
}

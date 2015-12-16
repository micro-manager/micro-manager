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

import org.micromanager.api.PositionList;
import org.micromanager.asidispim.Data.Devices;

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
    * @see ASIdiSPIMInterface#stopAcquisition()
    */
   public void runAcquisition() throws ASIdiSPIMException;
   
   /**
    * Stops an acquisition, if one is currently running.  Also cancels a 
    *   requested acquisition that is not yet running.
    * @see ASIdiSPIMInterface#runAcquisition()
    */
   public void stopAcquisition() throws ASIdiSPIMException;
   
   /**
    * @return true if acquisition is currently running. "Downtime"
    *   during a multi-timepoint acquisition is considered running.
    *   There is a delay between requesting an acquisition and actually
    *   starting to run it due to time to perform sanity checks, initialize
    *   the controller and cameras, etc.
    * @see ASIdiSPIMInterface#isAcquisitionRequested()
    * @see ASIdiSPIMInterface#runAcquisition()
    * @see ASIdiSPIMInterface#stopAcquisition()
    */
   public boolean isAcquisitionRunning() throws ASIdiSPIMException;
   
   /**
    * @return true if acquisition has been requested or is currently running.
    *   There is a delay between requesting an acquisition and actually
    *     starting to run it due to time to perform sanity checks, initialize
    *     the controller and cameras, etc.  It is possible for the acquisition
    *     to be stopped before being started during this interval when
    *     it has been requested but not actually started.
    * @see ASIdiSPIMInterface#isAcquisitionRunning()
    * @see ASIdiSPIMInterface#runAcquisition()
    * @see ASIdiSPIMInterface#stopAcquisition()
    */
   public boolean isAcquisitionRequested() throws ASIdiSPIMException;

   /**
    * @return pathname on filesystem to last completed acquisition
    *   (even if it was stopped pre-maturely).  Will return an empty
    *   string if no acquisition has run, or null if the last acquisition
    *   was not saved to disk.
    */
   public String getLastAcquisitionPath() throws ASIdiSPIMException;

   /**
    * @return the internal Micro-Manager name for the last acquisition.
    *   Will start with the prefix specified previously, though it may 
    *   have appended characters for uniqueness.
    */
   public String getLastAcquisitionName() throws ASIdiSPIMException;
   
   /**
    * Closes the window associated with the last acquisition.
    * Equivalent to closeAcquisitionWindow(getLastAcquisitionName()).
    */
   public void closeLastAcquisitionWindow() throws ASIdiSPIMException;
   
   /**
    * Closes the acquisition window corresponding to the specified acquisition
    *   name.  Note that the acquisition name may be different from the
    *   prefix and also from the final field of the filesystem's pathname
    *   if it was saved to disk.
    * @param acquisitionName
    */
   public void closeAcquisitionWindow(String acquisitionName) throws ASIdiSPIMException;
   
   /**
    * @return directory is a string comprising the pathname on filesystem
    *   where the acquisition data is being saved
    */
   public String getSavingDirectoryRoot() throws ASIdiSPIMException;
   
   /**
    * Attempts to set the directory for saving acquisition data.
    * @param directory is a string comprising the pathname on filesystem
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
    */
   public void setSavingNamePrefix(String acqPrefix) throws ASIdiSPIMException;
   
   /**
    * Deprecated version of setSavingNamePrefix
    * @deprecated replaced by setSavingNamePrefix()
    */
   public void setAcquisitionNamePrefix(String acqName) throws ASIdiSPIMException;
   
   /**
    * @return true if each timepoint gets its own file, false if all timepoints
    *   are in one file
    */
   public boolean getSavingSeparateFile() throws ASIdiSPIMException;
   
   /**
    * @param separate is a boolean, true means checkbox is checked so that 
    *   each timepoint gets its own file of images.
    */
   public void setSavingSeparateFile(boolean separate) throws ASIdiSPIMException;
   
   /**
    * @return true if data is being saved to disk as it is generated
    */
   public boolean getSavingSaveWhileAcquiring() throws ASIdiSPIMException;
   
   /**
    * @param save, true means checkbox is checked so that data
    *   is saved to disk as it is generated
    */
   public void setSavingSaveWhileAcquiring(boolean save) throws ASIdiSPIMException;
   
   /**
    * @return mode is which acquisition mode is current selected
    */
   public org.micromanager.asidispim.Data.AcquisitionModes.Keys getAcquisitionMode() throws ASIdiSPIMException;
   
   /**
    * @param mode determines whether the piezo/slice are moved synchronously,
    *   only one is moved, stage scanning is used, etc. 
    */
   public void setAcquisitionMode(org.micromanager.asidispim.Data.AcquisitionModes.Keys mode) throws ASIdiSPIMException;
   
   /**
    * @return true if time points have been enabled
    */
   public boolean getTimepointsEnabled() throws ASIdiSPIMException;
   
   /**
    * @param enabled, true means checkbox is checked so that multiple time
    *   points are collected
    */
   public void setTimepointsEnabled(boolean enabled) throws ASIdiSPIMException;
   
   /**
    * @return number of time points set.  Will return GUI selection even if time points
    *   have been disabled.
    */
   public int getTimepointsNumber() throws ASIdiSPIMException;
   
   /**
    * @param numTimepoints sets the number of time points.  Works even if time
    *   point have been disabled, though in that case the number is meaningless.
    *   If numTimepoints is not between 1 and 32000 then ASIdiSPIMException is thrown.
    */
   public void setTimepointsNumber(int numTimepoints) throws ASIdiSPIMException;
   
   /**
    * @return interval between time points in seconds.
    */
   public double getTimepointInterval() throws ASIdiSPIMException; 
   
   /**
    * @param intervalTimepoints sets the interval between time points in seconds.
    * If intervalTimepoints is not between 0.1 and 32000 then ASIdiSPIMException is thrown.
    */
   public void setTimepointInterval(double intervalTimepoints) throws ASIdiSPIMException; 
   
   /**
    * @return true if multiple positions have been enabled
    */
   public boolean getMultiplePositionsEnabled() throws ASIdiSPIMException;
   
   /**
    * @param enabled, true means multiple XY positions will be enabled
    */
   public void setMultiplePositionsEnabled(boolean enabled) throws ASIdiSPIMException;
   
   /**
    * @return delay additional time in milliseconds after move completes
    *   before imaging begins (e.g. to let system settle mechanically)
    */
   public double getMultiplePositionsDelay() throws ASIdiSPIMException;
   
   /**
    * @param delayMs sets the additional delay in milliseconds after each move
    *   completes before imaging begins (e.g. to let system settle mechanically).
    *   If delayMs is not between 0 and 10000 then ASIdiSPIMException is thrown.
    */
   public void setMultiplePositionsDelay(double delayMs) throws ASIdiSPIMException;
   
   
   /**
    * Convenience method to get the stage position list
    *   (which belongs to the main MM application).  Its API is at
    *   https://valelab.ucsf.edu/~MM/doc/mmstudio/org/micromanager/api/PositionList.html.
    *   You can do operations such as adding and removing positions, moving to a
    *   specified position in the list, etc.
    */
   public PositionList getPositionList() throws ASIdiSPIMException;
   
   /**
    * @return true if multi-channel acquisition has been enabled (whole box enabled/disabled).
    * @see ASIdiSPIMInterface#getChannelEnabled(String) to see if an individual channel is enabled or not
    */
   public boolean getChannelsEnabled() throws ASIdiSPIMException;
   
   /**
    * @param enabled, true means checkbox is checked so that channel selections
    *   are used (including multiple channels per acquisition)
    */
   public void setChannelsEnabled(boolean enabled) throws ASIdiSPIMException;
   
   /**
    * @return list of all valid channel groups (depends on main MM configuration).
    *   Somewhat different from the core's method getAllowedPropertyValues() of
    *   the special property "ChannelGroup" of device "Core".
    */
   public String[] getAvailableChannelGroups() throws ASIdiSPIMException;
   
   /**
    * @return selected channel group from which presets will be selected
    *   for channel table
    */
   public String getChannelGroup() throws ASIdiSPIMException;
   
   /**
    * @param channelGroup, string to set the current channel group.  If it is not a valid
    *   channel, i.e. not in the list returned by getAvailableChannelGroups(),
    *   then an ASIdiSPIMException will be thrown.
    */
   public void setChannelGroup(String channelGroup) throws ASIdiSPIMException;
   
   /**
    * @return list of all valid channels (AKA presets) for currently-selected channel group
    */
   public String[] getAvailableChannels() throws ASIdiSPIMException;   
   
   /**
    * @param channel
    * @return true if the specified channel is present in the table and selected to be used
    * If it is not a valid channel, i.e. not in the list returned by getAvailableChannels(),
    *   then an ASIdiSPIMException will be thrown.
    * If the channels operation has been disabled
    *   (i.e. if getChannelsEnabled()==false) then this will always return false.
    * If the channel is valid but not present in the table then it will return false. 
    * @see ASIdiSPIMInterface#getChannelsEnabled(String) to see if multi-channel operation is enabled
    */
   public boolean getChannelEnabled(String channel) throws ASIdiSPIMException;
   
   /**
    * Use to enable/disable channels.  If the specified channel is not yet present in
    *   the table then it will be added if it is a valid channel.  If it is not a valid
    *   channel, i.e. not in the list returned by getAvailableChannels(),
    *   then an ASIdiSPIMException will be thrown.
    * @param channel
    * @param enabled
    */
   public void setChannelEnabled(String channel, boolean enabled) throws ASIdiSPIMException;

   /**
    * @return the channel change mode, VOLUME for (software) volume-by-volume,
    *   VOLUME_HW for PLogic-based volume-by-volume, and SLICE_HW for PLogic-based slice-by-slice
    * @deprecated out of laziness, can add if needed
    */
   public org.micromanager.asidispim.Data.MultichannelModes.Keys getChannelChangeMode() throws ASIdiSPIMException;
   
   /**
    * Sets the switching mode between the channels.
    * @param mode
    * @deprecated out of laziness, can add if needed
    */
   public void setChannelChangeMode(org.micromanager.asidispim.Data.MultichannelModes.Keys mode) throws ASIdiSPIMException;
   
   /**
    * @return number of sides per volume, either 1 or 2
    */
   public int getVolumeNumberOfSides() throws ASIdiSPIMException;
   
   /**
    * Sets the number of sides acquired.
    * @param numSides, usually 1 or 2.  If not then an ASIdiSPIMException will be thrown.
    */
   public void setVolumeNumberOfSides(int numSides) throws ASIdiSPIMException;
   
   /**
    * @return either Devices.Sides.A or Devices.Sides.B depending on which side is selected to go first
    */
   public Devices.Sides getVolumeFirstSide() throws ASIdiSPIMException;
   
   /**
    * Sets the first side to be acquired (or only side if only 1 sided acquisition
    *   is done).
    * @param firstSide Should be Devices.Sides.A or Devices.Sides.B.  If anything else
    *   is passed an ASIdiSPIMException is thrown.
    */
   public void setVolumeFirstSide(Devices.Sides side) throws ASIdiSPIMException;

   /**
    * @return delay before each side during acquisition in milliseconds.
    */
   public double getVolumeDelayBeforeSide() throws ASIdiSPIMException;
   
   /**
    * @param delay the delay in milliseconds before each side.  Usually used
    *   to allow for mechanical settling, at least 50 ms.
    *   If delayMs is not between 0 and 10000 then ASIdiSPIMException is thrown.
    */
   public void setVolumeDelayBeforeSide(double delayMs) throws ASIdiSPIMException;
   
   /**
    * @return number of slices of acquired data per volume.  Note that in 
    *   overlap/sychronous camera mode more triggers are sent.
    */
   public int getVolumeSlicesPerVolume() throws ASIdiSPIMException;
   
   /**
    * @param slices number of slices to be acquired per side
    * If slices is not between 0 and 65000 then ASIdiSPIMException is thrown.
    */
   public void setVolumeSlicesPerVolume(int slices) throws ASIdiSPIMException;
   
   /**
    * @return step size between successive slices in microns
    */
   public double getVolumeSliceStepSize() throws ASIdiSPIMException;
   
   /**
    * @param stepSizeUm step size in microns between successive slices.
    * If stepSizeUm is not between 0 and 100 then ASIdiSPIMException is thrown.
    */
   public void setVolumeSliceStepSize(double stepSizeUm) throws ASIdiSPIMException;
   
   /**
    * @return true if "minimize slice period" is selected
    */
   public boolean getVolumeMinimizeSlicePeriod() throws ASIdiSPIMException;
   
   /**
    * @param minimize true to have the plugin automatically minimize the slice period
    */
   public void setVolumeMinimizeSlicePeriod(boolean minimize) throws ASIdiSPIMException;
   
   /**
    * @return requested duration of each period in milliseconds.  Note this only
    *   applies when "minimize slice period" is unchecked/set to false.
    */
   public double getVolumeSlicePeriod() throws ASIdiSPIMException;
   
   /**
    * @param periodMs requested duration of each period, only used if the 
    *   "Minimize slice period" is unchecked.
    * If periodMs is not between 1 and 1000 then ASIdiSPIMException is thrown.
    */
   public void setVolumeSlicePeriod(double periodMs) throws ASIdiSPIMException;
   
   /**
    * @return exposure time in milliseconds that the laser will be on to
    *   expose the sample. The galvo sweep time is usually 0.5 ms longer,
    *   and this value is usually a half-integer (e.g. 3.5, 4.5, etc.)
    */
   public double getVolumeSampleExposure() throws ASIdiSPIMException;
   
   /**
    * @param exposureMs is the exposure time of the sample to the laser in
    *   milliseconds (related but not the same as the camera's exposure time).
    *   Currently will be rounded to the nearest half-integer (e.g. 3.5, 4.5, etc.)
    * If exposureMs is not between 2.5 and 1000.5 then ASIdiSPIMException is thrown.
    */
   public void setVolumeSampleExposure(double exposureMs) throws ASIdiSPIMException;
   
   /**
    * @return true if autofocus will be performed during acquisition.
    */
   public boolean getAutofocusDuringAcquisition() throws ASIdiSPIMException;
   
   /**
    * @param enable true to enable autofocus during acquisition.  Parameters for that
    *   are set on the autofocus tab or using this API.
    */
   public void setAutofocusDuringAcquisition(boolean enable) throws ASIdiSPIMException;
   
   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @return the imaging piezo's center position for acquisitions in specified side
    */
   public double getSideImagingCenter(Devices.Sides side) throws ASIdiSPIMException;

   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @param center the imaging piezo's center position on specified side
    * If center position is outside the range of the piezo then ASIdiSPIMException is thrown.
    */
   public void setSideImagingCenter(Devices.Sides side, double center) throws ASIdiSPIMException;

   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @return position of slice axis
    * @deprecated out of laziness, can add if needed
    */
   public double getSideSlicePosition(Devices.Sides side) throws ASIdiSPIMException;
   
   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @param position new position of slice axis
    * @deprecated out of laziness, can add if needed
    */
   public void setSideSlicePosition(Devices.Sides side, double position) throws ASIdiSPIMException;

   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @return position of imaging piezo
    * @deprecated out of laziness, can add if needed
    */
   public double getSideImagingPiezoPosition(Devices.Sides side) throws ASIdiSPIMException;
   
   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @param position new position of imaging piezo
    * @deprecated out of laziness, can add if needed
    */
   public void setSideImagingPiezoPosition(Devices.Sides side, double position) throws ASIdiSPIMException;

   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @return position of illumination piezo
    * @deprecated out of laziness, can add if needed
    */
   public double getSideIlluminationPiezoPosition(Devices.Sides side) throws ASIdiSPIMException;
   
   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @param position new position of illumination piezo
    * @deprecated out of laziness, can add if needed
    */
   public void setSideIlluminationPiezoPosition(Devices.Sides side, double position) throws ASIdiSPIMException;
   
   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @param position makes current position of the illumination piezo the new home position
    * @deprecated out of laziness, can add if needed
    */
   public void setSideIlluminationPiezoHome(Devices.Sides side) throws ASIdiSPIMException;

   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @return the sheet width in units of degrees
    * @deprecated out of laziness, can add if needed
    */
   public double getSideSheetWidth(Devices.Sides side) throws ASIdiSPIMException;
   
   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @param width the sheet width in units of degrees
    * @deprecated out of laziness, can add if needed
    */
   public void setSideSheetWidth(Devices.Sides side, double width) throws ASIdiSPIMException;

   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @return sheet offset from center in units of degrees (not calibration offset)
    * @deprecated out of laziness, can add if needed
    */
   public double getSideSheetOffset(Devices.Sides side) throws ASIdiSPIMException;
   
   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @param the sheet offset from center in units of degrees (not calibration offset)
    * @deprecated out of laziness, can add if needed
    */
   public void setSideSheetOffset(Devices.Sides side, double offset) throws ASIdiSPIMException;
   
   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @return calibration slope for specified side
    * @throws ASIdiSPIMException
    * @deprecated out of laziness, can add if needed
    */
   public double getSideCalibrationSlope(Devices.Sides side) throws ASIdiSPIMException;
   
   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @param slope slope of calibration in um/degree
    * @deprecated out of laziness, can add if needed
    */
   public void setSideCalibrationSlope(Devices.Sides side, double slope) throws ASIdiSPIMException;
   
   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @return calibration offset in units of um
    */
   public double getSideCalibrationOffset(Devices.Sides side) throws ASIdiSPIMException;
   
   /**
    * @param side Devices.Sides.A or Devices.Sides.B
    * @param calibration offset in units of um
    */
   public void setSideCalibrationOffset(Devices.Sides side, double offset) throws ASIdiSPIMException;
   
   /**
    * Updates the offset with the current positions of the slice and imaging piezo,
    *   just like clicking the GUI button does
    * @param side Devices.Sides.A or Devices.Sides.B
    */
   public void updateSideCalibrationOffset(Devices.Sides side) throws ASIdiSPIMException;
   
   /**
    * Runs the autofocus just like a GUI button press.  If the autofocus
    *   is successful then slice or piezo will be adjusted to the best-focus
    *   position but the offset isn't updated automatically.
    * @see ASIdiSPIMInterface#updateSideOffset()
    * @param side Devices.Sides.A or Devices.Sides.B
    */
   public void runAutofocusSide(Devices.Sides side) throws ASIdiSPIMException;
   
   // following are not included out of laziness for now, if they are needed they can be added
   /**
    * @return number of images in each autofocus stack
    * @deprecated out of laziness, can add if needed
    */
   public int getAutofocusNumImages() throws ASIdiSPIMException;
   
   /**
    * @param numImages number of images to take in autofocus stack
    * @deprecated out of laziness, can add if needed
    */
   public void setAutofocusNumImages(int numImages) throws ASIdiSPIMException;
   
   /**
    * @return step size in microns between images in autofocus stack
    * @deprecated out of laziness, can add if needed
    */
   public double getAutofocusStepSize() throws ASIdiSPIMException; 
   
   /**
    * @param stepSizeUm step size between successive autofocus images
    * @deprecated out of laziness, can add if needed
    */
   public void setAutofocusStepSize(double stepSizeUm) throws ASIdiSPIMException; 

   /**
    * @return mode used by autofocus, either FIX_PIEZO for fixing the piezo and sweeping the slice
    *   or FIX_SLICE for fixing the slice and sweeping the piezo
    * @deprecated out of laziness, can add if needed
    */
   public org.micromanager.asidispim.AutofocusPanel.Modes getAutofocusMode() throws ASIdiSPIMException;
   
   /**
    * @param mode either FIX_PIEZO or FIX_SLICE depending on whether piezo is fixed and
    *   slice is swept or vice versa.
    * @deprecated out of laziness, can add if needed
    */
   public void setAutofocusMode(org.micromanager.asidispim.AutofocusPanel.Modes mode) throws ASIdiSPIMException;
   
   /**
    * @return true if autofocus will be performed before starting the acquisition.
    *   Only applies if "autofocus during acquisition" has been enabled
    * @deprecated out of laziness, can add if needed
    */
   public boolean getAutofocusBeforeAcquisition() throws ASIdiSPIMException;
   
   /**
    * @param enable true will run autofocus before the acquisition begins.
    *   Only applies if "autofocus during acquisition" has been enabled
    * @deprecated out of laziness, can add if needed
    */
   public void setAutofocusBeforeAcquisition(boolean enable) throws ASIdiSPIMException;
   
   /**
    * @return how often (in time points) that the autofocus runs during acquisition.
    */
   public int getAutofocusTimepointInterval() throws ASIdiSPIMException;
   
   /**
    * @param numTimepoints will run autofocus after this many time points
    */
   public void setAutofocusTimepointInterval(int numTimepoints) throws ASIdiSPIMException;
   
   /**
    * @return which channel will be used for autofocus during acquisition
    * @deprecated out of laziness, can add if needed
    */
   public String getAutofocusChannel() throws ASIdiSPIMException;
   
   /**
    * @param channel set the channel to be used for autofocus during acquisition
    * @deprecated out of laziness, can add if needed
    */
   public void setAutofocusChannel(String channel) throws ASIdiSPIMException;
   
   /**
    * Convenience method to set the position of the XY stage.  Blocks until move
    *   complete.
    * @param x in microns
    * @param y in microns
    */
   public void setXYPosition(double x, double y) throws ASIdiSPIMException;

   /**
    * Convenience method.
    * @return XY position of the stage in microns
    */
   public java.awt.geom.Point2D.Double getXYPosition() throws ASIdiSPIMException;
   
   /**
    * Convenience method to set the position of the lower Z stage.
    * @param z in microns
    */
   public void setLowerZPosition(double z) throws ASIdiSPIMException;
   
   /**
    * Convenience method to set the position of the lower Z stage
    * @return position in microns
    */
   public double getLowerZPosition() throws ASIdiSPIMException;
   
   /**
    * Convenience method to set the position of the SPIM head.
    * @param z in microns
    */
   public void setSPIMHeadPosition(double z) throws ASIdiSPIMException;
   
   /**
    * Convenience method to set the position of the SPIM head.
    * @return position in microns
    */
   public double getSPIMHeadPosition() throws ASIdiSPIMException;
   
   /**
    * Raises the SPIM head to the position on the Navigation panel indicated
    *   by "load sample".
    */
   public void raiseSPIMHead() throws ASIdiSPIMException;

   /**
    * @param raised SPIM head position in microns when in raised state ("load sample")
    */
   public void setSPIMHeadRaisedPosition(double raised) throws ASIdiSPIMException; 
   
   /**
    * @return SPIM head position in microns when in raised state ("load sample")
    */
   public double getSPIMHeadRaisedPosition() throws ASIdiSPIMException; 
   
   /**
    * Lowers the SPIM head to the position on the Navigation panel indicated
    *   by "start hunting".
    */
   public void lowerSPIMHead() throws ASIdiSPIMException;
   
   /**
    * @param lowered SPIM head position in microns when in lowered state ("start hunting")
    */
   public void setSPIMHeadLoweredPosition(double lowered) throws ASIdiSPIMException; 
   
   /**
    * @return SPIM head position in microns when in lowered state ("start hunting")
    */
   public double getSPIMHeadLoweredPosition() throws ASIdiSPIMException;
   
   /**
    * Stops all motion by sending a halt signal to the Tiger controller,
    *   like the button on the navigation panel
    */
   public void haltAllMotion() throws ASIdiSPIMException;
   
   /**
    * @return object with all acquisition settings.  Intended for informational purposes
    *   only.  Contact the developers if you need to use a modified copy of this object
    *   to change the acquisition settings.  The fields of the AcquisitionSettings
    *   object may change in the future.
    */
   public org.micromanager.asidispim.Data.AcquisitionSettings getAcquisitionSettings() throws ASIdiSPIMException;
   
   /**
    * @return estimated duration of each slice in milliseconds according to current timing
    * @see ASIdiSPIMInterface#refreshEstimatedTiming()
    */
   public double getEstimatedSliceDuration();
   
   /**
    * @return estimated duration of each volume in milliseconds according to current timing
    * @see ASIdiSPIMInterface#refreshEstimatedTiming()
    */
   public double getEstimatedVolumeDuration();
   
   /**
    * @return estimated duration of entire acquisition in seconds according to current timing
    * @see ASIdiSPIMInterface#refreshEstimatedTiming()
    */
   public double getEstimatedAcquisitionDuration();
   
   /**
    * forces recalculation of estimated timings.
    */
   public void refreshEstimatedTiming();
}

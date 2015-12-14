///////////////////////////////////////////////////////////////////////////////
//FILE:          ASIdiSPIMImplementation.java
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

import java.awt.geom.Point2D.Double;

import mmcorej.CMMCore;

import org.micromanager.MMStudio;
import org.micromanager.api.PositionList;
import org.micromanager.asidispim.ASIdiSPIM;
import org.micromanager.asidispim.ASIdiSPIMFrame;
import org.micromanager.asidispim.AcquisitionPanel;
import org.micromanager.asidispim.AutofocusPanel;
import org.micromanager.asidispim.AutofocusPanel.Modes;
import org.micromanager.asidispim.NavigationPanel;
import org.micromanager.asidispim.SetupPanel;
import org.micromanager.asidispim.Data.AcquisitionModes.Keys;
import org.micromanager.asidispim.Data.AcquisitionSettings;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Devices.Sides;
import org.micromanager.utils.MMScriptException;

/**
 * Implementation of the ASidiSPIMInterface
 * To avoid depending on the internals of this class and restrict yourself
 * to the ASIdiSPIMInterface, always cast the instance of this class to ASIdiSPIMInterface
 * e.g.: 
 * 
 * import org.micromanager.asidispim.api.ASIdiSPIMInterface;
 * import org.micromanager.asidispim.api.ASIdiSPIMImplementation;
 *
 * ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
 * diSPIM.runAcquisition(); 
 * 
 * @author nico
 * @author Jon
 */
public class ASIdiSPIMImplementation implements ASIdiSPIMInterface {

   @Override
   public void runAcquisition() throws ASIdiSPIMException {
      getAcquisitionPanel().runAcquisition();
   }
   
   @Override
   public void stopAcquisition() throws ASIdiSPIMException {
      getAcquisitionPanel().stopAcquisition();
   }
   
   @Override
   public boolean isAcquisitionRequested() throws ASIdiSPIMException {
      return getAcquisitionPanel().isAcquisitionRequested();
   }
   
   @Override
   public boolean isAcquisitionRunning() throws ASIdiSPIMException {
      return getAcquisitionPanel().isAcquisitionRunning();
   }
   
   @Override
   public String getLastAcquisitionPath() throws ASIdiSPIMException {
      return getAcquisitionPanel().getLastAcquisitionPath();
   }
   
   @Override
   public String getLastAcquisitionName() throws ASIdiSPIMException {
      return getAcquisitionPanel().getLastAcquisitionName();
   }

   @Override
   public void closeLastAcquisitionWindow() throws ASIdiSPIMException {
      closeAcquisitionWindow(getLastAcquisitionName());
   }

   @Override
   public void closeAcquisitionWindow(String acquisitionName) throws ASIdiSPIMException {
      try {
         getGui().closeAcquisitionWindow(acquisitionName);
      } catch (MMScriptException e) {
         throw new ASIdiSPIMException(e);
      }
   }
   
   @Override
   public void setAcquisitionNamePrefix(String acqName) throws ASIdiSPIMException {
      getAcquisitionPanel().setAcquisitionNamePrefix(acqName);
   }
   
   @Override
   public String getSavingDirectoryRoot() throws ASIdiSPIMException {
      return getAcquisitionPanel().getSavingDirectoryRoot();
   }
   
   @Override
   public void setSavingDirectoryRoot(String directory) throws ASIdiSPIMException {
      getAcquisitionPanel().setSavingDirectoryRoot(directory);
   }
   
   @Override
   public String getSavingNamePrefix() throws ASIdiSPIMException {
      return getAcquisitionPanel().getSavingNamePrefix();
   }

   @Override
   public void setSavingNamePrefix(String acqPrefix) throws ASIdiSPIMException {
      getAcquisitionPanel().setSavingNamePrefix(acqPrefix);
   }
   
   @Override
   public boolean getSavingSeparateFile() throws ASIdiSPIMException {
      return getAcquisitionPanel().getSavingSeparateFile();
   }

   @Override
   public void setSavingSeparateFile(boolean separate) throws ASIdiSPIMException {
      getAcquisitionPanel().setSavingSeparateFile(separate);
   }
   
   @Override
   public boolean getSavingSaveWhileAcquiring() throws ASIdiSPIMException {
      return getAcquisitionPanel().getSavingSaveWhileAcquiring();
   }

   @Override
   public void setSavingSaveWhileAcquiring(boolean save) throws ASIdiSPIMException {
      getAcquisitionPanel().setSavingSaveWhileAcquiring(save);
   }
   
   @Override
   public Keys getAcquisitionMode() throws ASIdiSPIMException {
      return getAcquisitionPanel().getAcquisitionMode();
   }

   @Override
   public void setAcquisitionMode(org.micromanager.asidispim.Data.AcquisitionModes.Keys mode) throws ASIdiSPIMException {
      getAcquisitionPanel().setAcquisitionMode(mode);
   }
   
   @Override
   public boolean getTimepointsEnabled() throws ASIdiSPIMException {
      return getAcquisitionPanel().getTimepointsEnabled();
   }

   @Override
   public void setTimepointsEnabled(boolean enabled) throws ASIdiSPIMException {
      getAcquisitionPanel().setTimepointsEnabled(enabled);
   }
   
   @Override
   public int getTimepointsNumber() throws ASIdiSPIMException {
      return getAcquisitionPanel().getNumberOfTimepoints();
   }

   @Override
   public void setTimepointsNumber(int numTimepoints) throws ASIdiSPIMException {
      getAcquisitionPanel().setNumberOfTimepoints(numTimepoints);
   }
   
   @Override
   public double getTimepointInterval() throws ASIdiSPIMException {
      return getAcquisitionPanel().getTimepointInterval();
   }

   @Override
   public void setTimepointInterval(double intervalTimepoints) throws ASIdiSPIMException {
      // range checking done later
      getAcquisitionPanel().setTimepointInterval(intervalTimepoints);
   }
   
   @Override
   public boolean getMultiplePositionsEnabled() throws ASIdiSPIMException {
      return getAcquisitionPanel().getMultiplePositionsEnabled();
   }

   @Override
   public void setMultiplePositionsEnabled(boolean enabled) throws ASIdiSPIMException {
      getAcquisitionPanel().setMultiplePositionsEnabled(enabled);
   }
   
   @Override
   public double getMultiplePositionsDelay() throws ASIdiSPIMException {
      return getAcquisitionPanel().getMultiplePositionsPostMoveDelay();
   }

   @Override
   public void setMultiplePositionsDelay(double delayMs) throws ASIdiSPIMException {
      // range checking done later
      getAcquisitionPanel().setMultiplePositionsDelay(delayMs);
   }
   
   @Override
   public PositionList getPositionList() throws ASIdiSPIMException {
      try {
         return getGui().getPositionList();
      } catch (Exception ex) {
         throw new ASIdiSPIMException(ex);
      }
   }
   
   @Override
   public boolean getChannelsEnabled() throws ASIdiSPIMException {
      return getAcquisitionPanel().getChannelsEnabled();
   }

   @Override
   public void setChannelsEnabled(boolean enabled) throws ASIdiSPIMException {
      getAcquisitionPanel().setChannelsEnabled(enabled);
      
   }

   @Override
   public String[] getAvailableChannelGroups() throws ASIdiSPIMException {
      return getAcquisitionPanel().getAvailableChannelGroups();
   }
   
   @Override
   public String getChannelGroup() throws ASIdiSPIMException {
      return getAcquisitionPanel().getChannelGroup();
   }

   @Override
   public void setChannelGroup(String channelGroup) throws ASIdiSPIMException {
      String[] availableGroups = getAvailableChannelGroups();
      for (String gr : availableGroups) {
         if (gr.equals(channelGroup)) {
            getAcquisitionPanel().setChannelGroup(channelGroup);
            return;
         }
      }
      throw new ASIdiSPIMException("specified channel group not available");
   }

   @Override
   public String[] getAvailableChannels() throws ASIdiSPIMException {
      return getAcquisitionPanel().getAvailableChannels();
   }
   
   @Override
   public boolean getChannelEnabled(String channel) throws ASIdiSPIMException {
      if (!getChannelsEnabled()) {
         return false;
      }
      String[] availableChannels = getAvailableChannels();
      for (String ch : availableChannels) {
         if (ch.equals(channel)) {
            return getAcquisitionPanel().getChannelEnabled(channel);
         }
      }
      throw new ASIdiSPIMException("specified channel not available");
   }

   @Override
   public void setChannelEnabled(String channel, boolean enabled) throws ASIdiSPIMException {
      String[] availableChannels = getAvailableChannels();
      for (String ch : availableChannels) {
         if (ch.equals(channel)) {
            getAcquisitionPanel().setChannelEnabled(channel, enabled);
            return;
         }
      }
      throw new ASIdiSPIMException("specified channel not available");
   }
   
   @Override
   public org.micromanager.asidispim.Data.MultichannelModes.Keys getChannelChangeMode() throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setChannelChangeMode(org.micromanager.asidispim.Data.MultichannelModes.Keys mode) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public int getVolumeNumberOfSides() throws ASIdiSPIMException {
      return getAcquisitionPanel().getNumSides();
   }

   @Override
   public void setVolumeNumberOfSides(int numSides) throws ASIdiSPIMException {
      if (numSides < 1 || numSides > 2) {
         throw new ASIdiSPIMException("number of sides can only be 1 or 2");
      }
      getAcquisitionPanel().setVolumeNumberOfSides(numSides);
   }
   
   @Override
   public Devices.Sides getVolumeFirstSide() throws ASIdiSPIMException {
      if (getAcquisitionPanel().isFirstSideA()) {
         return Devices.Sides.A;
      } else {
         return Devices.Sides.B;
      }
   }

   @Override
   public void setVolumeFirstSide(Devices.Sides firstSide) throws ASIdiSPIMException {
      if (firstSide == Devices.Sides.A) {
         getAcquisitionPanel().setFirstSideIsA(true);
      } else if (firstSide == Devices.Sides.B) {
         getAcquisitionPanel().setFirstSideIsA(false);
      } else {
         throw new ASIdiSPIMException("invalid value of firstSide");
      }
   }
   
   @Override
   public double getVolumeDelayBeforeSide() throws ASIdiSPIMException {
      return getAcquisitionPanel().getVolumeDelayBeforeSide();
   }

   @Override
   public void setVolumeDelayBeforeSide(double delayMs) throws ASIdiSPIMException {
      // range checking done later
      getAcquisitionPanel().setVolumeDelayBeforeSide(delayMs);
   }
   
   @Override
   public int getVolumeSlicesPerVolume() throws ASIdiSPIMException {
      return getAcquisitionPanel().getVolumeSlicesPerVolume();
   }

   @Override
   public void setVolumeSlicesPerVolume(int slices) throws ASIdiSPIMException {
      // range checking done later
      getAcquisitionPanel().setVolumeSlicesPerVolume(slices);
   }
   
   @Override
   public double getVolumeSliceStepSize() throws ASIdiSPIMException {
      return getAcquisitionPanel().getVolumeSliceStepSize();
   }

   @Override
   public void setVolumeSliceStepSize(double stepSizeUm) throws ASIdiSPIMException {
      // range checking done later
      getAcquisitionPanel().setVolumeSliceStepSize(stepSizeUm);
   }
   
   @Override
   public boolean getVolumeMinimizeSlicePeriod() throws ASIdiSPIMException {
      return getAcquisitionPanel().getVolumeMinimizeSlicePeriod();
   }

   @Override
   public void setVolumeMinimizeSlicePeriod(boolean minimize) throws ASIdiSPIMException {
      getAcquisitionPanel().setVolumeMinimizeSlicePeriod(minimize);
   }
   
   @Override
   public double getVolumeSlicePeriod() throws ASIdiSPIMException {
      return getAcquisitionPanel().getVolumeSlicePeriod();
   }

   @Override
   public void setVolumeSlicePeriod(double periodMs) throws ASIdiSPIMException {
      // range checking done later
      getAcquisitionPanel().setVolumeSlicePeriod(periodMs);
   }

   @Override
   public double getVolumeSampleExposure() throws ASIdiSPIMException {
      return getAcquisitionPanel().getVolumeSampleExposure();
   }

   @Override
   public void setVolumeSampleExposure(double exposureMs) throws ASIdiSPIMException {
      // range checking done later
      getAcquisitionPanel().setVolumeSampleExposure(exposureMs);
   }
   
   @Override
   public boolean getAutofocusDuringAcquisition() throws ASIdiSPIMException {
      return getAcquisitionPanel().getAutofocusDuringAcquisition();
   }

   @Override
   public void setAutofocusDuringAcquisition(boolean enable) throws ASIdiSPIMException {
      getAcquisitionPanel().setAutofocusDuringAcquisition(enable);
   }
   
   @Override
   public double getSideImagingCenter(Sides side) throws ASIdiSPIMException {
      return getSetupPanel(side).getImagingCenter();
   }

   @Override
   public void setSideImagingCenter(Sides side, double center) throws ASIdiSPIMException {
      getSetupPanel(side).setImagingCenter(center);
   }
   
   @Override
   public double getSideSlicePosition(Sides side) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideSlicePosition(Sides side, double position) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public double getSideImagingPiezoPosition(Sides side) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideImagingPiezoPosition(Sides side, double position) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public double getSideIlluminationPiezoPosition(Sides side) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideIlluminationPiezoPosition(Sides side, double position) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideIlluminationPiezoHome(Sides side) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public double getSideSheetWidth(Sides side) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideSheetWidth(Sides side, double width) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }
   
   @Override
   public double getSideSheetOffset(Sides side) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideSheetOffset(Sides side, double width) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public double getSideCalibrationSlope(Sides side) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideCalibrationSlope(Sides side, double slope) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();      
   }
   
   @Override
   public double getSideCalibrationOffset(Sides side) throws ASIdiSPIMException {
      return getSetupPanel(side).getSideCalibrationOffset();
   }

   @Override
   public void setSideCalibrationOffset(Sides side, double offset) throws ASIdiSPIMException {
      getSetupPanel(side).setSideCalibrationOffset(offset);
   }
   
   @Override
   public void updateSideCalibrationOffset(Sides side) throws ASIdiSPIMException {
      getSetupPanel(side).updateCalibrationOffset();
   }
   
   @Override
   public void runAutofocusSide(Sides side) throws ASIdiSPIMException {
      getSetupPanel(side).runAutofocus();
   }
   
   @Override
   public int getAutofocusNumImages() throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setAutofocusNumImages(int numImages) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();      
   }

   @Override
   public double getAutofocusStepSize() throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setAutofocusStepSize(double stepSizeUm) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public Modes getAutofocusMode() throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setAutofocusMode(Modes mode) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }
   
   @Override
   public boolean getAutofocusBeforeAcquisition() throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setAutofocusBeforeAcquisition(boolean enable) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }
   
   @Override
   public int getAutofocusTimepointInterval() throws ASIdiSPIMException {
      return getAutofocusPanel().getAutofocusTimepointInterval();
   }

   @Override
   public void setAutofocusTimepointInterval(int numTimepoints) throws ASIdiSPIMException {
      getAutofocusPanel().setAutofocusTimepointInterval(numTimepoints);
   }
   
   @Override
   public String getAutofocusChannel() throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setAutofocusChannel(String channel) throws ASIdiSPIMException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }
   
   
   
   
   //** Private methods.  Only for internal use **//

   private MMStudio getGui() throws ASIdiSPIMException {
      MMStudio studio = MMStudio.getInstance();
      if (studio == null) {
         throw new ASIdiSPIMException ("MM Studio is not open"); 
      }
      return studio;
  }
   
   
   private CMMCore getCore() throws ASIdiSPIMException {
       CMMCore core = getGui().getCore();
       if (core == null) {
          throw new ASIdiSPIMException("Core is not open");
       }
       return core;
   }
   
   private ASIdiSPIMFrame getFrame() throws ASIdiSPIMException {
      ASIdiSPIMFrame frame = ASIdiSPIM.getFrame();
      if (frame == null) {
         throw new ASIdiSPIMException ("Plugin is not open");
      }
      return frame;
   }
   
   private AcquisitionPanel getAcquisitionPanel() throws ASIdiSPIMException {
      AcquisitionPanel acquisitionPanel = getFrame().getAcquisitionPanel();
      if (acquisitionPanel == null) {
         throw new ASIdiSPIMException ("AcquisitionPanel is not open");
      }
      return acquisitionPanel;
   }
   
   private SetupPanel getSetupPanel(Devices.Sides side) throws ASIdiSPIMException {
      SetupPanel setupPanel = getFrame().getSetupPanel(side);
      if (setupPanel == null) {
         throw new ASIdiSPIMException ("SetupPanel is not open");
      }
      return setupPanel;
   }
   
   private NavigationPanel getNavigationPanel() throws ASIdiSPIMException {
      NavigationPanel navigationPanel = getFrame().getNavigationPanel();
      if (navigationPanel == null) {
         throw new ASIdiSPIMException ("NavigationPanel is not open");
      }
      return navigationPanel;
   }
   
   private AutofocusPanel getAutofocusPanel() throws ASIdiSPIMException {
      AutofocusPanel autofocusPanel = getFrame().getAutofocusPanel();
      if (autofocusPanel == null) {
         throw new ASIdiSPIMException ("AutofocusPanel is not open");
      }
      return autofocusPanel;
   }




   





   @Override
   public void setXYPosition(double x, double y) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public Double getXYPosition() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public void setLowerZPosition(double z) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public double getLowerZPosition() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void setSPIMHeadPosition(double z) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public double getSPIMHeadPosition() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void raiseSPIMHead() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public void setSPIMHeadRaisedPosition(double raised)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public double getSPIMHeadRaisedPosition() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void lowerSPIMHead() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public void setSPIMHeadLoweredPosition(double raised)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public double getSPIMHeadLoweredPosition() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public AcquisitionSettings getAcquisitionSettings()
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public double getEstimatedSliceDuration() {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public double getEstimatedVolumeDuration() {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public double getEstimatedAcquisitionDuration() {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void refreshEstimatedTiming() {
      // TODO Auto-generated method stub
      
   }



}

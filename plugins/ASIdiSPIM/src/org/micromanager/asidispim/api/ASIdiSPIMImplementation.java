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

import mmcorej.CMMCore;

import java.rmi.RemoteException;

import org.micromanager.MMStudio;
import org.micromanager.api.PositionList;
import org.micromanager.asidispim.ASIdiSPIM;
import org.micromanager.asidispim.ASIdiSPIMFrame;
import org.micromanager.asidispim.AcquisitionPanel;
import org.micromanager.asidispim.AutofocusPanel;
import org.micromanager.asidispim.AutofocusPanel.Modes;
import org.micromanager.asidispim.DataAnalysisPanel;
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
 * diSPIM.runAcquisition(); // or other API methods in ASIdiSPIMInterface
 * 
 * 
 * Another approach is to use Java RMI with the name "ASIdiSPIM_API".
 * e.g.:
 * 
 *  public class AsiRmiClientStub {
 *  public final static String rmiName = "ASIdiSPIM_API";
 *  private AsiRmiClientStub() {}
 *  public static void main(String[] args) {
 *     String host = (args.length < 1) ? null : args[0];
 *     {
 *        Registry registry;
 *        try {
 *           registry = LocateRegistry.getRegistry(host);
 *           ASIdiSPIMInterface stub = (ASIdiSPIMInterface) registry.lookup(rmiName);
 *           stub.runAcquisition(); // or other API methods in ASIdiSPIMInterface
 *        } catch (Exception e) {
 *           e.printStackTrace();
 *        }
 *     } 
 *  }
 * }
 * 
 * 
 * @author nico
 * @author Jon
 */
public class ASIdiSPIMImplementation implements ASIdiSPIMInterface {

   @Override
   public void runAcquisition() throws ASIdiSPIMException, RemoteException {
      if (isAcquisitionRequested()) {
         throw new ASIdiSPIMException("another acquisition ongoing");
      }
      getAcquisitionPanel().runAcquisition();
   }

   @Override
   public ij.ImagePlus runAcquisitionBlocking() throws ASIdiSPIMException, RemoteException {
      if (isAcquisitionRequested()) {
         throw new ASIdiSPIMException("another acquisition ongoing");
      }
      getAcquisitionPanel().runAcquisition();
      try {
         Thread.sleep(100);
         while (isAcquisitionRequested()) {
            Thread.sleep(10);
         }
         return getAcquisitionPanel().getLastAcquisitionImagePlus();
      } catch (InterruptedException e) {
         throw new ASIdiSPIMException(e);
      }
   }
   
   @Override
   public ij.ImagePlus runAcquisitionBlocking(double x, double y, double f) throws ASIdiSPIMException, RemoteException {
      setXYPosition(x, y);
      setSPIMHeadPosition(f);
      return runAcquisitionBlocking();
   }
   
   @Override
   public void stopAcquisition() throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().stopAcquisition();
   }
   
   @Override
   public boolean isAcquisitionRunning() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().isAcquisitionRunning();
   }
   
   @Override
   public boolean isAcquisitionRequested() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().isAcquisitionRequested();
   }
   
   @Override
   public ij.ImagePlus getLastAcquisitionImagePlus() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getLastAcquisitionImagePlus();
   }

   
   @Override
   public String getLastAcquisitionPath() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getLastAcquisitionPath();
   }
   
   @Override
   public String getLastAcquisitionName() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getLastAcquisitionName();
   }

   @Override
   public void closeLastAcquisitionWindow() throws ASIdiSPIMException, RemoteException {
      closeAcquisitionWindow(getLastAcquisitionName());
   }

   @Override
   public void closeAcquisitionWindow(String acquisitionName) throws ASIdiSPIMException, RemoteException {
      try {
         getGui().closeAcquisitionWindow(acquisitionName);
      } catch (MMScriptException e) {
         throw new ASIdiSPIMException(e);
      }
   }
   
   @Override
   public void setAcquisitionNamePrefix(String acqName) throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().setAcquisitionNamePrefix(acqName);
   }
   
   @Override
   public String getSavingDirectoryRoot() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getSavingDirectoryRoot();
   }
   
   @Override
   public void setSavingDirectoryRoot(String directory) throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().setSavingDirectoryRoot(directory);
   }
   
   @Override
   public String getSavingNamePrefix() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getSavingNamePrefix();
   }

   @Override
   public void setSavingNamePrefix(String acqPrefix) throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().setSavingNamePrefix(acqPrefix);
   }
   
   @Override
   public boolean getSavingSeparateFile() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getSavingSeparateFile();
   }

   @Override
   public void setSavingSeparateFile(boolean separate) throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().setSavingSeparateFile(separate);
   }
   
   @Override
   public boolean getSavingSaveWhileAcquiring() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getSavingSaveWhileAcquiring();
   }

   @Override
   public void setSavingSaveWhileAcquiring(boolean save) throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().setSavingSaveWhileAcquiring(save);
   }
   
   @Override
   public Keys getAcquisitionMode() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getAcquisitionMode();
   }

   @Override
   public void setAcquisitionMode(org.micromanager.asidispim.Data.AcquisitionModes.Keys mode) throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().setAcquisitionMode(mode);
   }
   
   @Override
   public boolean getTimepointsEnabled() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getTimepointsEnabled();
   }

   @Override
   public void setTimepointsEnabled(boolean enabled) throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().setTimepointsEnabled(enabled);
   }
   
   @Override
   public int getTimepointsNumber() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getNumberOfTimepoints();
   }

   @Override
   public void setTimepointsNumber(int numTimepoints) throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().setNumberOfTimepoints(numTimepoints);
   }
   
   @Override
   public double getTimepointInterval() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getTimepointInterval();
   }

   @Override
   public void setTimepointInterval(double intervalTimepoints) throws ASIdiSPIMException, RemoteException {
      // range checking done later
      getAcquisitionPanel().setTimepointInterval(intervalTimepoints);
   }
   
   @Override
   public boolean getMultiplePositionsEnabled() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getMultiplePositionsEnabled();
   }

   @Override
   public void setMultiplePositionsEnabled(boolean enabled) throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().setMultiplePositionsEnabled(enabled);
   }
   
   @Override
   public double getMultiplePositionsDelay() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getMultiplePositionsPostMoveDelay();
   }

   @Override
   public void setMultiplePositionsDelay(double delayMs) throws ASIdiSPIMException, RemoteException {
      // range checking done later
      getAcquisitionPanel().setMultiplePositionsDelay(delayMs);
   }
   
   @Override
   public PositionList getPositionList() throws ASIdiSPIMException, RemoteException {
      try {
         return getGui().getPositionList();
      } catch (Exception ex) {
         throw new ASIdiSPIMException(ex);
      }
   }
   
   @Override
   public boolean getChannelsEnabled() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getChannelsEnabled();
   }

   @Override
   public void setChannelsEnabled(boolean enabled) throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().setChannelsEnabled(enabled);
      
   }

   @Override
   public String[] getAvailableChannelGroups() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getAvailableChannelGroups();
   }
   
   @Override
   public String getChannelGroup() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getChannelGroup();
   }

   @Override
   public void setChannelGroup(String channelGroup) throws ASIdiSPIMException, RemoteException {
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
   public String[] getAvailableChannels() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getAvailableChannels();
   }
   
   @Override
   public boolean getChannelEnabled(String channel) throws ASIdiSPIMException, RemoteException {
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
   public void setChannelEnabled(String channel, boolean enabled) throws ASIdiSPIMException, RemoteException {
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
   public org.micromanager.asidispim.Data.MultichannelModes.Keys getChannelChangeMode() throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setChannelChangeMode(org.micromanager.asidispim.Data.MultichannelModes.Keys mode) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public int getVolumeNumberOfSides() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getNumSides();
   }

   @Override
   public void setVolumeNumberOfSides(int numSides) throws ASIdiSPIMException, RemoteException {
      if (numSides < 1 || numSides > 2) {
         throw new ASIdiSPIMException("number of sides can only be 1 or 2");
      }
      getAcquisitionPanel().setVolumeNumberOfSides(numSides);
   }
   
   @Override
   public Devices.Sides getVolumeFirstSide() throws ASIdiSPIMException, RemoteException {
      if (getAcquisitionPanel().isFirstSideA()) {
         return Devices.Sides.A;
      } else {
         return Devices.Sides.B;
      }
   }

   @Override
   public void setVolumeFirstSide(Devices.Sides firstSide) throws ASIdiSPIMException, RemoteException {
      if (firstSide == Devices.Sides.A) {
         getAcquisitionPanel().setFirstSideIsA(true);
      } else if (firstSide == Devices.Sides.B) {
         getAcquisitionPanel().setFirstSideIsA(false);
      } else {
         throw new ASIdiSPIMException("invalid value of firstSide");
      }
   }
   
   @Override
   public double getVolumeDelayBeforeSide() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getVolumeDelayBeforeSide();
   }

   @Override
   public void setVolumeDelayBeforeSide(double delayMs) throws ASIdiSPIMException, RemoteException {
      // range checking done later
      getAcquisitionPanel().setVolumeDelayBeforeSide(delayMs);
   }
   
   @Override
   public int getVolumeSlicesPerVolume() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getVolumeSlicesPerVolume();
   }

   @Override
   public void setVolumeSlicesPerVolume(int slices) throws ASIdiSPIMException, RemoteException {
      // range checking done later
      getAcquisitionPanel().setVolumeSlicesPerVolume(slices);
   }
   
   @Override
   public double getVolumeSliceStepSize() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getVolumeSliceStepSize();
   }

   @Override
   public void setVolumeSliceStepSize(double stepSizeUm) throws ASIdiSPIMException, RemoteException {
      // range checking done later
      getAcquisitionPanel().setVolumeSliceStepSize(stepSizeUm);
   }
   
   @Override
   public boolean getVolumeMinimizeSlicePeriod() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getVolumeMinimizeSlicePeriod();
   }

   @Override
   public void setVolumeMinimizeSlicePeriod(boolean minimize) throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().setVolumeMinimizeSlicePeriod(minimize);
   }
   
   @Override
   public double getVolumeSlicePeriod() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getVolumeSlicePeriod();
   }

   @Override
   public void setVolumeSlicePeriod(double periodMs) throws ASIdiSPIMException, RemoteException {
      // range checking done later
      getAcquisitionPanel().setVolumeSlicePeriod(periodMs);
   }

   @Override
   public double getVolumeSampleExposure() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getVolumeSampleExposure();
   }

   @Override
   public void setVolumeSampleExposure(double exposureMs) throws ASIdiSPIMException, RemoteException {
      // range checking done later
      getAcquisitionPanel().setVolumeSampleExposure(exposureMs);
   }
   
   @Override
   public boolean getAutofocusDuringAcquisition() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getAutofocusDuringAcquisition();
   }

   @Override
   public void setAutofocusDuringAcquisition(boolean enable) throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().setAutofocusDuringAcquisition(enable);
   }
   
   @Override
   public double getSideImagingCenter(Sides side) throws ASIdiSPIMException, RemoteException {
      return getSetupPanel(side).getImagingCenter();
   }

   @Override
   public void setSideImagingCenter(Sides side, double center) throws ASIdiSPIMException, RemoteException {
      getSetupPanel(side).setImagingCenter(center);
   }
   
   @Override
   public double getSideSlicePosition(Sides side) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideSlicePosition(Sides side, double position) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public double getSideImagingPiezoPosition(Sides side) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideImagingPiezoPosition(Sides side, double position) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public double getSideIlluminationPiezoPosition(Sides side) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideIlluminationPiezoPosition(Sides side, double position) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideIlluminationPiezoHome(Sides side) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public double getSideSheetWidth(Sides side) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideSheetWidth(Sides side, double width) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }
   
   @Override
   public double getSideSheetOffset(Sides side) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideSheetOffset(Sides side, double width) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public double getSideCalibrationSlope(Sides side) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setSideCalibrationSlope(Sides side, double slope) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();      
   }
   
   @Override
   public double getSideCalibrationOffset(Sides side) throws ASIdiSPIMException, RemoteException {
      return getSetupPanel(side).getSideCalibrationOffset();
   }

   @Override
   public void setSideCalibrationOffset(Sides side, double offset) throws ASIdiSPIMException, RemoteException {
      getSetupPanel(side).setSideCalibrationOffset(offset);
   }
   
   @Override
   public void updateSideCalibrationOffset(Sides side) throws ASIdiSPIMException, RemoteException {
      getSetupPanel(side).updateCalibrationOffset();
   }
   
   @Override
   public double getSideLightSheetSlope(Sides side) throws ASIdiSPIMException, RemoteException {
      return getSetupPanel(side).getSideLightSheetSlope();
   }
   
   @Override
   public void setSideLightSheetSlope(Sides side, double slope) throws ASIdiSPIMException, RemoteException {
      getSetupPanel(side).setSideLightSheetSlope(slope);
   }
   
   @Override
   public double getSideLightSheetOffset(Sides side) throws ASIdiSPIMException, RemoteException {
      return getSetupPanel(side).getSideLightSheetOffset();
   }
   
   @Override
   public void setSideLightSheetOffset(Sides side, double offset) throws ASIdiSPIMException, RemoteException {
      getSetupPanel(side).setSideLightSheetOffset(offset);
   }
      
   @Override
   public void runAutofocusSide(Sides side) throws ASIdiSPIMException, RemoteException {
      getSetupPanel(side).runAutofocus();
   }
   
   @Override
   public int getAutofocusNumImages() throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setAutofocusNumImages(int numImages) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();      
   }

   @Override
   public double getAutofocusStepSize() throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setAutofocusStepSize(double stepSizeUm) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public Modes getAutofocusMode() throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setAutofocusMode(Modes mode) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }
   
   @Override
   public boolean getAutofocusBeforeAcquisition() throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setAutofocusBeforeAcquisition(boolean enable) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }
   
   @Override
   public int getAutofocusTimepointInterval() throws ASIdiSPIMException, RemoteException {
      return getAutofocusPanel().getAutofocusTimepointInterval();
   }

   @Override
   public void setAutofocusTimepointInterval(int numTimepoints) throws ASIdiSPIMException, RemoteException {
      getAutofocusPanel().setAutofocusTimepointInterval(numTimepoints);
   }
   
   @Override
   public String getAutofocusChannel() throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }

   @Override
   public void setAutofocusChannel(String channel) throws ASIdiSPIMException, RemoteException {
      // @deprecated out of laziness, can add if needed
      throw new UnsupportedOperationException();
   }
   
   @Override
   public void setXYPosition(double x, double y) throws ASIdiSPIMException, RemoteException {
      try {
         getCore().setXYPosition(getDevices().getMMDevice(Devices.Keys.XYSTAGE), x, y);
      } catch (Exception e) {
         throw new ASIdiSPIMException(e);
      }
   }

   @Override
   public java.awt.geom.Point2D.Double getXYPosition() throws ASIdiSPIMException, RemoteException {
      try {
         return getCore().getXYStagePosition(getDevices().getMMDevice(Devices.Keys.XYSTAGE));
      } catch (Exception e) {
         throw new ASIdiSPIMException(e);
      }
   }

   @Override
   public void setLowerZPosition(double z) throws ASIdiSPIMException, RemoteException {
      try {
         getCore().setPosition(getDevices().getMMDevice(Devices.Keys.LOWERZDRIVE), z);
      } catch (Exception e) {
         throw new ASIdiSPIMException(e);
      }
   }

   @Override
   public double getLowerZPosition() throws ASIdiSPIMException, RemoteException {
      try {
         return getCore().getPosition(getDevices().getMMDevice(Devices.Keys.LOWERZDRIVE));
      } catch (Exception e) {
         throw new ASIdiSPIMException(e);
      }
   }

   @Override
   public void setSPIMHeadPosition(double z) throws ASIdiSPIMException, RemoteException {
      try {
         getCore().setPosition(getDevices().getMMDevice(Devices.Keys.UPPERZDRIVE), z);
      } catch (Exception e) {
         throw new ASIdiSPIMException(e);
      }
   }

   @Override
   public double getSPIMHeadPosition() throws ASIdiSPIMException, RemoteException {
      try {
         return getCore().getPosition(getDevices().getMMDevice(Devices.Keys.UPPERZDRIVE));
      } catch (Exception e) {
         throw new ASIdiSPIMException(e);
      }
   }

   @Override
   public void raiseSPIMHead() throws ASIdiSPIMException, RemoteException {
      getNavigationPanel().raiseSPIMHead();
   }

   @Override
   public void setSPIMHeadRaisedPosition(double raised) throws ASIdiSPIMException, RemoteException {
      getNavigationPanel().setSPIMHeadRaisedPosition(raised);
   }

   @Override
   public double getSPIMHeadRaisedPosition() throws ASIdiSPIMException, RemoteException {
      return getNavigationPanel().getSPIMHeadRaisedPosition();
   }

   @Override
   public void lowerSPIMHead() throws ASIdiSPIMException, RemoteException {
      getNavigationPanel().lowerSPIMHead();
   }

   @Override
   public void setSPIMHeadLoweredPosition(double lowered) throws ASIdiSPIMException, RemoteException {
      getNavigationPanel().setSPIMHeadLoweredPosition(lowered);
   }

   @Override
   public double getSPIMHeadLoweredPosition() throws ASIdiSPIMException, RemoteException {
      return getNavigationPanel().getSPIMHeadLoweredPosition();
   }
   
   @Override
   public void haltAllMotion() throws ASIdiSPIMException, RemoteException {
      getNavigationPanel().haltAllMotion();
   }
   
   @Override
   public AcquisitionSettings getAcquisitionSettings() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getCurrentAcquisitionSettings();
   }

   @Override
   public double getEstimatedSliceDuration() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getEstimatedSliceDuration();
   }

   @Override
   public double getEstimatedVolumeDuration() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getEstimatedVolumeDuration();
   }

   @Override
   public double getEstimatedAcquisitionDuration() throws ASIdiSPIMException, RemoteException {
      return getAcquisitionPanel().getEstimatedAcquisitionDuration();
   }

   @Override
   public void refreshEstimatedTiming() throws ASIdiSPIMException, RemoteException {
      getAcquisitionPanel().updateDurationLabels();
   }
   
   @Override
   public void setExportBaseName(String baseName) throws ASIdiSPIMException, RemoteException {
      getDataAnalysisPanel().setExportBaseName(baseName);
   }

   @Override
   public void doExportData() throws ASIdiSPIMException, RemoteException {
      getDataAnalysisPanel().runExport();
   }
   
   
   //** Private methods.  Only for internal use **//

   private MMStudio getGui() throws ASIdiSPIMException, RemoteException {
      MMStudio studio = MMStudio.getInstance();
      if (studio == null) {
         throw new ASIdiSPIMException ("MM Studio is not open"); 
      }
      return studio;
  }
   
   
   private CMMCore getCore() throws ASIdiSPIMException, RemoteException {
       CMMCore core = getGui().getCore();
       if (core == null) {
          throw new ASIdiSPIMException("Core is not open");
       }
       return core;
   }
   
   private ASIdiSPIMFrame getFrame() throws ASIdiSPIMException, RemoteException {
      ASIdiSPIMFrame frame = ASIdiSPIM.getFrame();
      if (frame == null) {
         throw new ASIdiSPIMException ("Plugin is not open");
      }
      return frame;
   }
   
   private AcquisitionPanel getAcquisitionPanel() throws ASIdiSPIMException, RemoteException {
      AcquisitionPanel acquisitionPanel = getFrame().getAcquisitionPanel();
      if (acquisitionPanel == null) {
         throw new ASIdiSPIMException ("AcquisitionPanel is not open");
      }
      return acquisitionPanel;
   }
   
   private SetupPanel getSetupPanel(Devices.Sides side) throws ASIdiSPIMException, RemoteException {
      SetupPanel setupPanel = getFrame().getSetupPanel(side);
      if (setupPanel == null) {
         throw new ASIdiSPIMException ("SetupPanel is not open");
      }
      return setupPanel;
   }
   
   private NavigationPanel getNavigationPanel() throws ASIdiSPIMException, RemoteException {
      NavigationPanel navigationPanel = getFrame().getNavigationPanel();
      if (navigationPanel == null) {
         throw new ASIdiSPIMException ("NavigationPanel is not open");
      }
      return navigationPanel;
   }
   
   private AutofocusPanel getAutofocusPanel() throws ASIdiSPIMException, RemoteException {
      AutofocusPanel autofocusPanel = getFrame().getAutofocusPanel();
      if (autofocusPanel == null) {
         throw new ASIdiSPIMException ("AutofocusPanel is not open");
      }
      return autofocusPanel;
   }
   
   private DataAnalysisPanel getDataAnalysisPanel() throws ASIdiSPIMException, RemoteException {
      DataAnalysisPanel dataAnalysisPanel = getFrame().getDataAnalysisPanel();
      if (dataAnalysisPanel == null) {
         throw new ASIdiSPIMException ("DataAnalysisPanel is not open");
      }
      return dataAnalysisPanel;
   }
   
   private Devices getDevices() throws ASIdiSPIMException, RemoteException {
      Devices devices = getFrame().getDevices();
      if (devices == null) {
         throw new ASIdiSPIMException ("Devices object does not exist");
      }
      return devices;
   }

}

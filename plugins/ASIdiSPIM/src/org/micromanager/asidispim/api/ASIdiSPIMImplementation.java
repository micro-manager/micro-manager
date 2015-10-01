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
import mmcorej.MMCoreJ;

import org.micromanager.MMStudio;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.asidispim.ASIdiSPIM;
import org.micromanager.asidispim.ASIdiSPIMFrame;
import org.micromanager.asidispim.AcquisitionPanel;
import org.micromanager.asidispim.AutofocusPanel;
import org.micromanager.asidispim.AutofocusPanel.Modes;
import org.micromanager.asidispim.NavigationPanel;
import org.micromanager.asidispim.Data.AcquisitionModes.Keys;
import org.micromanager.asidispim.Data.AcquisitionSettings;
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
   public double getMultiplePositionsPostMoveDelay() throws ASIdiSPIMException {
      return getAcquisitionPanel().getMultiplePositionsPostMoveDelay();
   }

   @Override
   public void setMultiplePositionsPostMoveDelay(double delayMs) throws ASIdiSPIMException {
      getAcquisitionPanel().setMultiplePositionsPostMoveDelay(delayMs);
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
   public PositionList getPositionList() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public int getNumberOfPositions() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public MultiStagePosition getPositionFromIndex(int idx)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public void moveToPositionFromIndex(int idx) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public boolean getChannelsEnabled() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return false;
   }

   @Override
   public void setChannelsEnabled(boolean enabled) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public String getChannelGroup() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public void setChannelGroup(String channelGroup) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public boolean getChannelPresetEnabled(String channelPreset)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return false;
   }

   @Override
   public void setChannelPresetEnabled(String channelPreset, boolean enabled)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public org.micromanager.asidispim.Data.MultichannelModes.Keys getChannelChangeMode()
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public void setChannelChangeMode(
         org.micromanager.asidispim.Data.MultichannelModes.Keys mode)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public int getVolumeNumberOfSides() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void setVolumeNumberOfSides(int numSides) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public String getVolumeFirstSide() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public void setVolumeFirstSide(String firstSide) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public double getVolumeDelayBeforeSide() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void setVolumeDelayBeforeSide(double delayMs)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public int getVolumeSlicesPerVolume() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void setVolumeSlicesPerVolume(int slices) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public double getVolumeSliceStepSize() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void setVolumeSliceStepSize(double stepSizeUm)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public boolean getVolumeMinimizeSlicePeriod() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return false;
   }

   @Override
   public void setVolumeMinimizeSlicePeriod(boolean minimize)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public double getVolumeSlicePeriod() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void setVolumeSlicePeriod(double periodMs) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public double getVolumeSampleExposure() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void setVolumeSampleExposure(double exposureMs)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public boolean getAutofocusDuringAcquisition() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return false;
   }

   @Override
   public void setAutofocusDuringAcquisition(boolean enable)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public int getAutofocusNumImages() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void setAutofocusNumImages(int numImages) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public double getAutofocusStepSize() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void setAutofocusStepSize(double stepSizeUm)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public Modes getAutofocusMode() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public void setAutofocusMode(Modes mode) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public boolean getAutofocusBeforeAcquisition() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return false;
   }

   @Override
   public void setAutofocusBeforeAcquisition(boolean enable)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public int getAutofocusInterval() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public void setAutofocusInterval(int numTimepoints)
         throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
   }

   @Override
   public String getAutofocusChannel() throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public void setAutofocusChannel(String channel) throws ASIdiSPIMException {
      // TODO Auto-generated method stub
      
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

}

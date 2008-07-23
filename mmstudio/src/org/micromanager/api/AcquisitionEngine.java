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

import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.metadata.WellAcquisitionData;
import org.micromanager.navigation.PositionList;
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
   public void setCore(CMMCore core_);
   public void setPositionList(PositionList posList);
   public void setParentGUI(DeviceControlGUI parent);
   public void setZStageDevice(String stageLabel_);
   public void setUpdateLiveWindow(boolean b);
   public String installAutofocusPlugin(String className);
   
   // run-time control
   public void acquire() throws MMException, MMAcqDataException;
   public void acquireWellScan(WellAcquisitionData wad) throws MMException, MMAcqDataException;
   public void stop(boolean interrupted);
   public void setFinished();
   public boolean isAcquisitionRunning();
   public boolean isMultiFieldRunning();
   public int getCurrentFrameCount();
   public void shutdown();
   public void setPause(boolean state);
   
   // settings
   public double getFrameIntervalMs();
   public double getSliceZStepUm();
   public double getSliceZBottomUm();
   public void setChannel(int row, ChannelSpec channel);
   public String[] getChannelConfigs();
   public int getNumFrames();
   public String getChannelGroup();
   public boolean setChannelGroup(String newGroup);
   public void clear();
   public void setFrames(int numFrames, double interval);
   public double getMinZStepUm();
   public void setSlices(double bottom, double top, double step, boolean b);
   public boolean isZSliceSettingEnabled();
   public double getZTopUm();
   public void enableZSliceSetting(boolean boolean1);
   public void enableMultiPosition(boolean selected);
   public boolean isMultiPositionEnabled();
   public ArrayList<ChannelSpec> getChannels();
   public void setChannels(ArrayList<ChannelSpec> channels);
   public String getRootName();
   public void setRootName(String absolutePath);
   public void setCameraConfig(String config);
   public void setDirName(String text);
   public void setComment(String text);
   public boolean addChannel(String name, double exp, double offset, ContrastSettings s8, ContrastSettings s16, int skip, Color c);
   public void setSaveFiles(boolean selected);
   public boolean getSaveFiles();
   public void setSingleFrame(boolean selected);
   public int getSliceMode();
   public void setSliceMode(int mode);
   public int getPositionMode();
   public void setPositionMode(int mode);
   public void enableAutoFocus(boolean enabled);
   public boolean isAutoFocusEnabled();
   public void setParameterPreferences(Preferences prefs);
   
   // utility
   public String getVerboseSummary();
   public boolean isConfigAvailable(String config_);
   public String[] getCameraConfigs();
   public String[] getAvailableGroups();
   public double getCurrentZPos();
   public boolean isPaused();
   public Autofocus getAutofocus();
   public void restoreSystem();
}

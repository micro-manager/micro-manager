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
// CVS:          $Id$
//
package org.micromanager.utils;

import java.awt.Color;
import java.util.ArrayList;

import org.micromanager.navigation.PositionList;

import mmcorej.CMMCore;

/**
 * Acquisition engine interface.
 */
public interface AcquisitionEngine {
   
   // initialization
   public void setCore(CMMCore core_);
   public void setPositionList(PositionList posList);
   public void setParentGUI(DeviceControlGUI parent);
   public void setZStageDevice(String stageLabel_);
   public void setUpdateLiveWindow(boolean b);
   
   // run-time control
   public void acquire() throws Exception;
   public void stop();
   public boolean isAcquisitionRunning();
   public int getCurrentFrameCount();
   public void shutdown();
   
   // settings
   public double getFrameIntervalMs();
   public double getSliceZStepUm();
   public double getSliceZBottomUm();
   public void updateImageGUI();
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
   public boolean addChannel(String name, double exp, double offset, ContrastSettings s8, ContrastSettings s16, Color c);
   public void setSaveFiles(boolean selected);
   
   // utility
   public String getVerboseSummary();
   public boolean isConfigAvailable(String config_);
   public String[] getCameraConfigs();
   public String[] getAvailableGroups();
   public double getCurrentZPos();
}

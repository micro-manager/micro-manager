/*
Copyright (c) 2006 - 2013, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of
conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice, this list of
conditions and the following disclaimer in the documentation and/or other materials
provided with the distribution.
    * Neither the name of the University of California, San Francisco nor the names of its
contributors may be used to endorse or promote products derived from this software
without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.micromanager.internal;

import org.micromanager.Application;
import org.micromanager.ApplicationSkin;
import org.micromanager.Studio;
import org.micromanager.events.internal.DefaultChannelExposureEvent;
import org.micromanager.internal.hcwizard.MMConfigFileException;
import org.micromanager.internal.hcwizard.MicroscopeModel;
import org.micromanager.internal.utils.DefaultAutofocusManager;
import org.micromanager.internal.utils.ReportingUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class DefaultApplication implements Application {
  private final Studio studio_;
  private static final String EXPOSURE_KEY = "Exposure_";
  private final ApplicationSkin daytimeNighttimeManager_;

  public DefaultApplication(Studio studio, ApplicationSkin daynight) {
    studio_ = studio;
    daytimeNighttimeManager_ = daynight;
  }

  @Override
  public void refreshGUI() {
    ((MMStudio) studio_).uiManager().updateGUI(true, false);
  }

  @Override
  public void refreshGUIFromCache() {
    ((MMStudio) studio_).uiManager().updateGUI(true, true);
  }

  @Override
  public void setExposure(final double exposureTime) {
    // Avoid redundantly setting the exposure time.
    boolean shouldSetInCore = true;
    try {
      if (studio_.core() != null && studio_.core().getExposure() == exposureTime) {
        shouldSetInCore = false;
      }
    } catch (Exception e) {
      ReportingUtils.logError(e, "Error getting core exposure time");
    }
    // This is synchronized with the shutdown lock primarily so that
    // the exposure-time field in MainFrame won't cause issues when it loses
    // focus during shutdown.
    if (studio_.core() == null) {
      // Just give up.
      return;
    }
    // Do this prior to updating the Core, so that if the Core posts a
    // callback resulting in a GUI refresh, we don't have the old
    // exposure time override the new one (since GUI refreshes result in
    // resetting the exposure to the old, stored-in-profile exposure time).
    String channelGroup = "";
    String channel = "";
    try {
      channelGroup = studio_.core().getChannelGroup();
      channel = studio_.core().getCurrentConfigFromCache(channelGroup);
      storeChannelExposureTime(channelGroup, channel, exposureTime);
    } catch (Exception e) {
      studio_.logs().logError("Unable to determine channel group");
    }

    if (!studio_.core().getCameraDevice().equals("") && shouldSetInCore) {
      studio_.live().setSuspended(true);
      try {
        studio_.core().setExposure(exposureTime);
        studio_.core().waitForDevice(studio_.core().getCameraDevice());
      } catch (Exception e) {
        ReportingUtils.logError(e, "Failed to set core exposure time.");
      }
      studio_.live().setSuspended(false);
    }

    // Display the new exposure time
    double exposure;
    try {
      exposure = studio_.core().getExposure();
      studio_.events().post(new DefaultChannelExposureEvent(exposure, channelGroup, channel, true));
    } catch (Exception e) {
      ReportingUtils.logError(e, "Couldn't set exposure time.");
    }
  }

  /**
   * Updates the exposure time in the given preset Will also update current exposure if it the given
   * channel and channelgroup are the current one
   *
   * @param channelGroup -
   * @param channel - preset for which to change exposure time
   * @param exposure - desired exposure time
   */
  @Override
  public void setChannelExposureTime(String channelGroup, String channel, double exposure) {
    try {
      storeChannelExposureTime(channelGroup, channel, exposure);
      if (channelGroup != null && channelGroup.equals(studio_.core().getChannelGroup())) {
        if (channel != null
            && !channel.equals("")
            && channel.equals(studio_.core().getCurrentConfigFromCache(channelGroup))) {
          setExposure(exposure);
        }
      }
    } catch (Exception ex) {
      ReportingUtils.logError(
          "Failed to set exposure using Channelgroup: "
              + channelGroup
              + ", channel: "
              + channel
              + ", exposure: "
              + exposure);
    }
  }

  /**
   * Returns exposure time for the desired preset in the given channelgroup Acquires its info from
   * the preferences Same thing is used in MDA window, but this class keeps its own copy
   *
   * @param channelGroup Core-channelgroup
   * @param channel - specific channel of interest
   * @param defaultExp - default exposure
   * @return exposure time
   */
  @Override
  public double getChannelExposureTime(String channelGroup, String channel, double defaultExp) {
    return studio_
        .profile()
        .getSettings(Application.class)
        .getDouble(EXPOSURE_KEY + channelGroup + "_" + channel, defaultExp);
  }

  public void storeChannelExposureTime(String channelGroup, String channel, double exposure) {
    studio_
        .profile()
        .getSettings(Application.class)
        .putDouble(EXPOSURE_KEY + channelGroup + "_" + channel, exposure);
  }

  @Override
  public void saveConfigPresets(String path, boolean allowOverwrite) throws IOException {
    if (!allowOverwrite && new File(path).exists()) {
      throw new IOException("Cannot overwrite existing file at " + path);
    }
    MicroscopeModel model = new MicroscopeModel();
    try {
      String sysConfigFile = ((MMStudio) studio_).getSysConfigFile();
      model.loadFromFile(sysConfigFile);
      model.createSetupConfigsFromHardware(studio_.core());
      model.createResolutionsFromHardware(studio_.core());
      model.saveToFile(path);
      ((MMStudio) studio_).setSysConfigFile(path);
      ((MMStudio) studio_).setConfigChanged(false);
    } catch (MMConfigFileException e) {
      ReportingUtils.showError(e);
    }
  }

  @Override
  public void showAutofocusDialog() {
    ((DefaultAutofocusManager) studio_.getAutofocusManager()).showOptionsDialog();
  }

  /** Opens a dialog to record stage positions */
  @Override
  public void showPositionList() {
    ((MMStudio) studio_).uiManager().showPositionList();
  }

  @Override
  public void setROI(Rectangle r) throws Exception {
    studio_.live().setSuspended(true);
    studio_.core().setROI(r.x, r.y, r.width, r.height);
    ((MMStudio) studio_).cache().refreshValues();
    studio_.live().setSuspended(false);
  }

  @Override
  public void makeActive() {
    getMainWindow().toFront();
  }

  @Override
  public JFrame getMainWindow() {
    return ((MMStudio) studio_).uiManager().frame();
  }

  @Override
  public ApplicationSkin skin() {
    return daytimeNighttimeManager_;
  }

  @Override
  public ApplicationSkin getApplicationSkin() {
    return skin();
  }
}

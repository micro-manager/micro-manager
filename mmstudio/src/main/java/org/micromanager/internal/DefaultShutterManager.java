///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// -----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.internal;

import com.google.common.eventbus.Subscribe;
import mmcorej.DeviceType;
import org.micromanager.ShutterManager;
import org.micromanager.Studio;
import org.micromanager.events.GUIRefreshEvent;
import org.micromanager.events.LiveModeEvent;
import org.micromanager.events.SystemConfigurationLoadedEvent;
import org.micromanager.events.internal.DefaultAutoShutterEvent;
import org.micromanager.events.internal.DefaultShutterEvent;
import org.micromanager.events.internal.ShutterDevicesEvent;

import java.util.ArrayList;
import java.util.List;

public final class DefaultShutterManager implements ShutterManager {
  private Studio studio_;
  private ArrayList<String> shutters_;
  private boolean isAutoShutter_ = false;
  private boolean isOpen_ = false;

  public DefaultShutterManager(Studio studio) {
    studio_ = studio;
    studio.events().registerForEvents(this);
  }

  /** (Re)load the available shutter devices. */
  @Subscribe
  public void onSystemConfigurationLoaded(SystemConfigurationLoadedEvent event) {
    shutters_ = new ArrayList<String>();
    try {
      for (String shutter : studio_.core().getLoadedDevicesOfType(DeviceType.ShutterDevice)) {
        shutters_.add(shutter);
      }
    } catch (Exception e) {
      studio_.logs().logError(e, "Error getting shutter devices");
    }
    studio_.events().post(new ShutterDevicesEvent(shutters_));
  }

  @Subscribe
  public void onGUIRefresh(GUIRefreshEvent event) {
    repostShutterState();
  }

  @Subscribe
  public void onLiveMode(LiveModeEvent event) {
    repostShutterState();
  }

  /**
   * Check the current shutter state, and post events if it has changed compared to our cached
   * values.
   */
  private void repostShutterState() {
    try {
      boolean isOpen = getShutter();
      if (isOpen != isOpen_) {
        // Shutter state changed; notify everyone.
        isOpen_ = isOpen;
        studio_.events().post(new DefaultShutterEvent(isOpen));
      }
      boolean isAuto = getAutoShutter();
      if (isAuto != isAutoShutter_) {
        // Shutter auto state changed; notify everyone.
        isAutoShutter_ = isAuto;
        studio_.events().post(new DefaultAutoShutterEvent(isAuto));
      }
    } catch (Exception e) {
      studio_.logs().logError(e, "Error updating shutter state after GUI refresh");
    }
  }

  @Override
  public boolean setShutter(boolean isOpen) throws Exception {
    boolean isAutoOn = getAutoShutter();
    if (isAutoOn) {
      setAutoShutter(false);
    }
    studio_.core().setShutterOpen(isOpen);
    studio_.events().post(new DefaultShutterEvent(isOpen));
    isOpen_ = isOpen;
    return isAutoOn;
  }

  @Override
  public boolean getShutter() throws Exception {
    return studio_.core().getShutterOpen();
  }

  @Override
  public List<String> getShutterDevices() {
    return shutters_;
  }

  @Override
  public String getCurrentShutter() throws Exception {
    return studio_.core().getShutterDevice();
  }

  @Override
  public void setAutoShutter(boolean isAuto) throws Exception {
    studio_.core().setAutoShutter(isAuto);
    studio_.core().setShutterOpen(false);
    studio_.events().post(new DefaultAutoShutterEvent(isAuto));
    isAutoShutter_ = isAuto;
  }

  @Override
  public boolean getAutoShutter() {
    return studio_.core().getAutoShutter();
  }
}

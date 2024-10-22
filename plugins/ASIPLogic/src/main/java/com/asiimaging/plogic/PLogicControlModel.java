/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic;

import com.asiimaging.plogic.model.devices.ASIPLogic;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import org.micromanager.Studio;

/**
 * The data model for the plugin.
 */
public class PLogicControlModel {

   private final Studio studio_;
   private final CMMCore core_;

   private int selectedIndex_;
   private final List<ASIPLogic> devices_;
   private final AtomicBoolean isUpdating_;

   public PLogicControlModel(final Studio studio) {
      studio_ = Objects.requireNonNull(studio);
      core_ = studio_.core();
      selectedIndex_ = 0;
      devices_ = new ArrayList<>();
      isUpdating_ = new AtomicBoolean(false);
   }

   public boolean isUpdating() {
      return isUpdating_.get();
   }

   public void isUpdating(final boolean state) {
      isUpdating_.set(state);
   }

   public int getNumDevices() {
      return devices_.size();
   }

   public String[] getPLogicDevices() {
      return devices_
            .stream()
            .map(ASIPLogic::deviceName)
            .toArray(String[]::new);
   }

   /**
    * Return if any PLogic devices are found. Add all devices to the device list.
    *
    * @return {@code true} if a device is found
    */
   public boolean findDevices() {
      final StrVector devices = core_.getLoadedDevicesOfType(DeviceType.ShutterDevice);
      for (String device : devices) {
         if (getDeviceLibrary(device).equals(ASIPLogic.DEVICE_LIBRARY)
               && getDeviceDescription(device).startsWith(ASIPLogic.DEVICE_DESC_PREFIX)) {
            devices_.add(new ASIPLogic(studio_, device));
         }
      }
      return !devices_.isEmpty();
   }

   private String getDeviceLibrary(final String deviceName) {
      String deviceLibrary;
      try {
         deviceLibrary = core_.getDeviceLibrary(deviceName);
      } catch (Exception e) {
         deviceLibrary = ""; // return empty String if error
      }
      return deviceLibrary;
   }

   private String getDeviceDescription(final String deviceName) {
      String description;
      try {
         description = core_.getProperty(deviceName, "Description");
      } catch (Exception e) {
         description = ""; // return empty String if error
      }
      return description;
   }

   /**
    * Set the selected {@code ASIPLogic} device by index.
    * Used to make model.plc() get the correct device.
    *
    * @param index the index of the device
    */
   public void selectedIndex(final int index) {
      selectedIndex_ = index;
   }

   /**
    * Return the selected index.
    *
    * @return the selected index
    */
   public int selectedIndex() {
      return selectedIndex_;
   }

   /**
    * Returns the currently selected PLogic device.
    *
    * @return the PLogic device
    */
   public ASIPLogic plc() {
      return devices_.get(selectedIndex_);
   }

   public Studio studio() {
      return studio_;
   }

}

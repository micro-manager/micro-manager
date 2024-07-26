/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.asiimaging.plogic.model.devices.ASIPLogic;

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

    private final ASIPLogic plc_;
    private final ArrayList<String> devices_;

    private final AtomicBoolean isUpdating_;

    public PLogicControlModel(final Studio studio) {
        studio_ = Objects.requireNonNull(studio);
        core_ = studio_.core();
        devices_ = new ArrayList<>();
        // set initial deviceName in findDevices()
        plc_ = new ASIPLogic(studio_, "");
        isUpdating_ = new AtomicBoolean(false);
    }

    public boolean isUpdating() {
        return isUpdating_.get();
    }

    public void isUpdating(final boolean state) {
        isUpdating_.set(state);
    }

    /**
     * Return an array of available PLogic devices.
     *
     * @return an array of available devices
     */
    public String[] getPLogicDevices() {
        return devices_.toArray(new String[0]);
    }

    /**
     * Return true if any PLogic devices are found.
     * <p>Set device to first in list as default.
     *
     * @return true if any devices found
     */
    public boolean findDevices() {
        boolean found = false;
        final StrVector devices = core_.getLoadedDevicesOfType(DeviceType.ShutterDevice);
        for (String device : devices) {
            if (getDeviceLibrary(device).equals(ASIPLogic.DEVICE_LIBRARY)) {
                devices_.add(device);
                found = true;
            }
        }
        if (found) {
            // set device to first in list
            plc_.deviceName(devices_.get(0));
        }
        return found;
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

    /**
     * Returns the PLogic device.
     *
     * @return the PLogic device
     */
    public ASIPLogic plc() {
        return plc_;
    }

    public Studio studio() {
        return studio_;
    }

}

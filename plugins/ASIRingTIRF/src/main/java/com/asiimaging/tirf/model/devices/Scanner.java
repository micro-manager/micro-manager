/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */
package com.asiimaging.tirf.model.devices;

import org.micromanager.Studio;

/**
 * This class was designed to be used with the MMIRROR_TARGET build.
 *
 */
public class Scanner extends ASITigerDevice {

    public Scanner(final Studio studio) {
        super(studio);
    }

    /**
     * Returns true if the FAST_CIRCLES module exists on the device firmware.
     *
     * This is used to prevent the program from starting if the module is not available
     * because RING-TIRF requires FAST_CIRCLES to work.
     *
     * @return true if the module exists
     */
    public boolean hasFastCirclesModule() {
        boolean hasFastCircles = false;
        try {
            hasFastCircles = core.hasProperty(deviceName, Keys.FAST_CIRCLES_STATE);
        } catch (Exception ignore) {
            // No FAST_CIRCLES module => program requires FAST_CIRCLES so return false
        }
        return hasFastCircles;
    }

    public void setFastCirclesAsymmetry(final float value) {
        try {
            core.setProperty(deviceName, Keys.FAST_CIRCLES_ASYMMETRY, value);
        } catch (Exception e) {
            logs.logError("setFastCirclesAsymmetry failed.");
        }
    }

    public void setFastCirclesStateRestart() {
        try {
            core.setProperty(deviceName, Keys.FAST_CIRCLES_STATE, Values.FAST_CIRCLES_STATE.RESTART);
        } catch (Exception e) {
            logs.logError("setFastCirclesStateRestart failed.");
        }
    }

    public void setBeamEnabled(final boolean state) {
        try {
            core.setProperty(deviceName, Keys.BEAM_ENABLED, state ? Values.YES : Values.NO);
        } catch (Exception e) {
            logs.logError("setBeamState failed.");
        }
    }

    public void setFastCirclesState(final String state) {
        try {
            core.setProperty(deviceName, Keys.FAST_CIRCLES_STATE, state);
        } catch (Exception e) {
            logs.logError("setFastCirclesState failed.");
        }
    }

    public void setFastCirclesRate(final float rateHz) {
        try {
            core.setProperty(deviceName, Keys.FAST_CIRCLES_RATE, rateHz);
        } catch (Exception e) {
            logs.logError("setFastCirclesRateHz failed.");
        }
    }

    public void setFastCirclesRadius(final float degrees) {
        try {
            core.setProperty(deviceName, Keys.FAST_CIRCLES_RADIUS, degrees);
        } catch (Exception e) {
            logs.logError("setFastCirclesRadius failed.");
        }
    }

    public boolean getBeamEnabled() {
        String result = "";
        try {
            result = core.getProperty(deviceName, Keys.BEAM_ENABLED);
        } catch (Exception e) {
            logs.logError("setBeamState failed.");
        }
        return result.equals(Values.YES);
    }

    public float getFastCirclesRadius() {
        String result = "-1.0";
        try {
            result = core.getProperty(deviceName, Keys.FAST_CIRCLES_RADIUS);
        } catch (Exception e) {
            logs.logError("getFastCirclesRadius failed.");
        }
        return Float.parseFloat(result);
    }

    public float getFastCirclesRate() {
        String result = "-1.0";
        try {
            result = core.getProperty(deviceName, Keys.FAST_CIRCLES_RATE);
        } catch (Exception e) {
            logs.logError("getFastCirclesRate failed.");
        }
        return Float.parseFloat(result);
    }

    public String getFastCirclesState() {
        String result = "";
        try {
            result = core.getProperty(deviceName, Keys.FAST_CIRCLES_STATE);
        } catch (Exception e) {
            logs.logError("getFastCirclesState failed.");
        }
        return result;
    }

    public boolean getFastCirclesEnabled() {
        String result = "";
        try {
            result = core.getProperty(deviceName, Keys.FAST_CIRCLES_STATE);
        } catch (Exception e) {
            logs.logError("getFastCirclesEnabled failed.");
        }
        return result.equals(Values.FAST_CIRCLES_STATE.ON);
    }

    public float getFastCirclesAsymmetry() {
        String result = "-1.0f";
        try {
            result = core.getProperty(deviceName, Keys.FAST_CIRCLES_ASYMMETRY);
        } catch (Exception e) {
            logs.logError("setFastCirclesAsymmetry failed.");
        }
        return Float.parseFloat(result);
    }

    // TODO: move these elsewhere?
    public void moveH(final float value) {
        try {
            core.setProperty("TigerCommHub","SerialCommand", "m h=" + value);
        } catch (Exception e) {
            logs.logError("moveH failed.");
        }
    }

    public void moveI(final float value) {
        try {
            core.setProperty("TigerCommHub","SerialCommand", "m i=" + value);
        } catch (Exception e) {
            logs.logError("moveI failed.");
        }
    }

    public float getPositionH() {
        String response = "";
        try {
            core.setProperty("TigerCommHub", "SerialCommand", "w h?");
            response = core.getProperty("TigerCommHub","SerialResponse");
            response = response.split(" ")[1];
        } catch (Exception e) {
            logs.logError("could not get the positon of axis H.");
        }
        return Float.parseFloat(response);
    }

    public float getPositionI() {
        String response = "";
        try {
            core.setProperty("TigerCommHub", "SerialCommand", "w i?");
            response = core.getProperty("TigerCommHub","SerialResponse");
            response = response.split(" ")[1];
        } catch (Exception e) {
            logs.logError("could not get the positon of axis I.");
        }
        return Float.parseFloat(response);
    }


    public void setSaveSettings() {
        try {
            core.setProperty(deviceName, Keys.SAVE_CARD_SETTINGS, Values.SAVE_SETTINGS_DONE);
        } catch (Exception e) {
            logs.logError("could not save settings to the scanner.");
        }
    }

    // Device Properties
    public static class Keys {
        public static final String ATTENUATE_X = "AttenuateX(0..1)";
        public static final String ATTENUATE_Y = "AttenuateY(0..1)";
        public static final String AXIS_POLARITY_X = "AxisPolarityX";
        public static final String AXIS_POLARITY_Y = "AxisPolarityY";
        public static final String BEAM_ENABLED = "BeamEnabled";

        // FAST_CIRCLES module
        public static final String FAST_CIRCLES_ASYMMETRY = "FastCirclesAsymmetry";
        public static final String FAST_CIRCLES_RADIUS = "FastCirclesRadius(deg)";
        public static final String FAST_CIRCLES_RATE = "FastCirclesRate(Hz)";
        public static final String FAST_CIRCLES_STATE = "FastCirclesState";

        public static final String FILTER_FREQ_X = "FilterFreqX(kHz)";
        public static final String FILTER_FREQ_Y = "FilterFreqY(kHz)";

        public static final String INPUT_MODE = "InputMode";
        public static final String JOYSTICK_FAST_SPEED = "JoystickFastSpeed";
        public static final String JOYSTICK_SLOW_SPEED = "JoystickSlowSpeed";
        public static final String JOYSTICK_INPUT_X = "JoystickInputX";
        public static final String JOYSTICK_INPUT_Y = "JoystickInputY";
        public static final String JOYSTICK_REVERSE = "JoystickReverse";

        public static final String LASER_OUTPUT_MODE = "LaserOutputMode";
        public static final String LASER_SWITCH_TIME = "LaserSwitchTime(ms)";

        public static final String MAX_DEFLECTION_X = "MaxDeflectionX(deg)";
        public static final String MAX_DEFLECTION_Y = "MaxDeflectionY(deg)";
        public static final String MIN_DEFLECTION_X = "MinDeflectionX(deg)";
        public static final String MIN_DEFLECTION_Y = "MinDeflectionX(deg)";

        public static final String REFRESH_PROPERTY_VALUES = "RefreshPropertyValues";
        public static final String SAVE_CARD_SETTINGS = "SaveCardSettings";

        public static final String RING_BUFFER_AUTOPLAY_RUNNING = "MaxDeflectionX(deg)";
        public static final String RING_BUFFER_DELAY_BETWEEN_POINTS = "MaxDeflectionX(deg)";
        public static final String RING_BUFFER_MODE = "RingBufferMode";
        public static final String RING_BUFFER_TRIGGER = "RingBufferTrigger";

        public static final String TARGET_EXPOSURE_TIME = "TargetExposureTime(ms)";
        public static final String TARGET_SETTLING_TIME = "TargetSettlingTime(ms)";

        // TODO: single axis properties?

        public static final String VECTOR_MOVE_X = "VectorMoveX-VE(mm/s)";
        public static final String VECTOR_MOVE_Y = "VectorMoveY-VE(mm/s)";

        public static final String WHEEL_REVERSE = "WheelReverse";
        public static final String WHEEL_FAST_SPEED = "WheelFastSpeed";
        public static final String WHEEL_SLOW_SPEED = "WheelSlowSpeed";

        public static class ReadOnly {
            public static final String AXIS_LETTER_X = "AxisLetterX";
            public static final String AXIS_LETTER_Y = "AxisLetterY";
            public static final String SCANNER_TRAVEL_RANGE = "ScannerTravelRange(deg)";
        }
    }

    public static class Values {
        // BEAM_ENABLED, JOYSTICK_REVERSE, RING_BUFFER_AUTOPLAY_RUNNING, WHEEL_REVERSE
        public static final String NO = "No";
        public static final String YES = "Yes";

        public static final String SAVE_SETTINGS_DONE = "save settings done";

        public static class AXIS_POLARITY {
            public static final String NORMAL = "Normal";
            public static final String REVERSED = "Reversed";
        }

        public static class FAST_CIRCLES_STATE {
            public static final String OFF = "Off";
            public static final String ON = "On";
            public static final String RESTART = "Restart";
        }

        public static class INPUT_MODE {
            public static final String INTERNAL_INPUT = "internal input";
            public static final String EXTERNAL_INPUT = "external input";
        }

        // JOYSTICK_INPUT_X and JOYSTICK_INPUT_Y
        public static class JOYSTICK_INPUT {
            public static final String NONE = "0 - none";
            public static final String JOYSTICK_X = "2 - joystick X";
            public static final String JOYSTICK_Y = "3 - joystick Y";
            public static final String RIGHT_WHEEL = "22 - right wheel";
            public static final String LEFT_WHEEL = "23 - left wheel";
        }

        public static class LASER_OUTPUT_MODE {
            public static final String FAST_CIRCLES = "fast circles";
            public static final String INDIVIDUAL_SHUTTERS = "individual shutters";
            public static final String SHUTTER_SIDE = "shutter + side";
            public static final String SIDE_SIDE = "side + side";
        }

        public static class RING_BUFFER_MODE {
            public static final String DO_IT = "1 - One Point";
            public static final String DONE = "2 - Play Once";
            public static final String NOT_DONE = "3 - Repeat";
        }

        public static class RING_BUFFER_TRIGGER {
            public static final String DO_IT = "Do it";
            public static final String DONE = "Done";
            public static final String NOT_DONE = "Not Done";
        }
    }

}

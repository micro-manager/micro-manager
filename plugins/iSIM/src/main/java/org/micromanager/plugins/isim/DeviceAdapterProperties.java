package org.micromanager.plugins.isim;

/** Device property names for the iSIMWaveforms device adapter. */
final class DeviceAdapterProperties {
   private DeviceAdapterProperties() {}

   static final String DEVICE_LIBRARY         = "iSIMWaveforms";
   static final String ALIGNMENT_MODE_ENABLED = "Alignment Mode Enabled";
   static final String EXPOSURE_TIME_MS       = "ExposureTimeMs";
   static final String READOUT_TIME_MS        = "ReadoutTimeMs";
   static final String CAMERA_PULSE_WIDTH_MS  = "Camera Pulse Width (ms)";
   static final String PARKING_FRACTION       = "Parking Fraction";
   static final String EXPOSURE_VOLTAGE_VPP   = "Exposure Voltage (Vpp)";
   static final String GALVO_OFFSET_V         = "Galvo Offset (V)";
   static final String AOTF_BLANKING_V        = "AOTF Blanking (V)";

   static String modInEnabledProp(int channel) {
      return "AOTF MOD IN " + channel + " Enabled";
   }

   static String modInVoltageProp(int channel) {
      return "AOTF MOD IN " + channel + " (V)";
   }
}

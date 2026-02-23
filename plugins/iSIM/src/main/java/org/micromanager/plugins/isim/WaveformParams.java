package org.micromanager.plugins.isim;

import org.micromanager.Studio;

/**
 * Value class holding all parameters needed to reconstruct the iSIM waveforms.
 * Populated by reading scalar properties from the iSIMWaveforms device adapter.
 */
class WaveformParams {
   private static final int N_MOD_IN = 4;

   // Timing (read from device properties)
   double exposureTimeMs;
   double readoutTimeMs;
   double cameraPulseWidthMs;

   // Derived timing
   double frameIntervalMs;     // = exposureTimeMs + readoutTimeMs
   double parkingFraction;
   double parkingTimeMs;       // = readoutTimeMs * parkingFraction
   double rampTimeMs;          // = frameIntervalMs - parkingTimeMs
   double waveformOffsetMs;    // = parkingTimeMs + (readoutTimeMs - parkingTimeMs) / 2.0
   double waveformPeriodMs;    // = 2 * frameIntervalMs

   // Voltages
   double exposurePpV;
   double galvoOffsetV;
   double waveformPpV;         // = exposurePpV * (rampTimeMs / exposureTimeMs)
   double waveformHighV;       // = galvoOffsetV + waveformPpV / 2
   double waveformLowV;        // = galvoOffsetV - waveformPpV / 2
   double aotfBlankingV;

   // Mode
   boolean alignmentModeEnabled;

   // MOD IN channels (index 0 = MOD IN 1, ..., index 3 = MOD IN 4)
   double[] modInVoltage   = new double[N_MOD_IN];
   boolean[] modInEnabled  = new boolean[N_MOD_IN];
   boolean[] modInConfigured = new boolean[N_MOD_IN];

   /**
    * Reads all waveform parameters from the iSIMWaveforms device adapter and
    * returns a fully populated WaveformParams instance.
    *
    * @param studio the Micro-Manager Studio instance
    * @param deviceLabel the label of the iSIMWaveforms device
    * @return populated WaveformParams, or null if a required property is missing
    */
   static WaveformParams fromDevice(Studio studio, String deviceLabel) {
      try {
         WaveformParams p = new WaveformParams();

         p.exposureTimeMs      = getDouble(studio, deviceLabel, "ExposureTimeMs");
         p.readoutTimeMs       = getDouble(studio, deviceLabel, "ReadoutTimeMs");
         p.cameraPulseWidthMs  = getDouble(studio, deviceLabel, "Camera Pulse Width (ms)");

         p.frameIntervalMs     = p.exposureTimeMs + p.readoutTimeMs;
         p.parkingFraction     = getDouble(studio, deviceLabel, "Parking Fraction");
         p.parkingTimeMs       = p.readoutTimeMs * p.parkingFraction;
         p.rampTimeMs          = p.frameIntervalMs - p.parkingTimeMs;
         p.waveformOffsetMs    = p.parkingTimeMs + (p.readoutTimeMs - p.parkingTimeMs) / 2.0;
         p.waveformPeriodMs    = 2.0 * p.frameIntervalMs;

         p.exposurePpV         = getDouble(studio, deviceLabel, "Exposure Voltage (Vpp)");
         p.galvoOffsetV        = getDouble(studio, deviceLabel, "Galvo Offset (V)");
         if (p.exposureTimeMs > 0) {
            p.waveformPpV      = p.exposurePpV * (p.rampTimeMs / p.exposureTimeMs);
         }
         p.waveformHighV       = p.galvoOffsetV + p.waveformPpV / 2.0;
         p.waveformLowV        = p.galvoOffsetV - p.waveformPpV / 2.0;
         p.aotfBlankingV       = getDouble(studio, deviceLabel, "AOTF Blanking (V)");

         String alignStr = studio.core().getProperty(deviceLabel, "Alignment Mode Enabled");
         p.alignmentModeEnabled = "Yes".equals(alignStr);

         for (int i = 0; i < N_MOD_IN; i++) {
            int ch = i + 1;
            String enabledProp = "AOTF MOD IN " + ch + " Enabled";
            String voltageProp = "AOTF MOD IN " + ch + " Voltage";
            if (studio.core().hasProperty(deviceLabel, enabledProp)) {
               p.modInConfigured[i] = true;
               p.modInEnabled[i] = "Yes".equals(
                     studio.core().getProperty(deviceLabel, enabledProp));
               p.modInVoltage[i] = getDouble(studio, deviceLabel, voltageProp);
            }
         }

         return p;
      } catch (Exception e) {
         studio.logs().logError(e, "WaveformParams.fromDevice failed");
         return null;
      }
   }

   private static double getDouble(Studio studio, String device, String prop) throws Exception {
      return Double.parseDouble(studio.core().getProperty(device, prop));
   }
}

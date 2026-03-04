package org.micromanager.plugins.isim;

import org.jfree.data.xy.XYSeries;

/**
 * Reconstructs iSIM waveforms from scalar parameters.
 * Each method returns an XYSeries with time in ms on the x-axis and
 * voltage in V on the y-axis, spanning one complete waveform period.
 *
 * <p>The waveform logic mirrors the C++ iSIMWaveforms device adapter.
 */
class WaveformReconstructor {
   private static final int N_POINTS = 500;

   /** Camera trigger output voltage when HIGH (standard 5 V TTL). */
   private static final double CAMERA_TRIGGER_HIGH_V = 5.0;

   /**
    * Camera trigger waveform: two pulses of width {@code cameraPulseWidthMs}
    * at the start of each frame.
    */
   static XYSeries cameraWaveform(WaveformParams p) {
      XYSeries series = new XYSeries("Camera Trigger", false);
      double dt = p.waveformPeriodMs / N_POINTS;
      double pulse = p.cameraPulseWidthMs;

      for (int i = 0; i <= N_POINTS; i++) {
         double t = i * dt;
         double tInFrame = t % p.frameIntervalMs;
         double v = tInFrame < pulse ? CAMERA_TRIGGER_HIGH_V : 0.0;
         series.add(t, v);
      }
      return series;
   }

   /**
    * Galvo waveform. In normal mode: a two-frame triangle wave with offset
    * rotation and a parking phase. In alignment mode: constant at
    * {@code galvoOffsetV}.
    */
   static XYSeries galvoWaveform(WaveformParams p) {
      XYSeries series = new XYSeries("Galvo", false);
      if (p.alignmentModeEnabled) {
         series.add(0.0, p.galvoOffsetV);
         series.add(p.waveformPeriodMs, p.galvoOffsetV);
         return series;
      }

      double dt = p.waveformPeriodMs / N_POINTS;
      for (int i = 0; i <= N_POINTS; i++) {
         double t = i * dt;
         series.add(t, galvoVoltageAt(t, p));
      }
      return series;
   }

   /**
    * AOTF Blanking waveform. In normal mode: HIGH during the exposure window of
    * each frame (after readout), LOW during readout. In alignment mode: constant
    * at {@code aotfBlankingV}.
    */
   static XYSeries blankingWaveform(WaveformParams p) {
      XYSeries series = new XYSeries("AOTF Blanking", false);
      if (p.alignmentModeEnabled) {
         series.add(0.0, p.aotfBlankingV);
         series.add(p.waveformPeriodMs, p.aotfBlankingV);
         return series;
      }

      double dt = p.waveformPeriodMs / N_POINTS;
      for (int i = 0; i <= N_POINTS; i++) {
         double t = i * dt;
         double tInFrame = t % p.frameIntervalMs;
         double v = tInFrame >= p.readoutTimeMs ? p.aotfBlankingV : 0.0;
         series.add(t, v);
      }
      return series;
   }

   /**
    * AOTF MOD IN waveform for a given channel (0-indexed). Identical pattern to
    * blanking but uses the per-channel voltage. In alignment mode: constant at
    * the channel voltage.
    *
    * @param p            waveform parameters
    * @param channelIndex 0-based index (0 = MOD IN 1, ..., 3 = MOD IN 4)
    */
   static XYSeries modInWaveform(WaveformParams p, int channelIndex) {
      if (channelIndex < 0 || channelIndex >= p.modInVoltage.length) {
         throw new IllegalArgumentException(
               "channelIndex out of range: " + channelIndex
               + " (expected 0–" + (p.modInVoltage.length - 1) + ")");
      }
      double voltage = p.modInVoltage[channelIndex];
      XYSeries series = new XYSeries("AOTF MOD IN " + (channelIndex + 1), false);
      if (p.alignmentModeEnabled) {
         series.add(0.0, voltage);
         series.add(p.waveformPeriodMs, voltage);
         return series;
      }

      double dt = p.waveformPeriodMs / N_POINTS;
      for (int i = 0; i <= N_POINTS; i++) {
         double t = i * dt;
         double tInFrame = t % p.frameIntervalMs;
         double v = tInFrame >= p.readoutTimeMs ? voltage : 0.0;
         series.add(t, v);
      }
      return series;
   }

   /**
    * Computes the galvo voltage at time {@code t} using the offset-rotated
    * two-frame triangle wave.
    *
    * <p>std::rotate(begin, end - n, end) moves the element at index (size - n) to
    * position 0, so the rotated value at t equals the original value at
    * (t - waveformOffsetMs) mod period.
    */
   private static double galvoVoltageAt(double t, WaveformParams p) {
      double period = p.waveformPeriodMs;
      double tAdj = ((t % period) - p.waveformOffsetMs + period) % period;

      // Determine which frame and position within that frame
      double tInFrame = tAdj % p.frameIntervalMs;
      boolean frame0  = tAdj < p.frameIntervalMs;

      if (frame0) {
         // Frame 0: ramp from lowV to highV over rampTimeMs, then park at highV
         if (tInFrame < p.rampTimeMs) {
            return p.waveformLowV + (tInFrame / p.rampTimeMs) * p.waveformPpV;
         } else {
            return p.waveformHighV;
         }
      } else {
         // Frame 1: ramp from highV to lowV over rampTimeMs, then park at lowV
         if (tInFrame < p.rampTimeMs) {
            return p.waveformHighV - (tInFrame / p.rampTimeMs) * p.waveformPpV;
         } else {
            return p.waveformLowV;
         }
      }
   }
}

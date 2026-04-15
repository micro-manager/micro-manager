package edu.ucsf.valelab.mmclearvolumeplugin.animation;

/**
 * Represents a single parsed instruction from a 3D animation script.
 *
 * <p>The animation language is described in:
 * Wan et al. (2019) Nature Methods, "The iLastik interactive learning and
 * segmentation toolkit". Each instruction covers a time interval [beginFrame,
 * endFrame] (inclusive), an action type, action parameters, an optional
 * channel index, and an easing function.
 *
 * <p>For "At frame N" instructions, beginFrame == endFrame.
 */
public final class AnimationInstruction {

   /**
    * All supported action types in the animation language.
    *
    * <p>Actions marked "unsupported" have no direct ClearVolume API mapping
    * and will be logged as warnings during playback.
    */
   public enum ActionType {
      // Rotation
      ROTATE_H,              // rotate by N degrees horizontally
      ROTATE_V,              // rotate by N degrees vertically
      ROTATE_AXIS,           // rotate by N degrees around (ax, ay, az)

      // Translation
      TRANSLATE_H,           // translate horizontally by N
      TRANSLATE_V,           // translate vertically by N
      TRANSLATE_XYZ,         // translate by (x, y, z)

      // Zoom
      ZOOM,                  // zoom by a factor of N

      // Bounding-box / clipping
      CHANGE_CLIP_MIN_X,
      CHANGE_CLIP_MAX_X,
      CHANGE_CLIP_X,         // (min, max)
      CHANGE_CLIP_MIN_Y,
      CHANGE_CLIP_MAX_Y,
      CHANGE_CLIP_Y,
      CHANGE_CLIP_MIN_Z,
      CHANGE_CLIP_MAX_Z,
      CHANGE_CLIP_Z,
      CHANGE_FRONT_CLIP,
      CHANGE_BACK_CLIP,
      CHANGE_FRONT_BACK_CLIP,

      // Channel intensity
      CHANGE_CH_MIN_INTENSITY,
      CHANGE_CH_MAX_INTENSITY,
      CHANGE_CH_INTENSITY,       // (min, max, gamma)
      CHANGE_CH_INTENSITY_GAMMA,

      // Channel alpha (unsupported — no direct ClearVolume API)
      CHANGE_CH_MIN_ALPHA,
      CHANGE_CH_MAX_ALPHA,
      CHANGE_CH_ALPHA,           // (min, max, gamma)
      CHANGE_CH_ALPHA_GAMMA,

      // Channel color, weight, and visibility
      CHANGE_CH_COLOR,           // (r, g, b) in 0-255
      CHANGE_CH_WEIGHT,          // approximated via layer visibility (threshold 0.5)
      CHANGE_CH_VISIBLE,         // [1.0 = visible, 0.0 = hidden] — direct boolean

      // Dataset time axis
      CHANGE_TIME,               // change time to N  (N = 1-based in script; stored 0-based)

      // Viewer reset — restores the default state (identity rotation, default
      // translation, full clip box) as a known baseline before applying
      // absolute-delta instructions.  Reproducible regardless of the viewer
      // state when the script was started.
      RESET,
   }

   /** Easing functions controlling the interpolation curve. */
   public enum Easing {
      LINEAR,       // constant speed
      EASE_IN,      // slow start, then accelerating
      EASE_OUT,     // linear start, then decelerating
      EASE_IN_OUT,  // slow start and slow end
      EASE,         // same shape as EASE_IN_OUT, but less dramatic
   }

   /** First frame of the interval (0-based). */
   public final int beginFrame;

   /** Last frame of the interval (0-based, inclusive). Equal to beginFrame for "At frame N". */
   public final int endFrame;

   /** What this instruction does. */
   public final ActionType action;

   /**
    * Action-specific numeric parameters.
    *
    * <p>Interpretation depends on {@code action}:
    * <ul>
    *   <li>ROTATE_H / ROTATE_V: [totalDegrees]</li>
    *   <li>ROTATE_AXIS: [totalDegrees, axisX, axisY, axisZ]</li>
    *   <li>TRANSLATE_H / TRANSLATE_V: [delta]</li>
    *   <li>TRANSLATE_XYZ: [dx, dy, dz]</li>
    *   <li>ZOOM: [factor]</li>
    *   <li>CHANGE_CLIP_MIN_X/MAX_X (and Y, Z): [value] (-1 to 1)</li>
    *   <li>CHANGE_CLIP_X/Y/Z: [min, max]</li>
    *   <li>CHANGE_FRONT_CLIP / CHANGE_BACK_CLIP: [value]</li>
    *   <li>CHANGE_FRONT_BACK_CLIP: [value] (applied to both)</li>
    *   <li>CHANGE_CH_MIN/MAX_INTENSITY: [value] (0–1 normalised)</li>
    *   <li>CHANGE_CH_INTENSITY / _ALPHA: [min, max, gamma]</li>
    *   <li>CHANGE_CH_INTENSITY_GAMMA / _ALPHA_GAMMA: [gamma]</li>
    *   <li>CHANGE_CH_COLOR: [r, g, b] (0–255)</li>
    *   <li>CHANGE_CH_WEIGHT: [weight] (0–1)</li>
    *   <li>CHANGE_TIME one-param form: [targetTimePointIndex] — start comes from viewer</li>
    *   <li>CHANGE_TIME two-param form:
    *       [startTimePointIndex, targetTimePointIndex] — explicit start</li>
    *   <li>(Both forms store indices 0-based internally; script syntax is 1-based)</li>
    * </ul>
    *
    * <p>A params entry may be {@link Double#NaN} when the corresponding
    * {@link #paramFunctions} slot names a script function that supplies the
    * value at runtime.
    */
   public final double[] params;

   /**
    * Per-parameter script function names, parallel to {@link #params}.
    * A non-null entry at index {@code i} means {@code params[i]} should be
    * replaced at runtime by calling the named script function with the current
    * frame number as its argument.  May be {@code null} if no parameter of
    * this instruction uses a script function.
    */
   public final String[] paramFunctions;

   /**
    * Zero-based channel index for channel-specific actions, or -1 for
    * non-channel actions.
    */
   public final int channel;

   /** Easing function to apply during interpolation. Defaults to LINEAR. */
   public final Easing easing;

   public AnimationInstruction(int beginFrame, int endFrame,
                               ActionType action, double[] params,
                               int channel, Easing easing) {
      this(beginFrame, endFrame, action, params, null, channel, easing);
   }

   public AnimationInstruction(int beginFrame, int endFrame,
                               ActionType action, double[] params,
                               String[] paramFunctions,
                               int channel, Easing easing) {
      this.beginFrame = beginFrame;
      this.endFrame = endFrame;
      this.action = action;
      this.params = params.clone();
      this.paramFunctions = paramFunctions == null ? null : paramFunctions.clone();
      this.channel = channel;
      this.easing = easing;
   }

   @Override
   public String toString() {
      return String.format("AnimationInstruction{frames=%d-%d, action=%s, channel=%d, easing=%s}",
            beginFrame, endFrame, action, channel, easing);
   }
}

package edu.ucsf.valelab.mmclearvolumeplugin.animation;

import com.jogamp.opengl.math.Quaternion;
import edu.ucsf.valelab.mmclearvolumeplugin.CVViewer;
import edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationInstruction.ActionType;
import edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationInstruction.Easing;
import edu.ucsf.valelab.mmclearvolumeplugin.recorder.AnimationFrameRecorder;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.micromanager.LogManager;

/**
 * Executes a parsed 3D animation script frame-by-frame, driving the
 * {@link CVViewer} renderer and capturing each rendered frame for export.
 *
 * <p>Rotation is applied by composing incremental quaternions with the
 * renderer's current quaternion each frame, so it integrates cleanly with
 * ClearVolume's quaternion-based rotation model.
 *
 * <p>Two export targets are supported:
 * <ul>
 *   <li>{@link ExportTarget#IMAGEJ} — collects frames into an ImageJ
 *       {@link ImageStack} and opens it as an {@link ImagePlus} window.</li>
 *   <li>{@link ExportTarget#FFMPEG} — writes PNG frames to a temp directory
 *       and invokes ffmpeg to produce an MP4.</li>
 * </ul>
 *
 * <p>Call {@link #play()} to start playback on the calling thread (run on a
 * background thread). Call {@link #stop()} from any thread to request early
 * termination.
 */
public final class AnimationPlayer {

   /** Where captured frames should be sent. */
   public enum ExportTarget {
      /** Collect frames into an ImageJ stack window. */
      IMAGEJ,
      /** Write an MP4 via ffmpeg. */
      FFMPEG,
      /**
       * Preview only: drive the viewer at the requested frame rate without
       * capturing or saving anything.
       */
      PREVIEW
   }

   private static final double DEG_TO_RAD = Math.PI / 180.0;

   private final CVViewer viewer_;
   private final List<AnimationInstruction> instructions_;
   private final ScriptFunctions scriptFunctions_;
   private final int totalFrames_;
   private final int fps_;
   private final ExportTarget target_;
   private final String ffmpegPath_;     // null for IMAGEJ target
   private final String outputPath_;     // full path for MP4 output (FFMPEG only)
   private final boolean restoreState_;  // restore viewer state after playback
   private final LogManager log_;

   private volatile boolean stopped_ = false;

   /**
    * Lazily populated map from (instruction identity × paramIndex) to the
    * viewer's actual value at the instruction's beginFrame. Used to
    * interpolate from the true starting state rather than from 0.
    * Key encoding: System.identityHashCode(instr) * 100 + paramIndex.
    */
   private final Map<Long, Double> startValues_ = new HashMap<Long, Double>();

   /**
    * Creates an AnimationPlayer.
    *
    * @param viewer          the CVViewer to animate
    * @param instructions    parsed animation instructions
    * @param scriptFunctions script functions referenced by the instructions
    * @param totalFrames     total number of frames to render
    * @param fps             playback and capture frame rate
    * @param target          export destination
    * @param ffmpegPath      path to ffmpeg binary (required for FFMPEG target)
    * @param outputPath      full output file path for MP4 (required for FFMPEG target)
    * @param restoreState    if true, the viewer is returned to its pre-animation
    *                        state when playback ends (or is stopped)
    * @param log             Micro-Manager log manager for warnings
    */
   public AnimationPlayer(CVViewer viewer,
                          List<AnimationInstruction> instructions,
                          ScriptFunctions scriptFunctions,
                          int totalFrames,
                          int fps,
                          ExportTarget target,
                          String ffmpegPath,
                          String outputPath,
                          boolean restoreState,
                          LogManager log) {
      viewer_ = viewer;
      instructions_ = instructions;
      scriptFunctions_ = scriptFunctions != null ? scriptFunctions : ScriptFunctions.EMPTY;
      totalFrames_ = totalFrames;
      fps_ = fps;
      target_ = target;
      ffmpegPath_ = ffmpegPath;
      outputPath_ = outputPath;
      restoreState_ = restoreState;
      log_ = log;
   }

   /**
    * Requests that playback stop after the current frame completes.
    * Safe to call from any thread.
    */
   public void stop() {
      stopped_ = true;
   }

   /**
    * Runs the animation. Blocks until all frames are rendered or
    * {@link #stop()} is called. Should be invoked on a background thread.
    *
    * @throws IOException if frame capture or ffmpeg export fails
    * @throws InterruptedException if the playback thread is interrupted
    */
   public void play() throws IOException, InterruptedException {
      startValues_.clear();
      if (target_ == ExportTarget.PREVIEW) {
         playPreview();
         return;
      }

      CVViewer.ViewerState savedState = restoreState_ ? viewer_.snapshotState() : null;

      // Export setup.
      ImageStack stack = null;             // used for IMAGEJ target
      File tempDir = null;                 // used for FFMPEG target
      int pngIndex = 0;

      if (target_ == ExportTarget.FFMPEG) {
         String dirPath = System.getProperty("java.io.tmpdir")
               + File.separator + "cv_anim_" + System.currentTimeMillis();
         tempDir = new File(dirPath);
         if (!tempDir.mkdirs()) {
            throw new IOException("Cannot create temp directory: " + dirPath);
         }
      }

      // Install the frame recorder directly (no toggle/redraw side effects).
      // It uses glReadPixels via the OpenGL callback — never AWT canvas.paint().
      AnimationFrameRecorder recorder = new AnimationFrameRecorder();
      viewer_.setAnimationRecorder(recorder);

      try {
         for (int frame = 0; frame < totalFrames_; frame++) {
            if (stopped_) {
               break;
            }

            // Apply all instructions active at this frame.
            for (AnimationInstruction instr : instructions_) {
               if (frame >= instr.beginFrame && frame <= instr.endFrame) {
                  applyInstruction(instr, frame);
               }
            }

            // Arm the recorder, trigger a redraw, then wait for the OpenGL
            // callback to deliver the frame via glReadPixels.
            recorder.arm();
            viewer_.addTranslationZ(0f);  // force a redraw
            boolean captured = recorder.await(3, TimeUnit.SECONDS);

            BufferedImage img = recorder.getLastFrame();
            if (!captured || img == null) {
               log_.logMessage("AnimationPlayer: frame capture timed out at frame " + frame);
               continue;
            }

            if (target_ == ExportTarget.IMAGEJ) {
               if (stack == null) {
                  stack = new ImageStack(img.getWidth(), img.getHeight());
               }
               stack.addSlice(new ColorProcessor(img));
            } else {
               // FFMPEG: write lossless PNG.
               pngIndex++;
               File frameFile = new File(tempDir,
                     String.format("frame_%06d.png", pngIndex));
               ImageIO.write(img, "png", frameFile);
            }
         }
      } finally {
         // Disarm and remove the recorder regardless of how the loop exits.
         recorder.setActive(false);
         viewer_.setAnimationRecorder(null);
         if (restoreState_ && savedState != null) {
            viewer_.restoreState(savedState);
         }
         if (target_ == ExportTarget.FFMPEG && tempDir != null) {
            try {
               runFfmpeg(tempDir, pngIndex);
            } finally {
               deleteTempDir(tempDir);
            }
         }
      }

      // Show ImageJ result on EDT.
      if (target_ == ExportTarget.IMAGEJ && stack != null) {
         final ImageStack finalStack = stack;
         javax.swing.SwingUtilities.invokeLater(() -> {
            ImagePlus ip = new ImagePlus("3D Animation", finalStack);
            ip.show();
         });
      }
   }

   /**
    * Preview mode: applies instructions and paces playback at the requested
    * fps, but does not capture or save any frames.
    */
   private void playPreview() throws InterruptedException {
      CVViewer.ViewerState savedState = restoreState_ ? viewer_.snapshotState() : null;
      long frameIntervalMs = (fps_ > 0) ? (1000L / fps_) : 100L;

      try {
         for (int frame = 0; frame < totalFrames_; frame++) {
            if (stopped_) {
               break;
            }

            long frameStart = System.currentTimeMillis();

            for (AnimationInstruction instr : instructions_) {
               if (frame >= instr.beginFrame && frame <= instr.endFrame) {
                  applyInstruction(instr, frame);
               }
            }

            viewer_.addTranslationZ(0f);  // force a redraw

            long elapsed = System.currentTimeMillis() - frameStart;
            long remaining = frameIntervalMs - elapsed;
            if (remaining > 0) {
               Thread.sleep(remaining);
            }
         }
      } finally {
         if (restoreState_ && savedState != null) {
            viewer_.restoreState(savedState);
         }
      }
   }

   // -----------------------------------------------------------------------
   // Instruction application
   // -----------------------------------------------------------------------

   private void applyInstruction(AnimationInstruction instr, int frame) {
      double et = easedProgress(instr, frame);

      switch (instr.action) {

         // ---- Rotation ----
         case ROTATE_H: {
            double deltaDeg = deltaForParam(instr, 0, frame);
            applyYawDeg(deltaDeg);
            break;
         }
         case ROTATE_V: {
            double deltaDeg = deltaForParam(instr, 0, frame);
            applyPitchDeg(deltaDeg);
            break;
         }
         case ROTATE_AXIS: {
            // Axis components are always literal; only the angle may be scripted.
            float ax = (float) instr.params[1];
            float ay = (float) instr.params[2];
            float az = (float) instr.params[3];
            double deltaDeg = deltaForParam(instr, 0, frame);
            applyAxisRotationDeg(deltaDeg, ax, ay, az);
            break;
         }

         // ---- Translation ----
         case TRANSLATE_H:
            viewer_.addTranslationX((float) deltaForParam(instr, 0, frame));
            break;
         case TRANSLATE_V:
            viewer_.addTranslationY((float) deltaForParam(instr, 0, frame));
            break;
         case TRANSLATE_XYZ:
            viewer_.addTranslationX((float) deltaForParam(instr, 0, frame));
            viewer_.addTranslationY((float) deltaForParam(instr, 1, frame));
            viewer_.addTranslationZ((float) deltaForParam(instr, 2, frame));
            break;

         // ---- Zoom (Z-translation) ----
         case ZOOM:
            viewer_.addTranslationZ((float) deltaForParam(instr, 0, frame));
            break;

         // ---- Clipping ----
         case CHANGE_CLIP_MIN_X:
            viewer_.setClipMinMax(0, (float) targetForParam(instr, 0, frame, et), null);
            break;
         case CHANGE_CLIP_MAX_X:
            viewer_.setClipMinMax(0, null, (float) targetForParam(instr, 0, frame, et));
            break;
         case CHANGE_CLIP_X:
            viewer_.setClipRange(0,
                  (float) targetForParam(instr, 0, frame, et),
                  (float) targetForParam(instr, 1, frame, et));
            break;
         case CHANGE_CLIP_MIN_Y:
            viewer_.setClipMinMax(1, (float) targetForParam(instr, 0, frame, et), null);
            break;
         case CHANGE_CLIP_MAX_Y:
            viewer_.setClipMinMax(1, null, (float) targetForParam(instr, 0, frame, et));
            break;
         case CHANGE_CLIP_Y:
            viewer_.setClipRange(1,
                  (float) targetForParam(instr, 0, frame, et),
                  (float) targetForParam(instr, 1, frame, et));
            break;
         case CHANGE_CLIP_MIN_Z:
            viewer_.setClipMinMax(2, (float) targetForParam(instr, 0, frame, et), null);
            break;
         case CHANGE_CLIP_MAX_Z:
            viewer_.setClipMinMax(2, null, (float) targetForParam(instr, 0, frame, et));
            break;
         case CHANGE_CLIP_Z:
            viewer_.setClipRange(2,
                  (float) targetForParam(instr, 0, frame, et),
                  (float) targetForParam(instr, 1, frame, et));
            break;
         case CHANGE_FRONT_CLIP:
            viewer_.setClipMinMax(2, (float) targetForParam(instr, 0, frame, et), null);
            break;
         case CHANGE_BACK_CLIP:
            viewer_.setClipMinMax(2, null, (float) targetForParam(instr, 0, frame, et));
            break;
         case CHANGE_FRONT_BACK_CLIP: {
            float v = (float) targetForParam(instr, 0, frame, et);
            viewer_.setClipMinMax(2, v, v);
            break;
         }

         // ---- Channel intensity ----
         case CHANGE_CH_MIN_INTENSITY:
            viewer_.setTransferFunctionMin(instr.channel,
                  (float) targetForParam(instr, 0, frame, et));
            break;
         case CHANGE_CH_MAX_INTENSITY:
            viewer_.setTransferFunctionMax(instr.channel,
                  (float) targetForParam(instr, 0, frame, et));
            break;
         case CHANGE_CH_INTENSITY:
            viewer_.setTransferFunctionRange(instr.channel,
                  (float) targetForParam(instr, 0, frame, et),
                  (float) targetForParam(instr, 1, frame, et));
            viewer_.setGamma(instr.channel, targetForParam(instr, 2, frame, et));
            break;
         case CHANGE_CH_INTENSITY_GAMMA:
            viewer_.setGamma(instr.channel, targetForParam(instr, 0, frame, et));
            break;

         // ---- Channel color ----
         case CHANGE_CH_COLOR: {
            int r = (int) Math.round(targetForParam(instr, 0, frame, et));
            int g = (int) Math.round(targetForParam(instr, 1, frame, et));
            int b = (int) Math.round(targetForParam(instr, 2, frame, et));
            viewer_.setChannelColor(instr.channel,
                  new Color(clamp(r, 0, 255), clamp(g, 0, 255), clamp(b, 0, 255)));
            break;
         }

         // ---- Channel weight (approximated via visibility) ----
         case CHANGE_CH_WEIGHT:
            viewer_.setChannelVisible(instr.channel,
                  targetForParam(instr, 0, frame, et) > 0.5);
            break;

         // ---- Channel visibility (direct boolean) ----
         case CHANGE_CH_VISIBLE:
            viewer_.setChannelVisible(instr.channel, instr.params[0] >= 0.5);
            break;

         // ---- Time axis ----
         case CHANGE_TIME: {
            // Two-param form "change time from A to B": params[0]=start, params[1]=target.
            // One-param form "change time to B":        params[0]=target.
            int targetIdx = instr.params.length >= 2 ? 1 : 0;
            double raw = targetForParam(instr, targetIdx, frame, et);
            // Script functions return 1-based time points; subtract 1 to get 0-based.
            // Literal params are already stored 0-based (adjusted at parse time).
            boolean isFunction = instr.paramFunctions != null
                  && instr.paramFunctions[targetIdx] != null;
            int t = (int) Math.round(isFunction ? raw - 1.0 : raw);
            viewer_.setTimePoint(t);
            break;
         }

         // ---- Unsupported alpha actions ----
         case CHANGE_CH_MIN_ALPHA:
         case CHANGE_CH_MAX_ALPHA:
         case CHANGE_CH_ALPHA:
         case CHANGE_CH_ALPHA_GAMMA:
            log_.logMessage("AnimationPlayer: alpha actions are not supported "
                  + "by ClearVolume; skipping: " + instr.action);
            break;

         default:
            log_.logMessage("AnimationPlayer: unhandled action: " + instr.action);
            break;
      }
   }

   // -----------------------------------------------------------------------
   // Rotation helpers (quaternion composition)
   // -----------------------------------------------------------------------

   /** Applies a horizontal (yaw, Y-axis) rotation delta in degrees. */
   private void applyYawDeg(double deg) {
      applyAxisRotationDeg(deg, 0f, 1f, 0f);
   }

   /** Applies a vertical (pitch, X-axis) rotation delta in degrees. */
   private void applyPitchDeg(double deg) {
      applyAxisRotationDeg(deg, 1f, 0f, 0f);
   }

   /** Composes an incremental rotation of {@code deg} degrees around (ax,ay,az)
    * onto the renderer's current quaternion. */
   private void applyAxisRotationDeg(double deg, float ax, float ay, float az) {
      // Normalise axis.
      float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
      if (len < 1e-6f) {
         return;
      }
      ax /= len;
      ay /= len;
      az /= len;

      Quaternion current = viewer_.getQuaternion();
      // Always work on a private copy so we never mutate the renderer's
      // internal quaternion instance in place (aliasing / thread-safety).
      Quaternion q = (current != null) ? new Quaternion(current) : new Quaternion();
      if (current == null) {
         q.setIdentity();
      }
      // rotateByAngleNormalAxis expects the angle in radians and a normalised axis.
      // It applies the rotation in-place on our private copy.
      float rad = (float) (deg * DEG_TO_RAD);
      q.rotateByAngleNormalAxis(rad, ax, ay, az);
      q.normalize();
      viewer_.setQuaternion(q);
   }

   // -----------------------------------------------------------------------
   // Easing & interpolation
   // -----------------------------------------------------------------------

   /**
    * Returns the eased progress t ∈ [0,1] for {@code frame} within the
    * instruction's interval.
    */
   private static double easedProgress(AnimationInstruction instr, int frame) {
      int span = instr.endFrame - instr.beginFrame;
      if (span <= 0) {
         return 1.0;
      }
      double t = (double) (frame - instr.beginFrame) / span;
      t = Math.max(0.0, Math.min(1.0, t));
      return applyEasing(t, instr.easing);
   }

   /**
    * Returns the incremental eased-progress step between frame and frame+1,
    * i.e. et(frame+1) - et(frame). Used for delta-based actions (rotation,
    * translation) so they accumulate correctly with easing.
    */
   private static double easedProgressDelta(AnimationInstruction instr, int frame) {
      int span = instr.endFrame - instr.beginFrame;
      if (span <= 0) {
         return 1.0;
      }
      double t0 = Math.max(0.0, Math.min(1.0,
            (double) (frame - instr.beginFrame) / span));
      double t1 = Math.max(0.0, Math.min(1.0,
            (double) (frame + 1 - instr.beginFrame) / span));
      return applyEasing(t1, instr.easing) - applyEasing(t0, instr.easing);
   }

   /**
    * For "change to value" instructions: interpolates from the viewer's actual
    * value at {@code beginFrame} to the script-specified target using the
    * eased progress {@code et ∈ [0,1]}.
    *
    * <p>On the first call for a given instruction/paramIndex pair (i.e. when
    * {@code frame == instr.beginFrame}), the current viewer value is read and
    * cached as the start value.  Subsequent frames interpolate
    * {@code start + (target - start) * et}.
    *
    * <p>If the instruction has a script function for this parameter slot, the
    * function is called with the current frame number and its return value is
    * used directly (no interpolation).
    */
   private double targetForParam(AnimationInstruction instr, int paramIndex,
                                 int frame, double et) {
      if (instr.paramFunctions != null && instr.paramFunctions[paramIndex] != null) {
         // Script functions receive 1-based frame numbers, matching the script language.
         return scriptFunctions_.evaluate(instr.paramFunctions[paramIndex], frame + 1);
      }
      double target = instr.params[paramIndex];
      long key = (long) System.identityHashCode(instr) * 100 + paramIndex;
      if (!startValues_.containsKey(key)) {
         startValues_.put(key, readStartValue(instr, paramIndex));
      }
      double start = startValues_.get(key);
      return start + (target - start) * et;
   }

   /**
    * Reads the viewer's current value for the parameter at {@code paramIndex}
    * of {@code instr}, to use as the interpolation start value.
    */
   private double readStartValue(AnimationInstruction instr, int paramIndex) {
      float[] clip;
      switch (instr.action) {
         case CHANGE_CLIP_MIN_X:
         case CHANGE_CLIP_MAX_X:
         case CHANGE_CLIP_X:
            clip = viewer_.getClipBox();
            if (clip == null) {
               return 0.0;
            }
            return paramIndex == 0 ? clip[0] : clip[1];
         case CHANGE_CLIP_MIN_Y:
         case CHANGE_CLIP_MAX_Y:
         case CHANGE_CLIP_Y:
            clip = viewer_.getClipBox();
            if (clip == null) {
               return 0.0;
            }
            return paramIndex == 0 ? clip[2] : clip[3];
         case CHANGE_CLIP_MIN_Z:
         case CHANGE_FRONT_CLIP:
            clip = viewer_.getClipBox();
            return clip != null ? clip[4] : 0.0;
         case CHANGE_CLIP_MAX_Z:
         case CHANGE_BACK_CLIP:
            clip = viewer_.getClipBox();
            return clip != null ? clip[5] : 0.0;
         case CHANGE_CLIP_Z:
         case CHANGE_FRONT_BACK_CLIP:
            clip = viewer_.getClipBox();
            if (clip == null) {
               return 0.0;
            }
            return paramIndex == 0 ? clip[4] : clip[5];
         case CHANGE_CH_MIN_INTENSITY:
            return viewer_.getTransferRangeMin(instr.channel);
         case CHANGE_CH_MAX_INTENSITY:
            return viewer_.getTransferRangeMax(instr.channel);
         case CHANGE_CH_INTENSITY:
            if (paramIndex == 0) {
               return viewer_.getTransferRangeMin(instr.channel);
            }
            if (paramIndex == 1) {
               return viewer_.getTransferRangeMax(instr.channel);
            }
            return viewer_.getGamma(instr.channel);
         case CHANGE_CH_INTENSITY_GAMMA:
            return viewer_.getGamma(instr.channel);
         case CHANGE_TIME:
            // Two-param form "change time from A to B": params[0]=start, params[1]=target.
            // The start value is the explicit params[0], not the viewer's current position.
            // One-param form "change time to B": params[0]=target, start from viewer.
            if (instr.params.length >= 2) {
               return instr.params[0]; // explicit start, already 0-based
            }
            return viewer_.getCurrentTimePoint();
         default:
            // For actions without a readable start value, fall back to 0.
            return 0.0;
      }
   }

   /**
    * For delta-based actions (rotation, translation, zoom):
    * if the parameter is a literal number, returns the eased per-frame delta;
    * if it is a script function, calls the function with the frame number and
    * returns the result directly as the per-frame value.
    */
   private double deltaForParam(AnimationInstruction instr, int paramIndex, int frame) {
      if (instr.paramFunctions != null && instr.paramFunctions[paramIndex] != null) {
         // Script functions receive 1-based frame numbers, matching the script language.
         return scriptFunctions_.evaluate(instr.paramFunctions[paramIndex], frame + 1);
      }
      return instr.params[paramIndex] * easedProgressDelta(instr, frame);
   }

   private static double applyEasing(double t, Easing easing) {
      switch (easing) {
         case LINEAR:
            return t;
         case EASE_IN:
            return t * t;
         case EASE_OUT:
            return t * (2.0 - t);
         case EASE_IN_OUT:
            return t < 0.5 ? 2.0 * t * t : -1.0 + (4.0 - 2.0 * t) * t;
         case EASE:
            // Smoother than EASE_IN_OUT but same shape.
            return t * t * (3.0 - 2.0 * t);
         default:
            return t;
      }
   }

   // -----------------------------------------------------------------------
   // ffmpeg invocation
   // -----------------------------------------------------------------------

   private void runFfmpeg(File tempDir, int frameCount) throws IOException {
      if (frameCount == 0) {
         return;
      }
      String framePattern = new File(tempDir, "frame_%06d.png").getAbsolutePath();

      List<String> command = new ArrayList<String>();
      command.add(ffmpegPath_);
      command.add("-framerate");
      command.add(String.valueOf(fps_));
      command.add("-i");
      command.add(framePattern);
      command.add("-vf");
      command.add("scale=trunc(iw/2)*2:trunc(ih/2)*2");
      command.add("-c:v");
      command.add("libx264");
      command.add("-preset");
      command.add("medium");
      command.add("-pix_fmt");
      command.add("yuv420p");
      command.add(outputPath_);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process;
      try {
         process = pb.start();
      } catch (IOException e) {
         throw new IOException("Failed to start ffmpeg at: " + ffmpegPath_, e);
      }

      StringBuilder ffmpegOutput = new StringBuilder();
      try (BufferedReader br = new BufferedReader(
            new InputStreamReader(process.getInputStream()))) {
         String line;
         while ((line = br.readLine()) != null) {
            ffmpegOutput.append(line).append("\n");
         }
      } catch (IOException e) {
         log_.logError(e, "Error reading ffmpeg output");
      }

      int exitCode;
      try {
         exitCode = process.waitFor();
      } catch (InterruptedException e) {
         process.destroy();
         Thread.currentThread().interrupt();
         throw new IOException("Interrupted while waiting for ffmpeg", e);
      }

      if (exitCode != 0) {
         throw new IOException("ffmpeg exited with code " + exitCode
               + ".\nffmpeg output:\n" + ffmpegOutput);
      }
   }

   private static void deleteTempDir(File dir) {
      if (dir == null) {
         return;
      }
      File[] files = dir.listFiles();
      if (files != null) {
         for (File f : files) {
            f.delete();
         }
      }
      dir.delete();
   }

   // -----------------------------------------------------------------------
   // Utility
   // -----------------------------------------------------------------------

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }
}

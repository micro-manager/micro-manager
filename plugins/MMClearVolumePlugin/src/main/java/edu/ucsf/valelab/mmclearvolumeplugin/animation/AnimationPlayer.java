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
import java.util.List;
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
      IMAGEJ,
      FFMPEG
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
   private final LogManager log_;

   private volatile boolean stopped_ = false;

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
                          LogManager log) {
      viewer_ = viewer;
      instructions_ = instructions;
      scriptFunctions_ = scriptFunctions != null ? scriptFunctions : ScriptFunctions.EMPTY;
      totalFrames_ = totalFrames;
      fps_ = fps;
      target_ = target;
      ffmpegPath_ = ffmpegPath;
      outputPath_ = outputPath;
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

   // -----------------------------------------------------------------------
   // Instruction application
   // -----------------------------------------------------------------------

   /**
    * Returns the value for parameter slot {@code index} of the instruction.
    * If a script function name is stored in that slot it is evaluated with
    * {@code frame} as the argument; otherwise the literal value is returned.
    */
   private double resolveParam(AnimationInstruction instr, int index, int frame) {
      if (instr.paramFunctions != null && instr.paramFunctions[index] != null) {
         return scriptFunctions_.evaluate(instr.paramFunctions[index], frame);
      }
      return instr.params[index];
   }

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
      if (current == null) {
         current = new Quaternion();
         current.setIdentity();
      }
      // rotateByAngleNormalAxis expects the angle in radians and a normalised axis.
      // It applies the rotation in-place: current = current * delta.
      float rad = (float) (deg * DEG_TO_RAD);
      current.rotateByAngleNormalAxis(rad, ax, ay, az);
      current.normalize();
      viewer_.setQuaternion(current);
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
    * For "change to value" instructions: interpolates from the start of the
    * interval value (params[0]) toward params[0] using {@code et}. Because
    * ClearVolume exposes no "get current value" for all parameters, we treat
    * params[0] as the *target* and interpolate from 0 → target. Callers that
    * need a true start→end interpolation should use two instructions or accept
    * that the curve begins from whatever the initial state happens to be.
    *
    * <p>In practice this is fine: scripts typically specify a single target
    * value ("change channel 1 min intensity to 0.1") and rely on the initial
    * viewer state as the starting point.
    *
    * <p>If the instruction has a script function for this parameter slot, the
    * function is called with the current frame number and its return value is
    * used directly (no easing multiplication).
    */
   private double targetForParam(AnimationInstruction instr, int paramIndex,
                                 int frame, double et) {
      if (instr.paramFunctions != null && instr.paramFunctions[paramIndex] != null) {
         return scriptFunctions_.evaluate(instr.paramFunctions[paramIndex], frame);
      }
      return instr.params[paramIndex] * et;
   }

   /**
    * For delta-based actions (rotation, translation, zoom):
    * if the parameter is a literal number, returns the eased per-frame delta;
    * if it is a script function, calls the function with the frame number and
    * returns the result directly as the per-frame value.
    */
   private double deltaForParam(AnimationInstruction instr, int paramIndex, int frame) {
      if (instr.paramFunctions != null && instr.paramFunctions[paramIndex] != null) {
         return scriptFunctions_.evaluate(instr.paramFunctions[paramIndex], frame);
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
      try {
         BufferedReader br = new BufferedReader(
               new InputStreamReader(process.getInputStream()));
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

package org.micromanager.internal.navigation;

import com.google.common.util.concurrent.AtomicDouble;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.JOptionPane;
import mmcorej.MMCoreJ;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.internal.DefaultXYStagePositionChangedEvent;
import org.micromanager.internal.utils.AffineUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.ThreadFactoryFactory;

/**
 * Class that handles transformation between what the user sees on the screen
 * and how the stage moves.
 *
 * @author Nico
 */
public class XYNavigator {
   protected Studio studio_;
   protected boolean mirrorX_;
   protected boolean mirrorY_;
   protected boolean transposeXY_;
   protected boolean correction_;
   protected AffineTransform affineTransform_;
   private final Map<String, XYStageTask> xyStageMoverMap_;

   /**
    * Central interface to move the default XY stage in the direction
    * desired by the user.  Facilities to correct movement as seen on the
    * screen to movement of the XY stage are included.
    * The XY stage to be moved can be specified, however,
    * the movement corrections are likely only valid for the default XY Stage.
    * Each stage runs on its own executor.  These will only be created after
    * movement for a stage has been requested.
    *
    * @param studio Our beloved Micro-Manager Studio object
    */
   public XYNavigator(Studio studio) {
      studio_ = studio;
      xyStageMoverMap_ = new HashMap<>();
   }


   /**
    * Returns an AffineTransform representing the inverse of the ImageFlipper
    * transformation currently applied to the live display image, or null if no
    * ImageFlipper transform is active.  Because we work with pixel *deltas* the
    * transform is purely linear (no translation term matters).
    *
    * <p>The ImageFlipper applies: mirror-X first, then rotation.  To convert
    * post-flipper mouse-pixel deltas back to raw camera-pixel deltas we must
    * apply the inverse: rotate by -rotation, then mirror-X again (mirror is its
    * own inverse).
    */
   private AffineTransform getImageFlipperCorrection() {
      try {
         DisplayWindow display = studio_.live().getDisplay();
         if (display == null || display.isClosed()) {
            return null;
         }
         List<Image> images = display.getDisplayedImages();
         if (images == null || images.isEmpty()) {
            return null;
         }
         PropertyMap userData = images.get(0).getMetadata().getUserData();
         if (userData == null) {
            return null;
         }
         // Both keys must be present with the correct types for the flipper to have
         // touched this image.  Type-specific checks avoid ClassCastException if
         // unrelated metadata happens to use the same key names with different types.
         if (!userData.containsInteger("ImageFlipper-Rotation")
               || !userData.containsString("ImageFlipper-Mirror")) {
            return null;
         }
         int rotation = userData.getInteger("ImageFlipper-Rotation", 0);
         boolean mirrored = "On".equals(userData.getString("ImageFlipper-Mirror", "Off"));

         if (rotation == 0 && !mirrored) {
            return null; // identity — no correction needed
         }

         // Build the inverse of "mirror then rotate(rotation)":
         //   inverse = rotate(-rotation) then mirror
         // AffineTransform.quadrantRotate(n) rotates CCW by n*90 degrees.
         // ImageFlipper R90 = ImageJ rotateRight = CW 90, so its inverse is
         // CCW 90 = quadrantRotate(1).
         AffineTransform correction = new AffineTransform();
         // Step 1: undo the rotation
         if (rotation == 90) {
            correction.quadrantRotate(1); // CCW 90° = inverse of CW 90°
         } else if (rotation == 180) {
            correction.quadrantRotate(2); // 180° is self-inverse
         } else if (rotation == 270) {
            correction.quadrantRotate(3); // CCW 270° = inverse of CW 270°
         }
         // Step 2: undo the mirror — pre-multiply so mirror is applied AFTER rotate(-N),
         // giving correction = M * R(-N) rather than R(-N) * M.
         if (mirrored) {
            correction.preConcatenate(AffineTransform.getScaleInstance(-1.0, 1.0));
         }
         return correction;
      } catch (Exception e) {
         ReportingUtils.logError(e, "Failed to read ImageFlipper metadata");
         return null;
      }
   }

   /*
    * Ensures that the stage moves in the expected direction
    */
   private void getOrientation() {
      String camera = studio_.core().getCameraDevice();
      if (camera == null) {
         JOptionPane.showMessageDialog(null, "This function does not work without a camera");
         return;
      }
      // If there is an affine transform, use that, otherwise fallbakc to
      // the old mechanism
      try {
         double pixelSize = studio_.core().getPixelSizeUm();
         try {
            affineTransform_ = AffineUtils.doubleToAffine(studio_.core().getPixelSizeAffine(true));
            if (Math.abs(pixelSize
                  - AffineUtils.deducePixelSize(affineTransform_)) > 0.1 * pixelSize) {
               // affine transform does not correspond to pixelSize, so do not
               // trust it and fallback to old mechanism
               affineTransform_ = null;
            }
         } catch (Exception ex) {
            ReportingUtils.logError("Failed to find affine transform");
         }
      } catch (Exception exc) {
         ReportingUtils.showError(exc);
      }
      if (affineTransform_ == null) {
         // we can cache the current camera and only execute the code
         // below if the camera changed.  However, that will make it difficult
         // to experiment with these settings, and it probably is not very expensive
         // to check every time
         try {
            String tmp = studio_.core().getProperty(camera, "TransposeCorrection");
            correction_ = !(tmp.equals("0"));
            tmp = studio_.core().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX());
            mirrorX_ = !(tmp.equals("0"));
            tmp = studio_.core().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY());
            mirrorY_ = !(tmp.equals("0"));
            tmp = studio_.core().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY());
            transposeXY_ = !(tmp.equals("0"));
         } catch (Exception exc) {
            ReportingUtils.showError(exc);
         }
      } else {
         // Pre-multiply the affine transform by the inverse of any ImageFlipper
         // transform so that mouse-pixel deltas (which are in post-flipper image
         // space) are correctly mapped back to raw camera-pixel space before the
         // camera→stage affine is applied.
         AffineTransform flipperCorrection = getImageFlipperCorrection();
         if (flipperCorrection != null) {
            // concatenate() applies flipperCorrection first, then affineTransform_,
            // so the combined transform maps post-flipper pixel deltas → stage microns.
            affineTransform_.concatenate(flipperCorrection);
         }
      }
   }

   /**
    * Converts Camera-Pixel Space to Stage-micron space
    * Use the affine transform when available, otherwise uses pixelSize
    * and orientation booleans, determined using the getOrientation function.
    *
    * @param pixSizeUm - pixelSize in micron
    * @param x         - Desired x movement in pixels
    * @param y         - Desired y movement in pixels
    * @return - Needed stage movement as a Point2D.Double
    */
   private Point2D toStageSpace(double pixSizeUm, double x, double y) {
      getOrientation();
      Point2D dest = new Point2D.Double();
      if (affineTransform_ != null) {
         Point2D source = new Point2D.Double(x, y);
         affineTransform_.transform(source, dest);
         // not sure why, but for the stage movement to be correct, we need
         // to invert both axes"
         dest.setLocation(-dest.getX(), -dest.getY());
      } else {
         // if camera does not toStageSpace image orientation, we'll toStageSpace for it here:
         dest.setLocation(-x * pixSizeUm, -y * pixSizeUm);
         if (!correction_) {
            // Order: swapxy, then mirror axis
            if (transposeXY_) {
               dest.setLocation(y * pixSizeUm, x * pixSizeUm);
            }
            if (mirrorX_) {
               dest.setLocation(-dest.getX(), dest.getY());
            }
            if (mirrorY_) {
               dest.setLocation(dest.getX(), -dest.getY());
            }
         }
      }

      return dest;
   }

   /**
    * Moves the stage the requested distance.  If there is a camera, and the
    * affine transform is known, it will use that to determine the movement.
    *
    * @param tmpXUm amount (in microns) the sample should move in the x direction
    * @param tmpYUm amount (in microns) the sample should move in the y direction
    */
   public void moveSampleUm(final double tmpXUm, final double tmpYUm) {
      String xyStage = studio_.core().getXYStageDevice();
      if (xyStage == null || xyStage.isEmpty()) {
         return;
      }
      double pixSizeUm = studio_.core().getPixelSizeUm(true);
      if (pixSizeUm > 0.0) {
         moveSampleOnDisplayUm(tmpXUm, tmpYUm);
         return;
      }
      moveXYStageUm(xyStage, tmpXUm, tmpYUm);
   }

   /**
    * Move the XYStage in such a manner that the sample as displayed in the
    * viewer will move in an absolute Carthesian coordinate system.
    * For instance, moving 1 micron in x will move the stage so that the
    * image on the screen will move 1 micron to the right, which may
    * involve XYStage movement in both x and y.
    *
    * @param tmpXUm amount (in microns) the sample should move in the x direction
    * @param tmpYUm amount (in microns) the sample should move in the y direction
    */
   private void moveSampleOnDisplayUm(final double tmpXUm, final double tmpYUm) {
      double pixSizeUm = studio_.core().getPixelSizeUm(true);
      if (!(pixSizeUm > 0.0)) {
         JOptionPane.showMessageDialog(null,
               "Please provide pixel size calibration data before using this function");
         return;
      }
      String xyStage = studio_.core().getXYStageDevice();
      if (xyStage == null || xyStage.isEmpty()) {
         return;
      }
      // It is a bit funny to calculate back to pixels and then receive
      // a stageposition in microns, but that is likely more exact than
      // lying about pixelSize here (by setting it to 1)
      Point2D stagePos = toStageSpace(pixSizeUm, tmpXUm / pixSizeUm,
            tmpYUm / pixSizeUm);
      moveXYStageUm(studio_.core().getXYStageDevice(), stagePos.getX(),
            stagePos.getY());
   }

   /**
    * Move the XYStage in such a manner that the sample as displayed in the
    * viewer will move in an absolute Carthesian coordinate system.
    * For instance, moving 1 pixel in x will move the stage so that the
    * image on the screen will move 1 pixels to the right, which may
    * involve XYStage movement in both x and y.
    *
    * @param tmpXPixels amount (in pixels) the sample should move in the x direction
    * @param tmpYPixels amount (in pixels) the sample should move in the y direction
    */
   public void moveSampleOnDisplayPixels(final double tmpXPixels, final double tmpYPixels) {
      double pixSizeUm = studio_.core().getPixelSizeUm(true);
      if (!(pixSizeUm > 0.0)) {
         JOptionPane.showMessageDialog(null,
               "Please provide pixel size calibration data before using this function");
         return;
      }
      String xyStage = studio_.core().getXYStageDevice();
      if (xyStage == null || xyStage.isEmpty()) {
         return;
      }
      Point2D stagePos = toStageSpace(pixSizeUm, tmpXPixels,
            tmpYPixels);
      moveXYStageUm(studio_.core().getXYStageDevice(), stagePos.getX(),
            stagePos.getY());
   }


   /**
    * Moves the XYStage the desired distance in microns without corrections
    * Creates an XYStageTask if the stage is unknown, otherwise
    * adds the desired movement to the task.  If a task is still running,
    * it will add the movement to its future movement task.
    *
    * @param xyStage xyStage to be moved
    * @param xRel    x distance (in microns) to move the stage
    * @param yRel    y distance (in microns) to move the stage
    */
   public void moveXYStageUm(String xyStage, double xRel, double yRel) {
      if (!xyStageMoverMap_.containsKey(xyStage)) {
         xyStageMoverMap_.put(xyStage, new XYStageTask(xyStage));
      }
      // if a
      xyStageMoverMap_.get(xyStage).setPosition(xRel, yRel);
   }

   /**
    * Waits until the stage tasks have all been completed.
    *
    * @param xyStage  Stage that we are waiting for
    * @param timeoutMs Wait no more than this time (in ms)
    * @return false if device was not busy, or we waited until it was not busy,
    *          true if still busy after the timeout
    */
   public boolean waitForBusy(String xyStage, long timeoutMs) {
      if (xyStageMoverMap_.containsKey(xyStage)) {
         return xyStageMoverMap_.get(xyStage).waitForBusy(timeoutMs);
      }
      return false;
   }

   /**
    * Helper class that executes stage movement, and pools movement requests
    * if they come in while the stage is busy.
    *
    * <p>This should reduce the number of commands send to the stage when the UI
    * is firing requests faster than the stage can execute them.
    */
   private class XYStageTask implements Runnable {
      private final String xyStage_;
      private final ExecutorService executorService_;
      private Future<?> future_;
      AtomicDouble moveMemoryX_ = new AtomicDouble(0.0);
      AtomicDouble moveMemoryY_ = new AtomicDouble(0.0);

      public XYStageTask(String xyStage) {
         xyStage_ = xyStage;
         executorService_ = Executors.newSingleThreadExecutor(
               ThreadFactoryFactory.createThreadFactory("XYNavigator-" + xyStage));
      }

      /**
       * Sets the position of the XY stage. Uses the executorService to schedule the actual
       * move of the hardware.
       *
       * @param xRel - relative movement in X in microns
       * @param yRel - relative movement in Y in microns
       */
      public void setPosition(double xRel, double yRel) {
         moveMemoryX_.addAndGet(xRel);
         moveMemoryY_.addAndGet(yRel);
         if (future_ == null || future_.isDone()) {
            future_ = executorService_.submit(this);
         }
      }

      public boolean waitForBusy(long timeOutMs) {
         if (future_ == null || future_.isDone()) {
            return false;
         } else {
            try {
               future_.get(timeOutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
               return true;
            }
         }
         return false;
      }

      @Override
      public void run() {
         // Move the stage
         try {
            while (moveMemoryX_.get() != 0 || moveMemoryY_.get() != 0.0) {
               double xRel = moveMemoryX_.getAndSet(0.0);
               double yRel = moveMemoryY_.getAndSet(0.0);
               studio_.core().setRelativeXYPosition(xyStage_, xRel, yRel);
               studio_.core().waitForDevice(xyStage_);
               double[] xs = new double[1];
               double[] ys = new double[1];
               studio_.core().getXYPosition(xyStage_, xs, ys);
               studio_.events().post(
                     new DefaultXYStagePositionChangedEvent(xyStage_, xs[0], ys[0]));
            }
         } catch (Exception ex) {
            ReportingUtils.showError(ex.getMessage());
         }
      }
   }

}

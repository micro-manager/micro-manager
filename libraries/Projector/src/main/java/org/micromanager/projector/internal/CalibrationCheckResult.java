package org.micromanager.projector.internal;

import java.awt.geom.Point2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Outcome of a calibration check: for each test spot, the camera position that was targeted
 * (using the existing calibration) and the camera position where the spot was actually found.
 */
public class CalibrationCheckResult {

   /**
    * A single projected test spot.
    */
   public static final class Spot {
      public final Point2D.Double target;
      public final Point2D.Double device;
      // null when the spot was not detected
      public final Point2D.Double measured;

      Spot(Point2D.Double target, Point2D.Double device, Point2D.Double measured) {
         this.target = target;
         this.device = device;
         this.measured = measured;
      }

      public double dx() {
         return measured.x - target.x;
      }

      public double dy() {
         return measured.y - target.y;
      }

      public double error() {
         return measured.distance(target);
      }
   }

   private final List<Spot> spots_ = new ArrayList<>();
   private final double pixelSizeUm_;
   private final boolean useCentroid_;
   private final int nrCalibrationCells_;
   private final Point2D.Double offsetCorrection_;
   private final Date date_ = new Date();

   CalibrationCheckResult(double pixelSizeUm, boolean useCentroid, int nrCalibrationCells,
         Point2D.Double offsetCorrection) {
      pixelSizeUm_ = pixelSizeUm;
      useCentroid_ = useCentroid;
      nrCalibrationCells_ = nrCalibrationCells;
      offsetCorrection_ = offsetCorrection;
   }

   /**
    * Mean of measured - target over all detected spots, or null if no spot was detected.
    */
   public Point2D.Double getMeanOffset() {
      List<Spot> detected = detectedSpots();
      if (detected.isEmpty()) {
         return null;
      }
      double sx = 0;
      double sy = 0;
      for (Spot s : detected) {
         sx += s.dx();
         sy += s.dy();
      }
      return new Point2D.Double(sx / detected.size(), sy / detected.size());
   }

   public Date getDate() {
      return new Date(date_.getTime());
   }

   void add(Point2D.Double target, Point2D.Double device, Point2D.Double measured) {
      spots_.add(new Spot(target, device, measured));
   }

   public List<Spot> getSpots() {
      return Collections.unmodifiableList(spots_);
   }

   private List<Spot> detectedSpots() {
      List<Spot> detected = new ArrayList<>();
      for (Spot s : spots_) {
         if (s.measured != null) {
            detected.add(s);
         }
      }
      return detected;
   }

   private String pixAndUm(double pixels) {
      if (pixelSizeUm_ > 0) {
         return String.format(Locale.US, "%8.2f px  (%.3f micron)", pixels, pixels * pixelSizeUm_);
      }
      return String.format(Locale.US, "%8.2f px", pixels);
   }

   /**
    * Human-readable report with summary statistics and a per-spot table.
    */
   public String toReport() {
      StringBuilder sb = new StringBuilder();
      final List<Spot> detected = detectedSpots();
      sb.append("Calibration check: spots projected between calibration points\n");
      sb.append("Date: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date_))
            .append("\n");
      sb.append("Spot detection: ").append(useCentroid_
            ? "sub-pixel centroid" : "maximum of blurred image")
            .append("\n");
      sb.append("Calibration cells: ").append(nrCalibrationCells_).append("\n");
      sb.append(String.format(Locale.US, "Offset correction in use: dx = %.2f px, dy = %.2f px%n",
            offsetCorrection_.x, offsetCorrection_.y));
      sb.append(String.format(Locale.US, "Spots detected: %d of %d%n%n",
            detected.size(), spots_.size()));
      if (!detected.isEmpty()) {
         double[] errors = new double[detected.size()];
         double sumErr = 0;
         double sumSq = 0;
         double sumDx = 0;
         double sumDy = 0;
         Spot worst = detected.get(0);
         for (int i = 0; i < detected.size(); i++) {
            Spot s = detected.get(i);
            errors[i] = s.error();
            sumErr += errors[i];
            sumSq += errors[i] * errors[i];
            sumDx += s.dx();
            sumDy += s.dy();
            if (errors[i] > worst.error()) {
               worst = s;
            }
         }
         final int n = detected.size();
         Arrays.sort(errors);
         final double median = n % 2 == 1 ? errors[n / 2] : (errors[n / 2 - 1] + errors[n / 2]) / 2;
         sb.append("Distance between target and measured spot:\n");
         sb.append("  mean:   ").append(pixAndUm(sumErr / n)).append("\n");
         sb.append("  RMS:    ").append(pixAndUm(Math.sqrt(sumSq / n))).append("\n");
         sb.append("  median: ").append(pixAndUm(median)).append("\n");
         sb.append("  max:    ").append(pixAndUm(worst.error()))
               .append(String.format(Locale.US, "  at target (%.1f, %.1f)%n",
                     worst.target.x, worst.target.y));
         // a consistent offset points at drift (stage, focus, alignment) since calibration
         sb.append(String.format(Locale.US, "Mean offset (systematic shift): dx = %.2f px, "
               + "dy = %.2f px%n%n", sumDx / n, sumDy / n));
      }
      sb.append(String.format(Locale.US, "%4s %9s %9s %10s %10s %8s %8s %8s%n",
            "#", "target x", "target y", "measured x", "measured y", "dx", "dy", "dist"));
      for (int i = 0; i < spots_.size(); i++) {
         Spot s = spots_.get(i);
         if (s.measured == null) {
            sb.append(String.format(Locale.US, "%4d %9.1f %9.1f %10s %10s%n",
                  i + 1, s.target.x, s.target.y, "not found", ""));
         } else {
            sb.append(String.format(Locale.US, "%4d %9.1f %9.1f %10.1f %10.1f %8.2f %8.2f %8.2f%n",
                  i + 1, s.target.x, s.target.y, s.measured.x, s.measured.y,
                  s.dx(), s.dy(), s.error()));
         }
      }
      return sb.toString();
   }
}

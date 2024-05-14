package org.micromanager.autofocus.optimizers;

import ij.process.ImageProcessor;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.DoubleVector;
import mmcorej.TaggedImage;
import org.jfree.data.xy.XYSeries;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.imageprocessing.curvefit.Fitter;
import org.micromanager.imageprocessing.curvefit.PlotUtils;

public class ZStackFocusOptimizer implements FocusOptimizer {
   private final Function<ImageProcessor, Double> imgScoringFunction_;
   private Studio studio_;
   private String zDrive_;
   private boolean displayImages_ = false;
   private boolean displayGraph_ = false;
   private double searchRangeUm_ = 10.0; //
   private double absoluteToleranceUm_ = 1.0; // abuse the tolerance setting as Z step size
   private int imageCount_ = 0;

   /**
    * The constructor takes a function that calculates a focus score.
    *
    * @param imgScoringFunction A function that takes an ImageJ `ImageProcessor`
    *                           and returns a double indicating a measure of the
    *                           image sharpness. A large value indicates a sharper image.
    */
   public ZStackFocusOptimizer(Function<ImageProcessor, Double> imgScoringFunction) {
      imgScoringFunction_ = imgScoringFunction;
   }

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public void setZDrive(String driveName) {
      zDrive_ = driveName;
   }

   @Override
   public void setDisplayImages(boolean display) {
      displayImages_ = display;
   }

   @Override
   public boolean getDisplayImages() {
      return displayImages_;
   }

   @Override
   public int getImageCount() {
      return imageCount_;
   }

   @Override
   public void setSearchRange(double searchRangeUm) {
      searchRangeUm_ = searchRangeUm;
   }

   @Override
   public double getSearchRange() {
      return searchRangeUm_;
   }

   @Override
   public void setAbsoluteTolerance(double tolerance) {
      absoluteToleranceUm_ = tolerance;
   }

   @Override
   public double getAbsoluteTolerance() {
      return absoluteToleranceUm_;
   }

   public void setDisplayGraph(boolean display) {
      displayGraph_ = display;
   }

   /**
    * Acquires a Z-stack of images and computes the focus score for each image.
    * The Z position with the highest focus score is returned.
    * When the stage supports it, images are acquired as a sequence.
    *
    * @return Z position at the higher focus score.
    * @throws Exception A common exception is failure to set the Z position in the hardware
    */
   @Override
   public double runAutofocusAlgorithm() throws Exception {

      imageCount_ = 0;

      CMMCore core = studio_.getCMMCore();
      if (zDrive_.isEmpty()) {
         zDrive_ = core.getFocusDevice();
      }
      // start position
      double z = core.getPosition(zDrive_);
      double dz = searchRangeUm_ / 2;
      int nrZ = (int) (searchRangeUm_ / absoluteToleranceUm_);
      core.setPosition(z - dz);
      DoubleVector positions = new DoubleVector();
      for (int i = 0; i < nrZ; i++) {
         positions.add(z - dz + i * absoluteToleranceUm_);
      }
      SortedMap<Double, Double> focusScoreMap = new TreeMap<>();

      if (core.isStageSequenceable(zDrive_)) {
         core.loadStageSequence(zDrive_, positions);
         core.startSequenceAcquisition(nrZ, 0, true);
         core.waitForDevice(zDrive_);
         core.waitForDevice(core.getCameraDevice());
         while (core.isSequenceRunning() || core.getRemainingImageCount() > 0) {
            if (!(core.getRemainingImageCount() > 0)) {
               Thread.sleep((long) core.getExposure());
            } else {
               TaggedImage tImg = core.popNextTaggedImage();
               Image img = studio_.data().convertTaggedImage(tImg);
               if (displayImages_) {
                  SwingUtilities.invokeLater(() -> {
                     studio_.live().displayImage(img);
                  });
               }
               ImageProcessor proc = studio_.data().ij().createProcessor(img);
               focusScoreMap.put(positions.get(imageCount_), imgScoringFunction_.apply(proc));
               imageCount_++;
            }
         }
      } else {
         for (int i = 0; i < nrZ; i++) {
            core.setPosition(zDrive_, positions.get(i));
            core.waitForDevice(zDrive_);
            Image img = studio_.live().snap(displayImages_).get(0);
            if (img == null) {
               throw new Exception("Failed to acquire image.");
            }
            ImageProcessor proc = studio_.data().ij().createProcessor(img);
            focusScoreMap.put(positions.get(imageCount_), imgScoringFunction_.apply(proc));
            imageCount_++;
         }
      }

      // we have the map relating Z stage positions to Focus Score, now fit to find the
      // optimum position.

      XYSeries xySeries = new XYSeries("Focus Score");
      focusScoreMap.forEach(xySeries::add);
      double[] guess = {z, focusScoreMap.get(positions.get(imageCount_ / 2))};
      double[] fit = Fitter.fit(xySeries, Fitter.FunctionType.Gaussian, guess);
      double newZ = Fitter.getXofMaxY(xySeries, Fitter.FunctionType.Gaussian, fit);
      if (displayGraph_) {
         XYSeries xySeriesFitted = Fitter.getFittedSeries(xySeries, Fitter.FunctionType.Gaussian, fit);
         XYSeries[] data = {xySeries, xySeriesFitted};
         boolean[] shapes = {true, false};
         PlotUtils pu = new PlotUtils(studio_);
         pu.plotDataN("Focus Score", data, "z position", "Focus Score", shapes, "", newZ);
      }
      return newZ;
   }

}

package org.micromanager.projector.internal;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;

/**
 * Loads a dataset written by CollectFindSpotData.bsh: one Multipage TIFF per projector
 * position, page 0 = background (spot off), page 1 = spot on. The projector coordinates are
 * encoded in the file name ("..._MMStack_x=30950.0,y=29450.0.ome.tif").
 */
final class HelperCalibrationSpotData {

   private static final Pattern POS_PATTERN =
         Pattern.compile("x=([-0-9.eE]+),y=([-0-9.eE]+)\\.ome\\.tif$");

   static final class Sample {
      final String name;
      final Point2D.Double projector;
      final ImageProcessor background;
      final ImageProcessor spot;
      final FloatProcessor diff;

      Sample(String name, Point2D.Double projector, ImageProcessor background,
            ImageProcessor spot) throws Exception {
         this.name = name;
         this.projector = projector;
         this.background = background;
         this.spot = spot;
         // Same subtraction as Calibrator.measureSpotOnCamera()
         this.diff = (FloatProcessor) ImageUtils.subtractImageProcessors(
               spot.convertToFloatProcessor(), background.convertToFloatProcessor());
      }
   }

   private HelperCalibrationSpotData() {
   }

   static List<Sample> load(File dir) throws Exception {
      File[] files = dir.listFiles((d, n) -> POS_PATTERN.matcher(n).find());
      if (files == null || files.length == 0) {
         throw new IllegalArgumentException("No spot TIFFs found in " + dir);
      }
      Arrays.sort(files);
      List<Sample> samples = new ArrayList<>();
      for (File f : files) {
         Matcher m = POS_PATTERN.matcher(f.getName());
         if (!m.find()) {
            continue;
         }
         Point2D.Double proj = new Point2D.Double(
               Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)));
         ImagePlus imp = new Opener().openImage(f.getAbsolutePath());
         if (imp == null) {
            throw new IllegalStateException("ImageJ could not open " + f);
         }
         ImageStack stack = imp.getStack();
         if (stack.getSize() < 2) {
            throw new IllegalStateException("Expected 2 planes (background, spot) in " + f
                  + " but found " + stack.getSize());
         }
         samples.add(new Sample(f.getName(), proj,
               stack.getProcessor(1), stack.getProcessor(2)));
      }
      return samples;
   }
}

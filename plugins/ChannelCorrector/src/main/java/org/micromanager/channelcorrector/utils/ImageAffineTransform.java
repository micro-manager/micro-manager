/*
 *
 */
package org.micromanager.channelcorrector.utils;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayWindow;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nico
 */
public class ImageAffineTransform {
   private final Studio studio_;
   private final DataViewer dataViewer_;
   private final ArrayList<AffineTransformOp> affineTransformOps_;
   private final int interpolationType_;
   private final Object renderingHint_;

   public ImageAffineTransform(Studio studio, DataViewer dataViewer,
                               ArrayList<AffineTransform> affineTransforms,
                               int interpolationType) {
      studio_ = studio;
      dataViewer_ = dataViewer;
      if (interpolationType != AffineTransformOp.TYPE_BICUBIC
              && interpolationType != AffineTransformOp.TYPE_BILINEAR
              && interpolationType != AffineTransformOp.TYPE_NEAREST_NEIGHBOR) {
         interpolationType = AffineTransformOp.TYPE_NEAREST_NEIGHBOR;
      }
      interpolationType_ = interpolationType;
      affineTransformOps_ = new ArrayList<>(affineTransforms.size());
      for (AffineTransform aff : affineTransforms) {
         affineTransformOps_.add(new AffineTransformOp(aff, interpolationType));
      }
      // Part of work-around bug in AffineTransformOp
      Object rh = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
      if (interpolationType_ == AffineTransformOp.TYPE_BICUBIC) {
         rh = RenderingHints.VALUE_INTERPOLATION_BICUBIC;
      } else if (interpolationType_ == AffineTransformOp.TYPE_BILINEAR) {
         rh = RenderingHints.VALUE_INTERPOLATION_BILINEAR;
      }
      renderingHint_ = rh;
   }

   public void apply(boolean allPositions) throws IOException, ImageAffineTransformException {
      final DataProvider dp = dataViewer_.getDataProvider();
      final int maxChan = dp.getNextIndex(Coords.C) - 1;
      if (maxChan !=  affineTransformOps_.size()) {
         studio_.logs().showError("Unexpected difference between viewer and affine transform data");
         return;
      }
      // Calculate eventual width and height
      Coords.Builder builder = Coordinates.builder().t(0).z(0).p(0).c(0);
      int minWidth = dp.getAnyImage().getWidth();
      int minHeight = dp.getAnyImage().getHeight();
      for (int c = 1; c <= maxChan; c++) {
         AffineTransformOp affineTransformOp = affineTransformOps_.get(c - 1);
         Image out = transformImage(dp.getImage(builder.c(c).build()),
                 affineTransformOp);
         if (out.getWidth() < minWidth) minWidth = out.getWidth();
         if (out.getHeight() < minHeight) minHeight = out.getHeight();
      }
      Datastore outStore = studio_.data().createRAMDatastore();
      List<Integer> positions = new ArrayList<>();
      String posString = "";
      Coords intendedDimensions = dp.getSummaryMetadata().getIntendedDimensions();
      if (allPositions) {
         for (int p = 0; p < dp.getNextIndex(Coords.P); p++) {
            positions.add(p);
         }
      } else {
         positions.add(dataViewer_.getDisplayPosition().getP());
         posString = "-pos" + dataViewer_.getDisplayPosition().getP();
         intendedDimensions = intendedDimensions.copyBuilder().p(1).build();
      }
      outStore.setName(dp.getName() + "-Corrected" + posString);
      outStore.setSummaryMetadata(dp.getSummaryMetadata().copyBuilder().
              intendedDimensions(intendedDimensions).build());
      DisplayWindow newDisplay = studio_.displays().createDisplay(outStore, null);
      newDisplay.setDisplaySettings(dataViewer_.getDisplaySettings());
      studio_.displays().manage(outStore);

      for (Integer  p : positions) {
         for (int t = 0; t < dp.getNextIndex(Coords.T); t++) {
            for (int z = 0; z < dp.getNextIndex(Coords.Z); z++) {
               // crop channel 0 image, and add to outStore
               int pos = p;
               if (!allPositions) {
                  pos = 0;
               }
               Image inImage = dp.getImage(builder.c(0).z(z).t(t).p(pos).build());
               outStore.putImage(crop(inImage, 0, 0, minWidth, minHeight));
               for (int c = 1; c < dp.getNextIndex(Coords.C); c++) {
                  // transform other channel to channel 0, crop to size and add to store
                  inImage = dp.getImage(builder.c(c).z(z).t(t).p(pos).build());
                  outStore.putImage(transformImage(inImage, affineTransformOps_.get(c-1), minWidth, minHeight));
               }
            }
         }
      }

   }

   public Image crop (Image inImg, int x, int y, int width, int height) throws ImageAffineTransformException {
      ImageProcessor ip = null;
      if (inImg.getBytesPerPixel() == 1) {
         ip = new ByteProcessor(
                 inImg.getWidth(), inImg.getHeight(), (byte[]) inImg.getRawPixels());
      } else
      if (inImg.getBytesPerPixel() == 2) {
         ip = new ShortProcessor(
                 inImg.getWidth(), inImg.getHeight() );
         ip.setPixels(inImg.getRawPixels());
      }
      if (ip != null) {
         ip.setRoi(new Roi(x, y, width, height));
         ImageProcessor copyIp = ip.crop();
         return studio_.data().createImage(copyIp.getPixels(),
                 copyIp.getWidth(), copyIp.getHeight(),
                 inImg.getBytesPerPixel(), inImg.getNumComponents(),
                 inImg.getCoords().copyBuilder().build(),
                 inImg.getMetadata().copyBuilderWithNewUUID().build());
      }
      throw new ImageAffineTransformException("Failed to crop image");
   }


   /**
    * Given an input image and affine transform, will apply the affine transform
    * to the image and returned the transformed image (which may be of a different size
    * from the input image
    * For now only works on 16 bit (short) images
    *
    * @param inImg             - input image
    * @param aOp                - affine transform operation that will be applied
    */
   public Image transformImage(Image inImg, AffineTransformOp aOp
                            ) throws ImageAffineTransformException {

      if (inImg.getBytesPerPixel() != 2) {
         throw new ImageAffineTransformException("ImageAffineTransform only works with 2 bytes per pixel");
      }

      ShortProcessor testProc = (ShortProcessor) studio_.data().ij().createProcessor(inImg);
      BufferedImage bi16 = testProc.get16BitBufferedImage();
      BufferedImage afBi16 = aOp.filter(bi16, null);
      ImagePlus p = new ImagePlus("", afBi16);

      return studio_.data().ij().createImage(p.getProcessor(), inImg.getCoords(), inImg.getMetadata());
   }

   /**
    * Uses the provided AffineTransform to transfrom the input image, then crops
    * it to given width and height
    *
    * @param inImg  Input Image to be transformed
    * @param aOp     AffineTransformOp to be applied
    * @param width   Width in pixels of result image
    * @param height  Height in pixels of result image
    * @return        Transformed Image
    * @throws ImageAffineTransformException Currently only thrown when input != 16 bit image
    */
   public Image transformImage(Image inImg, AffineTransformOp aOp, int width, int height
   ) throws ImageAffineTransformException {

      if (inImg.getBytesPerPixel() != 2) {
         throw new ImageAffineTransformException("ImageAffineTransform only works with 2 bytes per pixel");
      }

      ShortProcessor testProc = (ShortProcessor) studio_.data().ij().createProcessor(inImg);
      BufferedImage bi16 = testProc.get16BitBufferedImage();
      BufferedImage aOpResult = new BufferedImage(bi16.getWidth(),
              bi16.getHeight(), BufferedImage.TYPE_USHORT_GRAY);
      aOp.filter(bi16, aOpResult);
      if (interpolationType_ == AffineTransformOp.TYPE_NEAREST_NEIGHBOR) {
         aOp.filter(bi16, aOpResult);
      } else {
         // work around bug in AffineTransformationOp, see:
         // http://stackoverflow.com/questions/2428109/java-error-on-bilinear-interpolation-of-16-bit-data
         // Note: although the image looks alright, histogram values are strangely distorted
         // presumably, since calculations are done with bytes rather than shorts
         Graphics2D g = aOpResult.createGraphics();
         g.transform(aOp.getTransform());
         g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, renderingHint_);
         g.drawImage(bi16, null, 0, 0);
      }
      ImagePlus p = new ImagePlus("", aOpResult);
      ImageProcessor destProc = p.getProcessor();
      destProc.setRoi(0, 0, width, height);
      destProc = destProc.crop();

      return studio_.data().ij().createImage(destProc, inImg.getCoords(), inImg.getMetadata());
   }
}
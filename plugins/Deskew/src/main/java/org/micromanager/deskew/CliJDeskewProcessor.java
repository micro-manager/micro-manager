package org.micromanager.deskew;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import java.io.IOException;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.clearcl.ClearCLImage;
import net.haesleinhuepf.clij2.CLIJ2;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplayWindow;

public class CliJDeskewProcessor implements Processor {
   private final Studio studio_;
   private final Double theta_;
   private final CLIJ2 clij2_;
   private ImageStack stack = null;

   private SummaryMetadata inputSummaryMetadata_;

   public CliJDeskewProcessor(Studio studio, Double theta) {
      studio_ = studio;
      theta_ = theta;
      clij2_ = CLIJ2.getInstance();
      clij2_.clear(); // Really needed?
   }

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata source) {
      inputSummaryMetadata_ = source;
      return source;
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      Coords coordsNoZ = image.getCoords().copyRemovingAxes(Coords.Z);
      if (image.getCoords().getZ() == 0) {


         stack = new ImageStack(image.getWidth(), image.getHeight());
      }
      ImageProcessor ip = studio_.data().getImageJConverter().createProcessor(image);
      stack.addSlice(ip);

      if (image.getCoords().getZ() == inputSummaryMetadata_.getIntendedDimensions().getZ() - 1) {

         // calculate deskew step size
         double pxStep = inputSummaryMetadata_.getZStepUm();
         double pxSize = image.getMetadata().getPixelSizeUm();
         double pxDepth = pxStep * Math.sin(theta_);
         double pxDeskew = Math.sqrt(Math.pow(pxStep, 2) - Math.pow(pxDepth, 2));
         double deskewStep = pxDeskew / pxSize; // how many pixels to shear each slice by

         // calculate scaling factors
         double pxMin = Math.min(pxDepth, pxSize); // smallest pixel dimension
         double xyScale = pxSize / pxMin;
         double depthScale = pxDepth / pxMin;

         // pad skew dimension with zeros
         int imDepth = inputSummaryMetadata_.getIntendedDimensions().getZ();
         int newWidth = (int) Math.ceil(xyScale * image.getWidth());
         int newHeight = (int) Math.ceil(xyScale * (image.getHeight() + deskewStep * imDepth));
         int newDepth = (int) Math.ceil(depthScale * imDepth);

         // pre-determine how we need to crop the final image
         int slicesToRemove = (int) Math.floor(Math.max(0,
                  newDepth - Math.ceil(image.getHeight() * xyScale * Math.sin(theta_))));
         int rowsToRemove = (int) Math.floor(Math.max(0, newHeight - Math.ceil(image.getHeight()
                  * depthScale * Math.cos(theta_) + newDepth / Math.sin(theta_))));

         // calculate how big our final 3D stack should be
         int finalWidth = newWidth;
         int finalHeight = newHeight - 2 * (int) (Math.floor(rowsToRemove / 2.0));
         int finalDepth = newDepth - 2 * (int) (Math.floor(slicesToRemove / 2.0));

         // do the clij stuff
         ImagePlus imp = new ImagePlus("test", stack);
         ClearCLBuffer gpuImage = clij2_.push(imp);
         ClearCLBuffer image3 = clij2_.create(new long[] {finalWidth, finalHeight, finalDepth},
                  gpuImage.getNativeType());
         String transform = "shearYZ=-" + depthScale * deskewStep + " scaleX=" + xyScale
                  + " scaleY=" + xyScale + " scaleZ=" + depthScale + " -center rotateX=-"
                  + Math.toDegrees(theta_) + " center" + " translateZ=-"
                  + (int) ((0.5 * finalDepth));
         clij2_.affineTransform3D(gpuImage, image3, transform);

         ImagePlus resultImage = clij2_.pull(image3);
         ImageStack stack1 = resultImage.getStack();
         Datastore store = studio_.data().createRAMDatastore();
         try {
            for (int i = 0; i < stack1.getSize(); i++) {
               ImageProcessor ip1 = stack1.getProcessor(i + 1);
               Image image1 = studio_.data().ij().createImage(ip1,
                        coordsNoZ.copyBuilder().z(i).build(),
                        image.getMetadata());
               store.putImage(image1);
            }
            store.freeze();
         } catch (IOException e) {
            studio_.logs().logError(e);
         }
         DisplayWindow display = studio_.displays().createDisplay(store);
         display.show();
      }
   }
}

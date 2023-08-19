package org.micromanager.deskew;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;

public class CliJDeskewProcessor implements Processor {
   private final Studio studio_;
   private final Double theta_;
   private final CLIJ2 clij2_;
   private final boolean doFullVolume_;
   private final boolean doXYProjections_;
   private final String xyProjectionMode_;
   private final boolean doOrthogonalProjections_;
   private final String orthogonalProjectionsMode_;
   private final boolean keepOriginals_;
   private Datastore fullVolumeStore_;
   private Datastore xyProjectionStore_;
   private Datastore orthogonalStore_;
   private DisplaySettings displaySettings_;

   private SummaryMetadata inputSummaryMetadata_;
   private final Map<Coords, ImageStack> stacks_ = new HashMap<>();
   private final PropertyMap settings_;
   private Integer newDepth_;
   private Double newZSizeUm_;

   public CliJDeskewProcessor(Studio studio, String gpuName, Double theta, boolean doFullVolume,
            boolean doXYProjections, String xyProjectionMode,
            boolean doOrthogonalProjections, String orthogonalProjectionsMode,
            boolean keepOriginals, PropertyMap settings) {
      studio_ = studio;
      theta_ = theta;
      clij2_ = CLIJ2.getInstance(gpuName);
      clij2_.clear(); // Really needed?
      doFullVolume_ = doFullVolume;
      doXYProjections_ = doXYProjections;
      xyProjectionMode_ = xyProjectionMode;
      doOrthogonalProjections_ = doOrthogonalProjections;
      orthogonalProjectionsMode_ = orthogonalProjectionsMode;
      keepOriginals_ = keepOriginals;
      settings_ = settings;
   }

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata source) {
      inputSummaryMetadata_ = source;

      // complicated way to find the viewer that had this data
      List<DataViewer> dataViewers = studio_.displays().getAllDataViewers();
      for (DataViewer dv : dataViewers) {
         DataProvider provider = dv.getDataProvider();
         if (provider != null && provider.getSummaryMetadata() == source) {
            displaySettings_ = dv.getDisplaySettings();
         }
      }
      return source;
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      Coords coordsNoZPossiblyNoT = image.getCoords().copyRemovingAxes(Coords.Z);
      if (settings_.getString(DeskewFrame.OUTPUT_OPTION, "")
               .equals(DeskewFrame.OPTION_REWRITABLE_RAM)) {
         coordsNoZPossiblyNoT = coordsNoZPossiblyNoT.copyRemovingAxes(Coords.T);
      }
      if (image.getCoords().getZ() == 0) {
         stacks_.put(coordsNoZPossiblyNoT, new ImageStack(image.getWidth(), image.getHeight()));
      }
      ImageProcessor ip = studio_.data().getImageJConverter().createProcessor(image);
      if (stacks_.get(coordsNoZPossiblyNoT) != null) {
         stacks_.get(coordsNoZPossiblyNoT).addSlice(ip);
      }

      if (stacks_.get(coordsNoZPossiblyNoT) != null
               && image.getCoords().getZ()
                     == inputSummaryMetadata_.getIntendedDimensions().getZ() - 1) {
         try {
            ClearCLBuffer fullVolumeGPU = deskewAndRotateOnGPU(
                     stacks_.get(coordsNoZPossiblyNoT), image);
            stacks_.remove(coordsNoZPossiblyNoT);
            if (doXYProjections_) {
               ClearCLBuffer xy = projectXYOnGPU(fullVolumeGPU);
               ImagePlus resultImage = clij2_.pull(xy);
               clij2_.release(xy);
               Image projection = studio_.data().ij().createImage(resultImage.getProcessor(),
                       coordsNoZPossiblyNoT.copyBuilder().build(), image.getMetadata());
               if (xyProjectionStore_ == null) {
                  String newPrefix = inputSummaryMetadata_.getPrefix() + "-"
                           + (xyProjectionMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                           + "-Projection-GPU";
                  xyProjectionStore_ = DeskewFactory.createStoreAndDisplay(studio_,
                           settings_,
                           inputSummaryMetadata_,
                           newPrefix,
                           projection.getWidth(),
                           projection.getHeight(),
                           0,
                           null);
               }
               xyProjectionStore_.putImage(projection);
            }
            if (doOrthogonalProjections_) {
               ClearCLBuffer ortho = projectOrthogonalOnGPU(fullVolumeGPU);
               ImagePlus resultImage = clij2_.pull(ortho);
               clij2_.release(ortho);
               Image projection = studio_.data().ij().createImage(resultImage.getProcessor(),
                       coordsNoZPossiblyNoT.copyBuilder().build(), image.getMetadata());
               if (orthogonalStore_ == null) {
                  String newPrefix = inputSummaryMetadata_.getPrefix() + "-"
                           + (orthogonalProjectionsMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                           + "-Orthogonal-Projection-GPU";
                  orthogonalStore_ = DeskewFactory.createStoreAndDisplay(studio_,
                           settings_,
                           inputSummaryMetadata_,
                           newPrefix,
                           projection.getWidth(),
                           projection.getHeight(),
                           0,
                           null);
               }
               orthogonalStore_.putImage(projection);
            }
            if (doFullVolume_) {
               ImagePlus resultImage = clij2_.pull(fullVolumeGPU);
               ImageStack resultStack = resultImage.getStack();
               for (int i = 0; i < resultStack.getSize(); i++) {
                  ImageProcessor ip1 = resultStack.getProcessor(i + 1);
                  Image image1 = studio_.data().ij().createImage(ip1,
                           coordsNoZPossiblyNoT.copyBuilder().z(i).build(),
                           image.getMetadata());
                  if (fullVolumeStore_ == null) {
                     String newPrefix = inputSummaryMetadata_.getPrefix() + "-Full-Volume-GPU";
                     fullVolumeStore_ = DeskewFactory.createStoreAndDisplay(studio_,
                             settings_,
                             inputSummaryMetadata_,
                             newPrefix,
                             image1.getWidth(),
                             image1.getHeight(),
                             newDepth_,
                             newZSizeUm_);
                  }
                  fullVolumeStore_.putImage(image1);
               }
            }
            clij2_.release(fullVolumeGPU);
         } catch (IOException e) {
            studio_.logs().showError(e);
         }
      }
      // TODO: freeze all stores at the end...
   }

   private ClearCLBuffer deskewAndRotateOnGPU(ImageStack stack, Image image) {
      // calculate deskew step size
      int imDepth = inputSummaryMetadata_.getIntendedDimensions().getZ();
      double pxStep = inputSummaryMetadata_.getZStepUm();
      double pxSize = image.getMetadata().getPixelSizeUm();
      double pxDepth = pxStep * Math.sin(theta_);
      double pxDeskew = Math.sqrt(Math.pow(pxStep, 2) - Math.pow(pxDepth, 2));
      double deskewStep = pxDeskew / pxSize; // how many pixels to shear each slice by

      // calculate scaling factors
      double pxMin = Math.min(pxDepth, pxSize); // smallest pixel dimension
      double xyScale = pxSize / pxMin;
      double depthScale = pxDepth / pxMin;

      // destination image size
      int newWidth = (int) Math.ceil(xyScale * image.getWidth());
      int newHeight = (int) Math.ceil(xyScale * image.getHeight() * Math.cos(theta_)
               + imDepth * depthScale / Math.sin(theta_));
      int newDepth = (int) Math.ceil(xyScale * image.getHeight() * Math.sin(theta_));

      newDepth_ = newDepth;
      newZSizeUm_ = pxDepth;

      // do the clij stuff
      ImagePlus imp = new ImagePlus("test", stack);
      ClearCLBuffer gpuInputImage = clij2_.push(imp);
      ClearCLBuffer gpuOutputImage = clij2_.create(new long[] {newWidth, newHeight, newDepth},
               gpuInputImage.getNativeType());
      String transform = "shearYZ=-" + deskewStep + " scaleX=" + xyScale
               + " scaleY=" + xyScale + " scaleZ=" + depthScale
               + " rotateX=-" + Math.toDegrees(theta_)
               + " translateZ=-" + newDepth
               + " rotateX=180 translateZ=-" + newDepth + " translateY=-" + newHeight;

      clij2_.affineTransform3D(gpuInputImage, gpuOutputImage, transform);
      clij2_.release(gpuInputImage);

      return gpuOutputImage;
   }

   ClearCLBuffer projectXYOnGPU(ClearCLBuffer input) {
      // perform optional orthogonal projections
      ClearCLBuffer destination = clij2_.create(new long[]{input.getWidth(), input.getHeight()},
               input.getNativeType());
      clij2_.maximumZProjection(input, destination);
      return destination;
   }

   ClearCLBuffer projectOrthogonalOnGPU(ClearCLBuffer input) {
      // perform optional orthogonal projections
      ClearCLBuffer xy = clij2_.create(new long[]{input.getWidth(), input.getHeight()},
               input.getNativeType());
      clij2_.maximumZProjection(input, xy);
      ClearCLBuffer xz = clij2_.create(new long[]{input.getWidth(), input.getDepth()},
               input.getNativeType());
      clij2_.maximumYProjection(input, xz);
      ClearCLBuffer yz = clij2_.create(new long[]{input.getDepth(),
                        input.getHeight() + input.getDepth()},
               input.getNativeType());
      clij2_.maximumXProjection(input, yz);
      ClearCLBuffer xyXz = clij2_.create(new long[]{input.getWidth(),
                        input.getHeight() + input.getDepth()}, input.getNativeType());
      clij2_.combineVertically(xy, xz, xyXz);
      ClearCLBuffer xyXzYz = clij2_.create(new long[]{input.getWidth() + input.getDepth(),
                        input.getHeight() + input.getDepth()}, input.getNativeType());
      clij2_.combineHorizontally(xyXz, yz, xyXzYz);
      clij2_.release(xy);
      clij2_.release(xz);
      clij2_.release(yz);
      clij2_.release(xyXz);
      return xyXzYz;
   }

}
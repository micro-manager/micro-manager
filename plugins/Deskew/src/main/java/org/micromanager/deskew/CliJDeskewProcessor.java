package org.micromanager.deskew;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.clearcl.exceptions.OpenCLException;
import net.haesleinhuepf.clij2.CLIJ2;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.internal.utils.NumberUtils;

/**
 * Implements deskewing using CliJ on the GPU.
 */
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
   private final DeskewAcqManager deskewAcqManager_;
   private Datastore fullVolumeStore_;
   private Datastore xyProjectionStore_;
   private Datastore orthogonalStore_;
   private SummaryMetadata inputSummaryMetadata_;
   private final Map<Coords, ImageStack> stacks_ = new HashMap<>();
   private final PropertyMap settings_;
   private Integer newDepth_;
   private Double newZSizeUm_;

   /**
    * Implements deskewing using CliJ on the GPU.
    *
    * @param studio Always present Studio object
    * @param deskewAcqManager Parent DeskewAcqManager
    * @param settings PropertyMap with settings
    * @throws ParseException if angle is not a valid number
    */
   public CliJDeskewProcessor(Studio studio, DeskewAcqManager deskewAcqManager,
                              PropertyMap settings) throws ParseException {
      studio_ = studio;
      settings_ = settings;
      String gpuName = settings.getString(DeskewFrame.GPU, CLIJ2.getInstance().getGPUName());
      clij2_ = CLIJ2.getInstance(gpuName);
      clij2_.clear(); // Really needed?
      // this can throw a ParseException if the angle is not a valid number
      theta_ = Math.toRadians(NumberUtils.displayStringToDouble(settings_.getString(
               DeskewFrame.DEGREE, "60.0")));
      if (theta_ == 0.0) {
         studio_.logs().showError("Can not deskew LighSheet data with an angle of 0.0 degrees");
      }
      doFullVolume_ = settings_.getBoolean(DeskewFrame.FULL_VOLUME, true);
      doXYProjections_ = settings_.getBoolean(DeskewFrame.XY_PROJECTION, false);
      xyProjectionMode_ = settings_.getString(DeskewFrame.XY_PROJECTION_MODE,
               DeskewFrame.MAX);
      doOrthogonalProjections_ = settings_.getBoolean(DeskewFrame.ORTHOGONAL_PROJECTIONS,
               false);
      orthogonalProjectionsMode_ = settings_.getString(
               DeskewFrame.ORTHOGONAL_PROJECTIONS_MODE, DeskewFrame.MAX);
      keepOriginals_ = settings_.getBoolean(DeskewFrame.KEEP_ORIGINAL, true);

      deskewAcqManager_ = deskewAcqManager;
   }

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata source) {
      inputSummaryMetadata_ = source;
      // previously the code would find the viewer to this data to make a copy of the
      // displaysettings, but these were not being used...
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
            if (fullVolumeGPU == null) {
               return;
            }
            stacks_.remove(coordsNoZPossiblyNoT);
            if (doXYProjections_) {
               ClearCLBuffer xy = projectXYOnGPU(fullVolumeGPU);
               ImagePlus resultImage = clij2_.pull(xy);
               clij2_.release(xy);
               Image projection = studio_.data().ij().createImage(resultImage.getProcessor(),
                       coordsNoZPossiblyNoT.copyBuilder().build(), image.getMetadata());
               if (xyProjectionStore_ == null) {
                  String prefix = inputSummaryMetadata_.getPrefix().isEmpty()
                           ? "Untitled" : inputSummaryMetadata_.getPrefix();
                  String newPrefix = prefix + "-"
                           + (xyProjectionMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                           + "-Projection-GPU";
                  xyProjectionStore_ = deskewAcqManager_.createStoreAndDisplay(studio_,
                           settings_,
                           inputSummaryMetadata_,
                           DeskewAcqManager.ProjectionType.YX_PROJECTION,
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
                  String prefix = inputSummaryMetadata_.getPrefix().isEmpty()
                           ? "Untitled" : inputSummaryMetadata_.getPrefix();
                  String newPrefix = prefix + "-"
                           + (orthogonalProjectionsMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                           + "-Orthogonal-Projection-GPU";
                  orthogonalStore_ = deskewAcqManager_.createStoreAndDisplay(studio_,
                           settings_,
                           inputSummaryMetadata_,
                           DeskewAcqManager.ProjectionType.ORTHOGONAL_VIEWS,
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
                     String prefix = inputSummaryMetadata_.getPrefix().isEmpty()
                              ? "Untitled" : inputSummaryMetadata_.getPrefix();
                     String newPrefix = prefix + "-" + "-Full-Volume-GPU";
                     fullVolumeStore_ = deskewAcqManager_.createStoreAndDisplay(studio_,
                             settings_,
                             inputSummaryMetadata_,
                             DeskewAcqManager.ProjectionType.FULL_VOLUME,
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

      if (keepOriginals_) {
         context.outputImage(image);
      }
   }

   @Override
   public void cleanup(ProcessorContext context) {
      // TODO: shutdown processing executor?
      if (fullVolumeStore_ != null) {
         try {
            fullVolumeStore_.freeze();
            if (fullVolumeStore_.getNumImages() == 0) {
               deskewAcqManager_.closeViewerFor(fullVolumeStore_);
               fullVolumeStore_.close();
            }
         } catch (IOException e) {
            studio_.logs().logError(e);
         }
      }
      if (xyProjectionStore_ != null) {
         try {
            xyProjectionStore_.freeze();
            if (xyProjectionStore_.getNumImages() == 0) {
               deskewAcqManager_.closeViewerFor(xyProjectionStore_);
               xyProjectionStore_.close();
            }
         } catch (IOException e) {
            studio_.logs().logError(e);
         }
      }
      if (orthogonalStore_ != null) {
         try {
            orthogonalStore_.freeze();
            if (orthogonalStore_.getNumImages() == 0) {
               deskewAcqManager_.closeViewerFor(orthogonalStore_);
               orthogonalStore_.close();
            }
         } catch (IOException e) {
            studio_.logs().logError(e);
         }
      }
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
      int newWidth = Math.abs((int) Math.ceil(xyScale * image.getWidth()));
      int newHeight = (int) Math.ceil(xyScale * image.getHeight() * Math.cos(theta_)
               + imDepth * depthScale / Math.sin(theta_));
      int newDepth = Math.abs((int) Math.ceil(xyScale * image.getHeight() * Math.sin(theta_)));

      newDepth_ = newDepth;
      newZSizeUm_ = pxDepth;

      // check if image fits into GPU memory
      long maxClijImageSize = clij2_.getCLIJ().getClearCLContext().getDevice()
               .getMaxMemoryAllocationSizeInBytes();
      long estimatedSize = (long) newWidth * (long) newHeight * (long) newDepth
               * (long) image.getBytesPerPixel();
      if (estimatedSize > maxClijImageSize) {
         studio_.logs().showError("Deskewed image size of "
                  + humanReadableBytes(estimatedSize)
                  + " bytes exceeds maximum GPU memory allocation size of "
                  + humanReadableBytes(maxClijImageSize)
                  + " bytes on GPU " + clij2_.getCLIJ().getGPUName() + ".\n"
                  + "Please choose a different GPU with more memory or reduce the image size.");
         return null;
      }

      // do the clij stuff
      ImagePlus imp = new ImagePlus("test", stack);
      ClearCLBuffer gpuInputImage = null;
      ClearCLBuffer gpuOutputImage = null;
      try {
         gpuInputImage = clij2_.push(imp);
         gpuOutputImage = clij2_.create(new long[]{newWidth, newHeight, newDepth},
                 gpuInputImage.getNativeType());
         String transform = "shearYZ=-" + deskewStep + " scaleX=" + xyScale
                 + " scaleY=" + xyScale + " scaleZ=" + depthScale
                 + " rotateX=-" + Math.toDegrees(theta_)
                 + " translateZ=-" + newDepth
                 + " rotateX=180 translateZ=-" + newDepth + " translateY=-" + newHeight;

         clij2_.affineTransform3D(gpuInputImage, gpuOutputImage, transform);
      }  catch (OpenCLException oe) {
         studio_.logs().showError(oe.getMessage());
         if (gpuOutputImage != null) {
            clij2_.release(gpuOutputImage);
         }
      }  finally {
         if (gpuInputImage != null) {
            clij2_.release(gpuInputImage);
         }
      }

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

   private String humanReadableBytes(double numBytes) {
      String[] units = {"bytes", "kilobytes", "megabytes", "gigabytes", "terabytes"};
      int unitIndex = 0;
      while (numBytes > 1024.0 && unitIndex < units.length - 1) {
         numBytes /= 1024.0;
         unitIndex++;
      }
      double rounded = ((long) (numBytes * 10.0)) / 10.0;
      return rounded + " " + units[unitIndex];
   }

}
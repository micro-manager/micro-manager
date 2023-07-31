package org.micromanager.deskew;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.PixelType;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.DisplayWindow;
import org.micromanager.lightsheet.StackResampler;

/**
 * Deskews data using the Deskew code in PycroManager.
 */
public class DeskewProcessor implements Processor {
   private SummaryMetadata inputSummaryMetadata_;
   private final Studio studio_;
   private final double theta_;
   private final boolean doFullVolume_;
   private final boolean doXYProjections_;
   private final String xyProjectionMode_;
   private final boolean doOrthogonalProjections_;
   private final String orthogonalProjectionsMode_;
   private final boolean keepOriginals_;
   private StackResampler fullVolumeResampler_ = null;
   private StackResampler xyProjectionResampler_ = null;
   private StackResampler orthogonalProjectionResampler_ = null;

   private ExecutorService processingExecutor_ =
         new ThreadPoolExecutor(1, 12, 1000, TimeUnit.MILLISECONDS,
               new LinkedBlockingDeque<>());
   private HashMap<HashMap<String, Object>, Future> processingFutures_ = new HashMap<>();
   private HashMap<String, StackResampler> freeProcessors_ = new HashMap<>();
   private HashMap<HashMap<String, Object>, StackResampler> activeProcessors_ = new HashMap<>();
   private Future<?> f = null;

   /**
    * Bit of an awkard way to translate user's desires to the
    * Pycromanager deskew code.
    *
    * @param studio Micro-Manager Studio instance
    * @param theta Angle between the light sheet and the sample plane in radians.
    * @param doFullVolume Whether to generate a full volume.
    * @param doXYProjections Whether to generate XY Projections.
    * @param xyProjectionMode Max or average projection
    * @param doOrthogonalProjections Whether to generate orthogonal projections
    * @param orthogonalProjectionsMode Max or average projection
    * @param keepOriginals Whether to send the original data through the pipeline or
    *                      to drop them.
    */
   public DeskewProcessor(Studio studio, double theta, boolean doFullVolume,
                          boolean doXYProjections, String xyProjectionMode,
                          boolean doOrthogonalProjections, String orthogonalProjectionsMode,
                          boolean keepOriginals) {
      studio_ = studio;
      theta_ = theta;
      doFullVolume_ = doFullVolume;
      doXYProjections_ = doXYProjections;
      xyProjectionMode_ = xyProjectionMode;
      doOrthogonalProjections_ = doOrthogonalProjections;
      orthogonalProjectionsMode_ = orthogonalProjectionsMode;
      keepOriginals_ = keepOriginals;
   }

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata summaryMetadata) {
      inputSummaryMetadata_ = summaryMetadata;
      return inputSummaryMetadata_;
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      if (image.getCoords().getZ() == 1) {
         if (doFullVolume_) {
            fullVolumeResampler_ = new StackResampler(StackResampler.FULL_VOLUME, false, theta_,
                     image.getMetadata().getPixelSizeUm(), inputSummaryMetadata_.getZStepUm(),
                     inputSummaryMetadata_.getIntendedDimensions().getZ(), image.getWidth(),
                     image.getHeight());
            fullVolumeResampler_.initializeProjections();
         }
         if (doXYProjections_) {
            xyProjectionResampler_ = new StackResampler(StackResampler.YX_PROJECTION,
                     xyProjectionMode_.equals(DeskewFrame.MAX),
                     theta_,
                     image.getMetadata().getPixelSizeUm(),
                     inputSummaryMetadata_.getZStepUm(),
                     inputSummaryMetadata_.getIntendedDimensions().getZ(),
                     image.getWidth(),
                     image.getHeight());
            xyProjectionResampler_.initializeProjections();
         }
         if (doOrthogonalProjections_) {
            orthogonalProjectionResampler_ = new StackResampler(
                     StackResampler.ORTHOGONAL_VIEWS,
                     orthogonalProjectionsMode_.equals(DeskewFrame.MAX),
                     theta_,
                     image.getMetadata().getPixelSizeUm(),
                     inputSummaryMetadata_.getZStepUm(),
                     inputSummaryMetadata_.getIntendedDimensions().getZ(),
                     image.getWidth(),
                     image.getHeight());
            orthogonalProjectionResampler_.initializeProjections();
            f = processingExecutor_.submit(orthogonalProjectionResampler_.startStackProcessing());
            //processingFutures_.put(nonZAxes, f);
         }
      }
      if (fullVolumeResampler_ != null) {
         fullVolumeResampler_.addImageToRecons((short[]) image.getRawPixels(),
                  image.getCoords().getZ());
      }
      if (xyProjectionResampler_ != null) {
         xyProjectionResampler_.addImageToRecons((short[]) image.getRawPixels(),
                  image.getCoords().getZ());
      }
      if (orthogonalProjectionResampler_ != null) {
         //orthogonalProjectionResampler_.addImageToRecons((short[]) image.getRawPixels(),
         //         image.getCoords().getZ());
         orthogonalProjectionResampler_.addToProcessImageQueue((short[]) image.getRawPixels(),
                     image.getCoords().getZ());
      }

      if (image.getCoords().getZ() == inputSummaryMetadata_.getIntendedDimensions().getZ() - 1) {
         if (fullVolumeResampler_ != null) {
            fullVolumeResampler_.finalizeProjections();
            int width = fullVolumeResampler_.getResampledShapeX();
            int height = fullVolumeResampler_.getResampledShapeY();
            int nrZPlanes = fullVolumeResampler_.getResampledShapeZ();
            double newZStep = fullVolumeResampler_.getReconstructionVoxelSizeUm();
            Datastore outputStore =  studio_.data().createRAMDatastore();
            SummaryMetadata outputSummaryMetadata = inputSummaryMetadata_.copyBuilder()
                     .zStepUm(newZStep).intendedDimensions(inputSummaryMetadata_
                              .getIntendedDimensions().copyBuilder().z(nrZPlanes).build())
                     .prefix(inputSummaryMetadata_.getPrefix() + "-Deskewed")
                     .build();
            PropertyMap.Builder formatBuilder = PropertyMaps.builder();
            formatBuilder.putInteger(PropertyKey.WIDTH.key(), width);
            formatBuilder.putInteger(PropertyKey.HEIGHT.key(), height);
            formatBuilder.putString(PropertyKey.PIXEL_TYPE.key(), PixelType.GRAY16.toString());
            PropertyMap format = formatBuilder.build();
            Coords.CoordsBuilder cb = studio_.data().coordsBuilder();
            try {
               outputStore.setSummaryMetadata(outputSummaryMetadata);
               short[][] reconstructedVolume = fullVolumeResampler_.getReconstructedVolumeZYX();
               for (int z = 0; z < reconstructedVolume.length; z++) {
                  Image img = new DefaultImage(reconstructedVolume[z], format, cb.z(z).build(),
                           image.getMetadata().copyBuilderWithNewUUID().build());
                  outputStore.putImage(img);
               }
               DisplayWindow display = studio_.displays().createDisplay(outputStore);
               display.setCustomTitle(inputSummaryMetadata_.getPrefix() + "-Deskewed");
               display.show();
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
         if (xyProjectionResampler_ != null) {
            xyProjectionResampler_.finalizeProjections();
            int width = xyProjectionResampler_.getResampledShapeX();
            int height = xyProjectionResampler_.getResampledShapeY();
            int nrZPlanes = 1;
            Datastore outputStore = studio_.data().createRAMDatastore();
            String newPrefix = inputSummaryMetadata_.getPrefix() + "-"
                  + (xyProjectionMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                  + "-Projection";
            SummaryMetadata outputSummaryMetadata = inputSummaryMetadata_.copyBuilder()
                     .intendedDimensions(inputSummaryMetadata_
                              .getIntendedDimensions().copyBuilder().z(nrZPlanes).build())
                     .prefix(newPrefix)
                     .build();
            PropertyMap.Builder formatBuilder = PropertyMaps.builder();
            formatBuilder.putInteger(PropertyKey.WIDTH.key(), width);
            formatBuilder.putInteger(PropertyKey.HEIGHT.key(), height);
            formatBuilder.putString(PropertyKey.PIXEL_TYPE.key(), PixelType.GRAY16.toString());
            PropertyMap format = formatBuilder.build();
            Coords.CoordsBuilder cb = studio_.data().coordsBuilder();
            try {
               outputStore.setSummaryMetadata(outputSummaryMetadata);
               short[] yxProjection = xyProjectionResampler_.getYXProjection();
               Image img = new DefaultImage(yxProjection, format, cb.build(),
                        image.getMetadata().copyBuilderWithNewUUID().build());
               outputStore.putImage(img);
               DisplayWindow display = studio_.displays().createDisplay(outputStore);
               display.setCustomTitle(newPrefix);
               display.show();
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
         if (orthogonalProjectionResampler_ != null) {
            if (f != null) {
               try {
                  f.get();
               } catch (InterruptedException e) {
                  throw new RuntimeException(e);
               } catch (ExecutionException e) {
                  throw new RuntimeException(e);
               }
            }
            orthogonalProjectionResampler_.finalizeProjections();
            int width = orthogonalProjectionResampler_.getResampledShapeX();
            int height = orthogonalProjectionResampler_.getResampledShapeY();
            int zSize = orthogonalProjectionResampler_.getResampledShapeZ();
            int separatorSize = 3;
            int newWidth = width + separatorSize + zSize;
            int newHeight = height + separatorSize + zSize;
            Datastore outputStore = studio_.data().createRAMDatastore();
            String newPrefix = inputSummaryMetadata_.getPrefix() + "-"
                  + (orthogonalProjectionsMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                  + "-Projection";
            SummaryMetadata outputSummaryMetadata = inputSummaryMetadata_.copyBuilder()
                     .intendedDimensions(inputSummaryMetadata_
                              .getIntendedDimensions().copyBuilder().z(1).build())
                     .prefix(newPrefix)
                     .build();
            PropertyMap.Builder formatBuilder = PropertyMaps.builder();
            formatBuilder.putInteger(PropertyKey.WIDTH.key(), newWidth);
            formatBuilder.putInteger(PropertyKey.HEIGHT.key(), newHeight);
            formatBuilder.putString(PropertyKey.PIXEL_TYPE.key(), PixelType.GRAY16.toString());
            PropertyMap format = formatBuilder.build();
            Coords.CoordsBuilder cb = studio_.data().coordsBuilder();
            try {
               outputStore.setSummaryMetadata(outputSummaryMetadata);
               short[] yxProjection = orthogonalProjectionResampler_.getYXProjection();
               short[] yzProjection = orthogonalProjectionResampler_.getYZProjection();
               short[] zxProjection = orthogonalProjectionResampler_.getZXProjection();
               short[] orthogonalView = new short[newWidth * newHeight];
               for (int row = 0; row < height; row++) {
                  System.arraycopy(yxProjection, row * width, orthogonalView,
                           row * newWidth, width);
                  System.arraycopy(yzProjection, row * zSize, orthogonalView,
                          (row * newWidth) + (width + separatorSize),
                         zSize);
               }
               int offset = (height + separatorSize) * newWidth;
               for (int z = 0; z < zSize; z++) {
                  System.arraycopy(zxProjection, z * width, orthogonalView,
                           offset + (z * newWidth),
                           width);
               }

               Image img = new DefaultImage(orthogonalView, format, cb.build(),
                        image.getMetadata().copyBuilderWithNewUUID().build());
               outputStore.putImage(img);
               DisplayWindow display = studio_.displays().createDisplay(outputStore);
               display.setCustomTitle(newPrefix);
               display.show();
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
      }

      if (keepOriginals_) {
         context.outputImage(image);
      }

   }

   @Override
   public void cleanup(ProcessorContext context) {
   }
}

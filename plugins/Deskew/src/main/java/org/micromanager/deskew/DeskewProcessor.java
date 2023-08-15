package org.micromanager.deskew;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.micromanager.data.RewritableDatastore;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.PixelType;
import org.micromanager.data.internal.PropertyKey;
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
   private final PropertyMap settings_;

   private final ExecutorService processingExecutor_;
   private final Map<Coords, StackResampler> fullVolumeResamplers_ = new HashMap<>();
   private final List<StackResampler> freeFullVolumeResamplers_ = new ArrayList<>();
   private final Map<Coords, StackResampler> xyProjectionResamplers_ = new HashMap<>();
   private final List<StackResampler> freeXYProjectionResamplers_ = new ArrayList<>();
   private final Map<Coords, StackResampler> orthogonalProjectionResamplers_ = new HashMap<>();
   private final List<StackResampler> freeOrthogonalProjectionResamplers_ = new ArrayList<>();
   private final Map<Coords, Future<?>> fullVolumeFutures_ = new HashMap<>();
   private final Map<Coords, Future<?>> xyProjectionFutures_ = new HashMap<>();
   private final Map<Coords, Future<?>> orthogonalFutures_ = new HashMap<>();
   private Datastore fullVolumeStore_;
   private Datastore xyProjectionStore_;
   private Datastore orthogonalStore_;

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
                          boolean keepOriginals, PropertyMap settings) {
      studio_ = studio;
      theta_ = theta;
      doFullVolume_ = doFullVolume;
      doXYProjections_ = doXYProjections;
      xyProjectionMode_ = xyProjectionMode;
      doOrthogonalProjections_ = doOrthogonalProjections;
      orthogonalProjectionsMode_ = orthogonalProjectionsMode;
      keepOriginals_ = keepOriginals;
      processingExecutor_ =
               new ThreadPoolExecutor(1, settings.getInteger(DeskewFrame.NR_THREADS, 12),
                        1000, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>());
      settings_ = settings;
   }

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata summaryMetadata) {
      inputSummaryMetadata_ = summaryMetadata;
      return inputSummaryMetadata_;
   }


   @Override
   public void processImage(Image image, ProcessorContext context) {
      Coords coordsNoZPossiblyNoT = image.getCoords().copyRemovingAxes(Coords.Z);
      if (settings_.getString(DeskewFrame.OUTPUT_OPTION, "")
               .equals(DeskewFrame.OPTION_REWRITABLE_RAM)) {
         coordsNoZPossiblyNoT = coordsNoZPossiblyNoT.copyRemovingAxes(Coords.T);
      }
      if (inputSummaryMetadata_ == null) { // seems possible in asynchronous context
         inputSummaryMetadata_ = context.getSummaryMetadata();
      }
      if (image.getCoords().getZ() == 0) {
         try {
            if (doFullVolume_) {
               if (fullVolumeResamplers_.get(coordsNoZPossiblyNoT) == null) {
                  if (freeFullVolumeResamplers_.isEmpty()) {
                     fullVolumeResamplers_.put(coordsNoZPossiblyNoT, new StackResampler(
                              StackResampler.FULL_VOLUME,
                              false,
                              theta_,
                              image.getMetadata().getPixelSizeUm(),
                              inputSummaryMetadata_.getZStepUm(),
                              inputSummaryMetadata_.getIntendedDimensions().getZ(),
                              image.getHeight(),
                              image.getWidth()));
                  } else {
                     fullVolumeResamplers_.put(coordsNoZPossiblyNoT,
                              freeFullVolumeResamplers_.remove(0));
                  }
               }
               fullVolumeResamplers_.get(coordsNoZPossiblyNoT).initializeProjections();
               fullVolumeFutures_.put(image.getCoords().copyBuilder().z(0).build(),
                        processingExecutor_.submit(fullVolumeResamplers_.get(coordsNoZPossiblyNoT)
                                 .startStackProcessing()));
               double newZStep = fullVolumeResamplers_.get(
                        coordsNoZPossiblyNoT).getReconstructionVoxelSizeUm();
               int width = fullVolumeResamplers_.get(coordsNoZPossiblyNoT).getResampledShapeX();
               int height = fullVolumeResamplers_.get(coordsNoZPossiblyNoT).getResampledShapeY();
               if (fullVolumeStore_ == null) {
                  String newPrefix = inputSummaryMetadata_.getPrefix() + "-Full-Volume-CPU";
                  fullVolumeStore_ = DeskewFactory.createStoreAndDisplay(studio_,
                           settings_,
                           inputSummaryMetadata_,
                           newPrefix,
                           width,
                           height,
                           fullVolumeResamplers_.get(
                                    coordsNoZPossiblyNoT).getResampledShapeZ(),
                           newZStep);
               }
            }
            if (doXYProjections_) {
               if (xyProjectionResamplers_.get(coordsNoZPossiblyNoT) == null) {
                  if (freeXYProjectionResamplers_.isEmpty()) {
                     xyProjectionResamplers_.put(coordsNoZPossiblyNoT, new StackResampler(
                              StackResampler.YX_PROJECTION,
                              xyProjectionMode_.equals(DeskewFrame.MAX),
                              theta_,
                              image.getMetadata().getPixelSizeUm(),
                              inputSummaryMetadata_.getZStepUm(),
                              inputSummaryMetadata_.getIntendedDimensions().getZ(),
                              image.getHeight(),
                              image.getWidth()));
                  } else {
                     xyProjectionResamplers_.put(coordsNoZPossiblyNoT,
                              freeXYProjectionResamplers_.remove(0));
                  }
               }
               xyProjectionResamplers_.get(coordsNoZPossiblyNoT).initializeProjections();
               xyProjectionFutures_.put(image.getCoords().copyBuilder().z(0).build(),
                        processingExecutor_.submit(xyProjectionResamplers_.get(coordsNoZPossiblyNoT)
                                 .startStackProcessing()));
               if (xyProjectionStore_ == null) {
                  int width = xyProjectionResamplers_.get(
                          coordsNoZPossiblyNoT).getResampledShapeX();
                  int height = xyProjectionResamplers_.get(
                          coordsNoZPossiblyNoT).getResampledShapeY();
                  String newPrefix = inputSummaryMetadata_.getPrefix() + "-"
                           + (xyProjectionMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                           + "-Projection-CPU";
                  xyProjectionStore_ = DeskewFactory.createStoreAndDisplay(studio_,
                           settings_,
                           inputSummaryMetadata_,
                           newPrefix,
                           width,
                           height,
                           0,
                           null);
               }
            }
            if (doOrthogonalProjections_) {
               if (orthogonalProjectionResamplers_.get(coordsNoZPossiblyNoT) == null) {
                  if (freeOrthogonalProjectionResamplers_.size() > 0) {
                     orthogonalProjectionResamplers_.put(coordsNoZPossiblyNoT,
                              freeOrthogonalProjectionResamplers_.get(0));
                     freeOrthogonalProjectionResamplers_.remove(0);
                  } else {
                     orthogonalProjectionResamplers_.put(coordsNoZPossiblyNoT, new StackResampler(
                              StackResampler.ORTHOGONAL_VIEWS,
                              orthogonalProjectionsMode_.equals(DeskewFrame.MAX),
                              theta_,
                              image.getMetadata().getPixelSizeUm(),
                              inputSummaryMetadata_.getZStepUm(),
                              inputSummaryMetadata_.getIntendedDimensions().getZ(),
                              image.getHeight(),
                              image.getWidth()));
                  }
               }
               orthogonalProjectionResamplers_.get(coordsNoZPossiblyNoT).initializeProjections();
               orthogonalFutures_.put(coordsNoZPossiblyNoT,
                        processingExecutor_.submit(
                                 orthogonalProjectionResamplers_.get(coordsNoZPossiblyNoT)
                                          .startStackProcessing()));
               if (orthogonalStore_ == null) {
                  String newPrefix = inputSummaryMetadata_.getPrefix() + "-"
                           + (orthogonalProjectionsMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                           + "-Orthogonal-Projection-CPU";
                  int width = orthogonalProjectionResamplers_.get(
                          coordsNoZPossiblyNoT).getResampledShapeX();
                  int height = orthogonalProjectionResamplers_.get(
                          coordsNoZPossiblyNoT).getResampledShapeY();
                  int zSize = orthogonalProjectionResamplers_.get(
                          coordsNoZPossiblyNoT).getResampledShapeZ();
                  int separatorSize = 3;
                  int newWidth = width + separatorSize + zSize;
                  int newHeight = height + separatorSize + zSize;
                  orthogonalStore_ = DeskewFactory.createStoreAndDisplay(studio_,
                           settings_,
                           inputSummaryMetadata_,
                           newPrefix,
                           newWidth,
                           newHeight,
                           0,
                           null);
               }
            }
         } catch (IOException e) {
            studio_.logs().showError(e);
            throw new RuntimeException(e);
         }
      }
      if (fullVolumeResamplers_.get(coordsNoZPossiblyNoT) != null) {
         fullVolumeResamplers_.get(coordsNoZPossiblyNoT).addToProcessImageQueue(
                  (short[]) image.getRawPixels(), image.getCoords().getZ());
      }
      if (xyProjectionResamplers_.get(coordsNoZPossiblyNoT) != null) {
         xyProjectionResamplers_.get(coordsNoZPossiblyNoT).addToProcessImageQueue(
                  (short[]) image.getRawPixels(), image.getCoords().getZ());
      }
      if (orthogonalProjectionResamplers_.get(coordsNoZPossiblyNoT) != null) {
         orthogonalProjectionResamplers_.get(coordsNoZPossiblyNoT).addToProcessImageQueue(
                  (short[]) image.getRawPixels(), image.getCoords().getZ());
      }

      if (image.getCoords().getZ() == inputSummaryMetadata_.getIntendedDimensions().getZ() - 1) {
         if (fullVolumeResamplers_.get(coordsNoZPossiblyNoT) != null) {
            Future<?> future = fullVolumeFutures_.getOrDefault(coordsNoZPossiblyNoT, null);
            if (future != null) {
               try {
                  future.get();
               } catch (InterruptedException | ExecutionException | OutOfMemoryError e) {
                  throw new RuntimeException(e);
               }
            }
            fullVolumeResamplers_.get(coordsNoZPossiblyNoT).finalizeProjections();
            int width = fullVolumeResamplers_.get(coordsNoZPossiblyNoT).getResampledShapeX();
            int height = fullVolumeResamplers_.get(coordsNoZPossiblyNoT).getResampledShapeY();
            PropertyMap.Builder formatBuilder = PropertyMaps.builder();
            formatBuilder.putInteger(PropertyKey.WIDTH.key(), width);
            formatBuilder.putInteger(PropertyKey.HEIGHT.key(), height);
            formatBuilder.putString(PropertyKey.PIXEL_TYPE.key(), PixelType.GRAY16.toString());
            PropertyMap format = formatBuilder.build();
            Coords.CoordsBuilder cb = image.getCoords().copyBuilder();
            if (settings_.getString(DeskewFrame.OUTPUT_OPTION, "")
                    .equals(DeskewFrame.OPTION_REWRITABLE_RAM)) {
               cb.time(0);
            }
            try {
               short[][] reconstructedVolume = fullVolumeResamplers_.get(coordsNoZPossiblyNoT)
                        .getReconstructedVolumeZYX();
               for (int z = 0; z < reconstructedVolume.length; z++) {
                  Image img = new DefaultImage(reconstructedVolume[z], format, cb.z(z).build(),
                           image.getMetadata().copyBuilderWithNewUUID().build());
                  fullVolumeStore_.putImage(img);
               }
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
            freeFullVolumeResamplers_.add(fullVolumeResamplers_.get(coordsNoZPossiblyNoT));
            fullVolumeResamplers_.remove(coordsNoZPossiblyNoT);
         }
         if (xyProjectionResamplers_.get(coordsNoZPossiblyNoT) != null) {
            Future<?> future = xyProjectionFutures_.getOrDefault(coordsNoZPossiblyNoT, null);
            if (future != null) {
               try {
                  future.get();
               } catch (InterruptedException | ExecutionException e) {
                  throw new RuntimeException(e);
               }
            }
            xyProjectionResamplers_.get(coordsNoZPossiblyNoT).finalizeProjections();
            int width = xyProjectionResamplers_.get(coordsNoZPossiblyNoT).getResampledShapeX();
            int height = xyProjectionResamplers_.get(coordsNoZPossiblyNoT).getResampledShapeY();
            PropertyMap.Builder formatBuilder = PropertyMaps.builder();
            formatBuilder.putInteger(PropertyKey.WIDTH.key(), width);
            formatBuilder.putInteger(PropertyKey.HEIGHT.key(), height);
            formatBuilder.putString(PropertyKey.PIXEL_TYPE.key(), PixelType.GRAY16.toString());
            PropertyMap format = formatBuilder.build();
            try {
               short[] yxProjection = xyProjectionResamplers_.get(
                        coordsNoZPossiblyNoT).getYXProjection();
               Image img = new DefaultImage(yxProjection, format, coordsNoZPossiblyNoT,
                        image.getMetadata().copyBuilderWithNewUUID().build());
               xyProjectionStore_.putImage(img);
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
            freeXYProjectionResamplers_.add(xyProjectionResamplers_.get(coordsNoZPossiblyNoT));
            xyProjectionResamplers_.remove(coordsNoZPossiblyNoT);
         }
         if (orthogonalProjectionResamplers_.get(coordsNoZPossiblyNoT) != null) {
            Future<?> future = orthogonalFutures_.getOrDefault(
                     image.getCoords().copyBuilder().z(0).build(), null);
            if (future != null) {
               try {
                  future.get();
               } catch (InterruptedException | ExecutionException e) {
                  throw new RuntimeException(e);
               }
            }
            orthogonalProjectionResamplers_.get(coordsNoZPossiblyNoT).finalizeProjections();
            int width = orthogonalProjectionResamplers_.get(
                     coordsNoZPossiblyNoT).getResampledShapeX();
            int height = orthogonalProjectionResamplers_.get(
                     coordsNoZPossiblyNoT).getResampledShapeY();
            int zSize = orthogonalProjectionResamplers_.get(
                     coordsNoZPossiblyNoT).getResampledShapeZ();
            int separatorSize = 3;
            int newWidth = width + separatorSize + zSize;
            int newHeight = height + separatorSize + zSize;
            PropertyMap.Builder formatBuilder = PropertyMaps.builder();
            formatBuilder.putInteger(PropertyKey.WIDTH.key(), newWidth);
            formatBuilder.putInteger(PropertyKey.HEIGHT.key(), newHeight);
            formatBuilder.putString(PropertyKey.PIXEL_TYPE.key(), PixelType.GRAY16.toString());
            PropertyMap format = formatBuilder.build();
            try {
               short[] yxProjection = orthogonalProjectionResamplers_.get(coordsNoZPossiblyNoT)
                        .getYXProjection();
               short[] yzProjection = orthogonalProjectionResamplers_.get(coordsNoZPossiblyNoT)
                        .getYZProjection();
               short[] zxProjection = orthogonalProjectionResamplers_.get(coordsNoZPossiblyNoT)
                        .getZXProjection();
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

               Image img = new DefaultImage(orthogonalView, format, coordsNoZPossiblyNoT,
                        image.getMetadata().copyBuilderWithNewUUID().build());
               orthogonalStore_.putImage(img);
               freeOrthogonalProjectionResamplers_.add(orthogonalProjectionResamplers_
                        .get(coordsNoZPossiblyNoT));
               orthogonalProjectionResamplers_.remove(coordsNoZPossiblyNoT);
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
      }

      if (keepOriginals_) {
         context.outputImage(image);
      }

   }

}

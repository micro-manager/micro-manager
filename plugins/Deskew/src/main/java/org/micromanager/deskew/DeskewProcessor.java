package org.micromanager.deskew;

import java.io.IOException;
import java.text.ParseException;
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
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.PixelType;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.NumberUtils;
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
   private final DeskewAcqManager deskewAcqManager_;
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
   private final List<DisplayWindow> testDisplayWindows_ = new ArrayList<>();
   private Datastore fullVolumeStore_;
   private Datastore xyProjectionStore_;
   private Datastore orthogonalStore_;

   /**
    * Bit of an awkard way to translate user's desires to the
    * Pycromanager deskew code.
    *
    * @param studio Micro-Manager Studio instance
    * @param deskewAcqManager DeskewAcqManager instance
    * @param settings PropertyMap with settings
    */
   public DeskewProcessor(Studio studio, DeskewAcqManager deskewAcqManager,
                          PropertyMap settings) throws ParseException {
      studio_ = studio;
      settings_ = settings;
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
      processingExecutor_ =
               new ThreadPoolExecutor(1,
                        settings.getInteger(DeskewFrame.NR_THREADS, 12),
                        1000,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingDeque<>());
      deskewAcqManager_ = deskewAcqManager;
   }

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata summaryMetadata) {
      inputSummaryMetadata_ = summaryMetadata;
      return inputSummaryMetadata_;
   }


   @Override
   public void processImage(Image image, ProcessorContext context) {
      Coords coordsNoZ = image.getCoords().copyRemovingAxes(Coords.Z);
      Coords coordsNoZPossiblyNoT = coordsNoZ;
      if (settings_.getString(DeskewFrame.OUTPUT_OPTION, "")
               .equals(DeskewFrame.OPTION_REWRITABLE_RAM)) {
         coordsNoZPossiblyNoT = coordsNoZ.copyRemovingAxes(Coords.T);
      }
      if (inputSummaryMetadata_ == null) { // seems possible in asynchronous context
         inputSummaryMetadata_ = context.getSummaryMetadata();
      }
      if (image.getCoords().getZ() == 0) {
         try {
            if (doFullVolume_) {
               if (fullVolumeResamplers_.get(coordsNoZ) == null) {
                  if (freeFullVolumeResamplers_.isEmpty()) {
                     fullVolumeResamplers_.put(coordsNoZ, new StackResampler(
                              StackResampler.FULL_VOLUME,
                              false,
                              theta_,
                              image.getMetadata().getPixelSizeUm(),
                              inputSummaryMetadata_.getZStepUm(),
                              inputSummaryMetadata_.getIntendedDimensions().getZ(),
                              image.getHeight(),
                              image.getWidth()));
                  } else {
                     fullVolumeResamplers_.put(coordsNoZ,
                              freeFullVolumeResamplers_.remove(0));
                  }
               }
               fullVolumeResamplers_.get(coordsNoZ).initializeProjections();
               fullVolumeFutures_.put(image.getCoords().copyBuilder().z(0).build(),
                        processingExecutor_.submit(fullVolumeResamplers_.get(coordsNoZ)
                                 .startStackProcessing()));
               double newZStep = fullVolumeResamplers_.get(
                        coordsNoZ).getReconstructionVoxelSizeUm();
               int width = fullVolumeResamplers_.get(coordsNoZ).getResampledShapeX();
               int height = fullVolumeResamplers_.get(coordsNoZ).getResampledShapeY();
               if (fullVolumeStore_ == null) {
                  String prefix = inputSummaryMetadata_.getPrefix().isEmpty()
                           ? "Untitled" : inputSummaryMetadata_.getPrefix();
                  String newPrefix = prefix + "-Full-Volume-CPU";
                  fullVolumeStore_ = deskewAcqManager_.createStoreAndDisplay(studio_,
                           settings_,
                           inputSummaryMetadata_,
                           DeskewAcqManager.ProjectionType.FULL_VOLUME,
                           newPrefix,
                           width,
                           height,
                           fullVolumeResamplers_.get(
                                    coordsNoZ).getResampledShapeZ(),
                           newZStep);
               }
            }
            if (doXYProjections_) {
               if (xyProjectionResamplers_.get(coordsNoZ) == null) {
                  if (freeXYProjectionResamplers_.isEmpty()) {
                     xyProjectionResamplers_.put(coordsNoZ, new StackResampler(
                              StackResampler.YX_PROJECTION,
                              xyProjectionMode_.equals(DeskewFrame.MAX),
                              theta_,
                              image.getMetadata().getPixelSizeUm(),
                              inputSummaryMetadata_.getZStepUm(),
                              inputSummaryMetadata_.getIntendedDimensions().getZ(),
                              image.getHeight(),
                              image.getWidth()));
                  } else {
                     xyProjectionResamplers_.put(coordsNoZ,
                              freeXYProjectionResamplers_.remove(0));
                  }
               }
               xyProjectionResamplers_.get(coordsNoZ).initializeProjections();
               xyProjectionFutures_.put(image.getCoords().copyBuilder().z(0).build(),
                        processingExecutor_.submit(xyProjectionResamplers_.get(coordsNoZ)
                                 .startStackProcessing()));
               if (xyProjectionStore_ == null) {
                  int width = xyProjectionResamplers_.get(
                          coordsNoZ).getResampledShapeX();
                  int height = xyProjectionResamplers_.get(
                          coordsNoZ).getResampledShapeY();
                  String prefix = inputSummaryMetadata_.getPrefix().isEmpty()
                           ? "Untitled" : inputSummaryMetadata_.getPrefix();
                  String newPrefix = prefix + "-"
                           + (xyProjectionMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                           + "-Projection-CPU";
                  xyProjectionStore_ = deskewAcqManager_.createStoreAndDisplay(studio_,
                           settings_,
                           inputSummaryMetadata_,
                           DeskewAcqManager.ProjectionType.YX_PROJECTION,
                           newPrefix,
                           width,
                           height,
                           0,
                           null);
               }
            }
            if (doOrthogonalProjections_) {
               if (orthogonalProjectionResamplers_.get(coordsNoZ) == null) {
                  if (freeOrthogonalProjectionResamplers_.size() > 0) {
                     orthogonalProjectionResamplers_.put(coordsNoZ,
                              freeOrthogonalProjectionResamplers_.get(0));
                     freeOrthogonalProjectionResamplers_.remove(0);
                  } else {
                     orthogonalProjectionResamplers_.put(coordsNoZ, new StackResampler(
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
               orthogonalProjectionResamplers_.get(coordsNoZ).initializeProjections();
               orthogonalFutures_.put(coordsNoZ,
                        processingExecutor_.submit(
                                 orthogonalProjectionResamplers_.get(coordsNoZ)
                                          .startStackProcessing()));
               if (orthogonalStore_ == null) {
                  String prefix = inputSummaryMetadata_.getPrefix().isEmpty()
                           ? "Untitled" : inputSummaryMetadata_.getPrefix();
                  String newPrefix = prefix + "-"
                           + (orthogonalProjectionsMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                           + "-Orthogonal-Projection-CPU";
                  int width = orthogonalProjectionResamplers_.get(
                          coordsNoZ).getResampledShapeX();
                  int height = orthogonalProjectionResamplers_.get(
                          coordsNoZ).getResampledShapeY();
                  int zSize = orthogonalProjectionResamplers_.get(
                          coordsNoZ).getResampledShapeZ();
                  int separatorSize = 3;
                  int newWidth = width + separatorSize + zSize;
                  int newHeight = height + separatorSize + zSize;
                  orthogonalStore_ = deskewAcqManager_.createStoreAndDisplay(studio_,
                           settings_,
                           inputSummaryMetadata_,
                           DeskewAcqManager.ProjectionType.ORTHOGONAL_VIEWS,
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
      if (fullVolumeResamplers_.get(coordsNoZ) != null) {
         fullVolumeResamplers_.get(coordsNoZ).addToProcessImageQueue(
                  (short[]) image.getRawPixels(), image.getCoords().getZ());
      }
      if (xyProjectionResamplers_.get(coordsNoZ) != null) {
         xyProjectionResamplers_.get(coordsNoZ).addToProcessImageQueue(
                  (short[]) image.getRawPixels(), image.getCoords().getZ());
      }
      if (orthogonalProjectionResamplers_.get(coordsNoZ) != null) {
         orthogonalProjectionResamplers_.get(coordsNoZ).addToProcessImageQueue(
                  (short[]) image.getRawPixels(), image.getCoords().getZ());
      }

      if (image.getCoords().getZ() == inputSummaryMetadata_.getIntendedDimensions().getZ() - 1) {
         if (fullVolumeResamplers_.get(coordsNoZ) != null) {
            Future<?> future = fullVolumeFutures_.getOrDefault(coordsNoZ, null);
            if (future != null) {
               try {
                  future.get();
               } catch (InterruptedException | ExecutionException | OutOfMemoryError e) {
                  throw new RuntimeException(e);
               }
            }
            fullVolumeResamplers_.get(coordsNoZ).finalizeProjections();
            int width = fullVolumeResamplers_.get(coordsNoZ).getResampledShapeX();
            int height = fullVolumeResamplers_.get(coordsNoZ).getResampledShapeY();
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
               short[][] reconstructedVolume = fullVolumeResamplers_.get(coordsNoZ)
                        .getReconstructedVolumeZYX();
               for (int z = 0; z < reconstructedVolume.length; z++) {
                  Image img = new DefaultImage(reconstructedVolume[z], format, cb.z(z).build(),
                           image.getMetadata().copyBuilderWithNewUUID().build());
                  fullVolumeStore_.putImage(img);
               }
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
            freeFullVolumeResamplers_.add(fullVolumeResamplers_.get(coordsNoZ));
            fullVolumeResamplers_.remove(coordsNoZ);
         }
         if (xyProjectionResamplers_.get(coordsNoZ) != null) {
            Future<?> future = xyProjectionFutures_.getOrDefault(coordsNoZ, null);
            if (future != null) {
               try {
                  future.get();
               } catch (InterruptedException | ExecutionException e) {
                  throw new RuntimeException(e);
               }
            }
            xyProjectionResamplers_.get(coordsNoZ).finalizeProjections();
            int width = xyProjectionResamplers_.get(coordsNoZ).getResampledShapeX();
            int height = xyProjectionResamplers_.get(coordsNoZ).getResampledShapeY();
            PropertyMap.Builder formatBuilder = PropertyMaps.builder();
            formatBuilder.putInteger(PropertyKey.WIDTH.key(), width);
            formatBuilder.putInteger(PropertyKey.HEIGHT.key(), height);
            formatBuilder.putString(PropertyKey.PIXEL_TYPE.key(), PixelType.GRAY16.toString());
            PropertyMap format = formatBuilder.build();
            try {
               short[] yxProjection = xyProjectionResamplers_.get(
                        coordsNoZ).getYXProjection();
               Image img = new DefaultImage(yxProjection, format, coordsNoZPossiblyNoT,
                        image.getMetadata().copyBuilderWithNewUUID().build());
               xyProjectionStore_.putImage(img);
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
            freeXYProjectionResamplers_.add(xyProjectionResamplers_.get(coordsNoZ));
            xyProjectionResamplers_.remove(coordsNoZ);
         }
         if (orthogonalProjectionResamplers_.get(coordsNoZ) != null) {
            Future<?> future = orthogonalFutures_.getOrDefault(
                     image.getCoords().copyBuilder().z(0).build(), null);
            if (future != null) {
               try {
                  future.get();
               } catch (InterruptedException | ExecutionException e) {
                  throw new RuntimeException(e);
               }
            }
            orthogonalProjectionResamplers_.get(coordsNoZ).finalizeProjections();
            int width = orthogonalProjectionResamplers_.get(
                     coordsNoZ).getResampledShapeX();
            int height = orthogonalProjectionResamplers_.get(
                     coordsNoZ).getResampledShapeY();
            int zSize = orthogonalProjectionResamplers_.get(
                     coordsNoZ).getResampledShapeZ();
            int separatorSize = 3;
            int newWidth = width + separatorSize + zSize;
            int newHeight = height + separatorSize + zSize;
            PropertyMap.Builder formatBuilder = PropertyMaps.builder();
            formatBuilder.putInteger(PropertyKey.WIDTH.key(), newWidth);
            formatBuilder.putInteger(PropertyKey.HEIGHT.key(), newHeight);
            formatBuilder.putString(PropertyKey.PIXEL_TYPE.key(), PixelType.GRAY16.toString());
            PropertyMap format = formatBuilder.build();
            try {
               short[] yxProjection = orthogonalProjectionResamplers_.get(coordsNoZ)
                        .getYXProjection();
               short[] yzProjection = orthogonalProjectionResamplers_.get(coordsNoZ)
                        .getYZProjection();
               short[] zxProjection = orthogonalProjectionResamplers_.get(coordsNoZ)
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
                        .get(coordsNoZ));
               orthogonalProjectionResamplers_.remove(coordsNoZ);
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
      // TODO: shutdown processing executor?
      if (fullVolumeStore_ != null) {
         try {
            fullVolumeStore_.freeze();
            fullVolumeFutures_.clear();
            fullVolumeResamplers_.clear();
            freeFullVolumeResamplers_.clear();
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
            xyProjectionFutures_.clear();
            xyProjectionResamplers_.clear();
            freeXYProjectionResamplers_.clear();
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
            orthogonalFutures_.clear();
            orthogonalProjectionResamplers_.clear();
            freeOrthogonalProjectionResamplers_.clear();
            if (orthogonalStore_.getNumImages() == 0) {
               deskewAcqManager_.closeViewerFor(orthogonalStore_);
               orthogonalStore_.close();
            }
         } catch (IOException e) {
            studio_.logs().logError(e);
         }
      }
   }

}

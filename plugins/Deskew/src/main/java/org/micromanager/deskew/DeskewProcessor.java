package org.micromanager.deskew;

import java.io.IOException;
import java.util.HashMap;
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
   private Map<Coords, Future<?>> fullVolumeFutures_ = new HashMap<>();
   private Map<Coords, Future<?>> xyProjectionFutures_ = new HashMap<>();
   private Map<Coords, Future<?>> orthogonalFutures_ = new HashMap<>();
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

   private Datastore createStoreAndDisplay(SummaryMetadata summaryMetadata,
                                           String prefix,
                                           int nrZSlices,
                                           Double newZStepUm) {
      Datastore store = studio_.data().createRAMDatastore();
      SummaryMetadata.Builder smb = summaryMetadata.copyBuilder()
               .intendedDimensions(summaryMetadata
                        .getIntendedDimensions().copyBuilder().z(nrZSlices).build())
               .prefix(prefix);
      if (newZStepUm != null) {
         smb.zStepUm(newZStepUm);
      }
      SummaryMetadata outputSummaryMetadata = smb.build();
      try {
         store.setSummaryMetadata(outputSummaryMetadata);
      } catch (IOException ioe) {
         studio_.logs().logError(ioe);
      }
      DisplayWindow display = studio_.displays().createDisplay(store);
      display.setCustomTitle(prefix);
      display.show();
      return store;
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      if (image.getCoords().getZ() == 1) {
         if (doFullVolume_) {
            if (fullVolumeResampler_ == null) {
               fullVolumeResampler_ = new StackResampler(StackResampler.FULL_VOLUME, false, theta_,
                        image.getMetadata().getPixelSizeUm(), inputSummaryMetadata_.getZStepUm(),
                        inputSummaryMetadata_.getIntendedDimensions().getZ(), image.getHeight(),
                        image.getWidth());
            }
            fullVolumeResampler_.initializeProjections();
            fullVolumeFutures_.put(image.getCoords().copyBuilder().z(0).build(),
                     processingExecutor_.submit(fullVolumeResampler_.startStackProcessing()));
            double newZStep = fullVolumeResampler_.getReconstructionVoxelSizeUm();
            if (fullVolumeStore_ == null) {
               String newPrefix = inputSummaryMetadata_.getPrefix() + "-Full-Volume";
               fullVolumeStore_ = createStoreAndDisplay(inputSummaryMetadata_,
                        newPrefix, fullVolumeResampler_.getResampledShapeZ(), newZStep);
            }
         }
         if (doXYProjections_) {
            if (xyProjectionResampler_ == null) {
               xyProjectionResampler_ = new StackResampler(StackResampler.YX_PROJECTION,
                        xyProjectionMode_.equals(DeskewFrame.MAX),
                        theta_,
                        image.getMetadata().getPixelSizeUm(),
                        inputSummaryMetadata_.getZStepUm(),
                        inputSummaryMetadata_.getIntendedDimensions().getZ(),
                        image.getHeight(),
                        image.getWidth());
            }
            xyProjectionResampler_.initializeProjections();
            xyProjectionFutures_.put(image.getCoords().copyBuilder().z(0).build(),
                     processingExecutor_.submit(xyProjectionResampler_.startStackProcessing()));
            if (xyProjectionStore_ == null) {
               String newPrefix = inputSummaryMetadata_.getPrefix() + "-"
                        + (xyProjectionMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                        + "-Projection";
               xyProjectionStore_ = createStoreAndDisplay(inputSummaryMetadata_,
                        newPrefix, 1, null);
            }
         }
         if (doOrthogonalProjections_) {
            if (orthogonalProjectionResampler_ == null) {
               orthogonalProjectionResampler_ = new StackResampler(
                        StackResampler.ORTHOGONAL_VIEWS,
                        orthogonalProjectionsMode_.equals(DeskewFrame.MAX),
                        theta_,
                        image.getMetadata().getPixelSizeUm(),
                        inputSummaryMetadata_.getZStepUm(),
                        inputSummaryMetadata_.getIntendedDimensions().getZ(),
                        image.getHeight(),
                        image.getWidth());
            }
            orthogonalProjectionResampler_.initializeProjections();
            orthogonalFutures_.put(image.getCoords().copyBuilder().z(0).build(),
                     processingExecutor_.submit(
                              orthogonalProjectionResampler_.startStackProcessing()));
            if (orthogonalStore_ == null) {
               String newPrefix = inputSummaryMetadata_.getPrefix() + "-"
                        + (orthogonalProjectionsMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                        + "-Projection";
               orthogonalStore_ = createStoreAndDisplay(inputSummaryMetadata_,
                        newPrefix, 1, null);
            }
         }
      }
      if (fullVolumeResampler_ != null) {
         fullVolumeResampler_.addToProcessImageQueue((short[]) image.getRawPixels(),
                  image.getCoords().getZ());
      }
      if (xyProjectionResampler_ != null) {
         xyProjectionResampler_.addToProcessImageQueue((short[]) image.getRawPixels(),
                  image.getCoords().getZ());
      }
      if (orthogonalProjectionResampler_ != null) {
         orthogonalProjectionResampler_.addToProcessImageQueue((short[]) image.getRawPixels(),
                     image.getCoords().getZ());
      }

      if (image.getCoords().getZ() == inputSummaryMetadata_.getIntendedDimensions().getZ() - 1) {
         if (fullVolumeResampler_ != null) {
            Future<?> future = fullVolumeFutures_.getOrDefault(
                     image.getCoords().copyBuilder().z(0).build(), null);
            if (future != null) {
               try {
                  future.get();
               } catch (InterruptedException | ExecutionException e) {
                  throw new RuntimeException(e);
               }
            }
            fullVolumeResampler_.finalizeProjections();
            int width = fullVolumeResampler_.getResampledShapeX();
            int height = fullVolumeResampler_.getResampledShapeY();
            int nrZPlanes = fullVolumeResampler_.getResampledShapeZ();
            double newZStep = fullVolumeResampler_.getReconstructionVoxelSizeUm();
            PropertyMap.Builder formatBuilder = PropertyMaps.builder();
            formatBuilder.putInteger(PropertyKey.WIDTH.key(), width);
            formatBuilder.putInteger(PropertyKey.HEIGHT.key(), height);
            formatBuilder.putString(PropertyKey.PIXEL_TYPE.key(), PixelType.GRAY16.toString());
            PropertyMap format = formatBuilder.build();
            Coords.CoordsBuilder cb = image.getCoords().copyBuilder();
            try {
               short[][] reconstructedVolume = fullVolumeResampler_.getReconstructedVolumeZYX();
               for (int z = 0; z < reconstructedVolume.length; z++) {
                  Image img = new DefaultImage(reconstructedVolume[z], format, cb.z(z).build(),
                           image.getMetadata().copyBuilderWithNewUUID().build());
                  fullVolumeStore_.putImage(img);
               }
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
         if (xyProjectionResampler_ != null) {
            Future<?> future = xyProjectionFutures_.getOrDefault(
                     image.getCoords().copyBuilder().z(0).build(), null);
            if (future != null) {
               try {
                  future.get();
               } catch (InterruptedException | ExecutionException e) {
                  throw new RuntimeException(e);
               }
            }
            xyProjectionResampler_.finalizeProjections();
            int width = xyProjectionResampler_.getResampledShapeX();
            int height = xyProjectionResampler_.getResampledShapeY();
            PropertyMap.Builder formatBuilder = PropertyMaps.builder();
            formatBuilder.putInteger(PropertyKey.WIDTH.key(), width);
            formatBuilder.putInteger(PropertyKey.HEIGHT.key(), height);
            formatBuilder.putString(PropertyKey.PIXEL_TYPE.key(), PixelType.GRAY16.toString());
            PropertyMap format = formatBuilder.build();
            Coords.CoordsBuilder cb = image.getCoords().copyBuilder().z(0);
            try {
               short[] yxProjection = xyProjectionResampler_.getYXProjection();
               Image img = new DefaultImage(yxProjection, format, cb.build(),
                        image.getMetadata().copyBuilderWithNewUUID().build());
               xyProjectionStore_.putImage(img);
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
         if (orthogonalProjectionResampler_ != null) {
            Future<?> future = orthogonalFutures_.getOrDefault(
                     image.getCoords().copyBuilder().z(0).build(), null);
            if (future != null) {
               try {
                  future.get();
               } catch (InterruptedException | ExecutionException e) {
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
            PropertyMap.Builder formatBuilder = PropertyMaps.builder();
            formatBuilder.putInteger(PropertyKey.WIDTH.key(), newWidth);
            formatBuilder.putInteger(PropertyKey.HEIGHT.key(), newHeight);
            formatBuilder.putString(PropertyKey.PIXEL_TYPE.key(), PixelType.GRAY16.toString());
            PropertyMap format = formatBuilder.build();
            Coords.CoordsBuilder cb = image.getCoords().copyBuilder().z(0);
            try {
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
               orthogonalStore_.putImage(img);
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

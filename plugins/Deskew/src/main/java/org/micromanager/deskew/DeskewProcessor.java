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
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.PixelType;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
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
   private final Datastore store_;

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
   public DeskewProcessor(Studio studio, Datastore store, double theta, boolean doFullVolume,
                          boolean doXYProjections, String xyProjectionMode,
                          boolean doOrthogonalProjections, String orthogonalProjectionsMode,
                          boolean keepOriginals, PropertyMap settings) {
      studio_ = studio;
      store_ = store;
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

   private Datastore createStoreAndDisplay(Datastore store, SummaryMetadata summaryMetadata,
                                           String prefix,
                                           int nrZSlices,
                                           Double newZStepUm) {
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
      // complicated way to find the viewer that had this data
      DisplaySettings displaySettings = null;
      List<DataViewer> dataViewers = studio_.displays().getAllDataViewers();
      for (DataViewer dv : dataViewers) {
         DataProvider provider = dv.getDataProvider();
         if (provider != null && provider.getSummaryMetadata() == summaryMetadata) {
            displaySettings = dv.getDisplaySettings();
         }
      }
      DisplayWindow display = studio_.displays().createDisplay(store);
      display.setCustomTitle(prefix);
      if (displaySettings != null) {
         display.setDisplaySettings(displaySettings);
      }
      display.show();
      return store;
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      Coords coordsNoZ = image.getCoords().copyRemovingAxes(Coords.Z);
      if (settings_.getString(DeskewFrame.OUTPUT_OPTION, "")
               .equals(DeskewFrame.OPTION_REWRITABLE_RAM)) {
         coordsNoZ = coordsNoZ.copyRemovingAxes(Coords.T);
      }
      if (inputSummaryMetadata_ == null) { // seems possible in asynchronous context
         inputSummaryMetadata_ = context.getSummaryMetadata();
      }
      if (image.getCoords().getZ() == 0) {
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
                  fullVolumeResamplers_.put(coordsNoZ, freeFullVolumeResamplers_.remove(0));
               }
            }
            fullVolumeResamplers_.get(coordsNoZ).initializeProjections();
            fullVolumeFutures_.put(image.getCoords().copyBuilder().z(0).build(),
                     processingExecutor_.submit(fullVolumeResamplers_.get(coordsNoZ)
                              .startStackProcessing()));
            double newZStep = fullVolumeResamplers_.get(coordsNoZ).getReconstructionVoxelSizeUm();
            if (fullVolumeStore_ == null) {
               String newPrefix = inputSummaryMetadata_.getPrefix() + "-Full-Volume";
               fullVolumeStore_ = createStoreAndDisplay(store_, inputSummaryMetadata_,
                        newPrefix, fullVolumeResamplers_.get(coordsNoZ).getResampledShapeZ(),
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
                  xyProjectionResamplers_.put(coordsNoZ, freeXYProjectionResamplers_.remove(0));
               }
            }
            xyProjectionResamplers_.get(coordsNoZ).initializeProjections();
            xyProjectionFutures_.put(image.getCoords().copyBuilder().z(0).build(),
                     processingExecutor_.submit(xyProjectionResamplers_.get(coordsNoZ)
                              .startStackProcessing()));
            if (xyProjectionStore_ == null) {
               String newPrefix = inputSummaryMetadata_.getPrefix() + "-"
                        + (xyProjectionMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                        + "-Projection";
               xyProjectionStore_ = createStoreAndDisplay(store_, inputSummaryMetadata_,
                        newPrefix, 1, null);
            }
         }
         if (doOrthogonalProjections_) {
            if (orthogonalProjectionResamplers_.get(coordsNoZ) == null) {
               if (freeOrthogonalProjectionResamplers_.size() > 0) {
                  orthogonalProjectionResamplers_.put(coordsNoZ, freeOrthogonalProjectionResamplers_
                           .get(0));
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
               String newPrefix = inputSummaryMetadata_.getPrefix() + "-"
                        + (orthogonalProjectionsMode_.equals(DeskewFrame.MAX) ? "Max" : "Avg")
                        + "-Projection";
               orthogonalStore_ = createStoreAndDisplay(store_, inputSummaryMetadata_,
                        newPrefix, 1, null);
            }
         }
      }
      if (fullVolumeResamplers_.get(coordsNoZ) != null) {
         fullVolumeResamplers_.get(coordsNoZ).addToProcessImageQueue((short[]) image.getRawPixels(),
                  image.getCoords().getZ());
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
            Coords.CoordsBuilder cb = image.getCoords().copyBuilder().z(0);
            try {
               short[] yxProjection = xyProjectionResamplers_.get(coordsNoZ).getYXProjection();
               Image img = new DefaultImage(yxProjection, format, cb.build(),
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
            int width = orthogonalProjectionResamplers_.get(coordsNoZ).getResampledShapeX();
            int height = orthogonalProjectionResamplers_.get(coordsNoZ).getResampledShapeY();
            int zSize = orthogonalProjectionResamplers_.get(coordsNoZ).getResampledShapeZ();
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

               Image img = new DefaultImage(orthogonalView, format, cb.build(),
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

}

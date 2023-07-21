package org.micromanager.deskew;

import java.io.IOException;
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

public class DeskewProcessor implements Processor {
   private SummaryMetadata inputSummaryMetadata_;
   private final Studio studio_;
   private final double theta_;
   private final boolean doFullVolume_;
   private final boolean doXYProjections_;
   private final boolean doOrthogonalProjections_;
   private final boolean keepOriginals_;
   private StackResampler fullVolumeResampler_ = null;
   private StackResampler xyProjectionResampler_ = null;
   private StackResampler orthogonalProjectionResampler_ = null;

   public DeskewProcessor(Studio studio, double theta, boolean doFullVolume,
                          boolean doXYProjections, boolean doOrthogonalProjections,
                          boolean keepOriginals) {
      studio_ = studio;
      theta_ = theta;
      doFullVolume_ = doFullVolume;
      doXYProjections_ = doXYProjections;
      doOrthogonalProjections_ = doOrthogonalProjections;
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
            xyProjectionResampler_ = new StackResampler(StackResampler.YX_PROJECTION, false,
                     theta_, image.getMetadata().getPixelSizeUm(),
                     inputSummaryMetadata_.getZStepUm(),
                     inputSummaryMetadata_.getIntendedDimensions().getZ(), image.getWidth(),
                     image.getHeight());
            xyProjectionResampler_.initializeProjections();
         }
         if (doOrthogonalProjections_) {
            orthogonalProjectionResampler_ = new StackResampler(
                     StackResampler.ORTHOGONAL_VIEWS, false,
                     theta_, image.getMetadata().getPixelSizeUm(),
                     inputSummaryMetadata_.getZStepUm(),
                     inputSummaryMetadata_.getIntendedDimensions().getZ(), image.getWidth(),
                     image.getHeight());
            orthogonalProjectionResampler_.initializeProjections();
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
         orthogonalProjectionResampler_.addImageToRecons((short[]) image.getRawPixels(),
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
            SummaryMetadata outputSummaryMetadata = inputSummaryMetadata_.copyBuilder()
                     .intendedDimensions(inputSummaryMetadata_
                              .getIntendedDimensions().copyBuilder().z(nrZPlanes).build())
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
               display.show();
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
         if (orthogonalProjectionResampler_ != null) {
            orthogonalProjectionResampler_.finalizeProjections();
            int width = orthogonalProjectionResampler_.getResampledShapeX();
            int height = orthogonalProjectionResampler_.getResampledShapeY();
            int nrZPlanes = 1;
            Datastore outputStore = studio_.data().createRAMDatastore();
            SummaryMetadata outputSummaryMetadata = inputSummaryMetadata_.copyBuilder()
                     .intendedDimensions(inputSummaryMetadata_
                              .getIntendedDimensions().copyBuilder().z(nrZPlanes).build())
                     .build();
            PropertyMap.Builder formatBuilder = PropertyMaps.builder();
            formatBuilder.putInteger(PropertyKey.WIDTH.key(), width);
            formatBuilder.putInteger(PropertyKey.HEIGHT.key(), height);
            formatBuilder.putString(PropertyKey.PIXEL_TYPE.key(), PixelType.GRAY16.toString());
            PropertyMap format = formatBuilder.build();
            Coords.CoordsBuilder cb = studio_.data().coordsBuilder();
            try {
               outputStore.setSummaryMetadata(outputSummaryMetadata);
               short[] yxProjection = orthogonalProjectionResampler_.getYXProjection();
               short[] zyProjection = orthogonalProjectionResampler_.getZYProjection();
               short[] zxProjection = orthogonalProjectionResampler_.getZXProjection();
               Image img = new DefaultImage(yxProjection, format, cb.build(),
                        image.getMetadata().copyBuilderWithNewUUID().build());
               outputStore.putImage(img);
               DisplayWindow display = studio_.displays().createDisplay(outputStore);
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

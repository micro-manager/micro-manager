package org.micromanager.plugins.framecombiner;

import java.util.Arrays;
import org.micromanager.LogManager;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.ProcessorContext;

/**
 * This class processes a single combination of Z, T, Channel, Stage Position.
 * It buffers the images and processes them when the buffer is full.
 * The single, "projected" processed image is then outputted.
 */
public class SingleCombinationProcessor {

   private final Studio studio_;
   private final LogManager log_;

   private final String processorAlgo_;
   private final String processorDimension_;
   private final int numberOfImagesToProcess_;

   // Do we want to enable processing for this combinations of Z, Channel, Stage Position ?
   private final boolean processCombinations_;
   private final boolean isAnyChannelToAvoid_;

   private int currentFrameIndex;
   private int processedFrameIndex;
   private Image[] bufferImages_;

   public SingleCombinationProcessor(Coords coords, Studio studio, String processorAlgo,
                                     String processorDimension,
                                     int numberOfImagesToProcess, boolean processCombinations,
                                     boolean isAnyChannelToAvoid) {

      studio_ = studio;
      log_ = studio_.logs();

      processorAlgo_ = processorAlgo;
      processorDimension_ = processorDimension;
      numberOfImagesToProcess_ = numberOfImagesToProcess;
      processCombinations_ = processCombinations;
      isAnyChannelToAvoid_ = isAnyChannelToAvoid;

      currentFrameIndex = 0;
      processedFrameIndex = 0;
      bufferImages_ = new Image[numberOfImagesToProcess_];

      for (int i = 0; i < numberOfImagesToProcess_; i++) {
         bufferImages_[i] = null;
      }
   }


   void addImage(Image image, ProcessorContext context) {

      if (!processCombinations_) {
         context.outputImage(image);
         return;
      }

      int currentBufferIndex = currentFrameIndex % numberOfImagesToProcess_;
      bufferImages_[currentBufferIndex] = image;

      Image processedImage = null;
      if (currentBufferIndex == (numberOfImagesToProcess_ - 1)) {
         try {
            // Process last `numberOfImagesToProcess_` images
            processedImage = processBufferImages();
         } catch (Exception ex) {
            log_.logError(ex);
         }

         if (processedImage == null) {
            return;
         }

         // Clean buffered images
         for (int i = 0; i < numberOfImagesToProcess_; i++) {
            bufferImages_[i] = null;
         }

         // Add metadata to the processed image
         Metadata metadata = processedImage.getMetadata();
         PropertyMap userData = metadata.getUserData();
         if (userData != null) {
            userData = userData.copyBuilder().putBoolean("FrameProcessed", true).build();
            userData =
                  userData.copyBuilder().putString(
                          "FrameProcessed-Operation", processorAlgo_).build();
            userData = userData.copyBuilder().putInteger(
                    "FrameProcessed-StackNumber", numberOfImagesToProcess_)
                  .build();
            metadata = metadata.copyBuilderPreservingUUID().userData(userData).build();
         }
         processedImage = processedImage.copyWithMetadata(metadata);

         // Add correct metadata if in acquisition mode
         if (studio_.acquisitions().isAcquisitionRunning() && !isAnyChannelToAvoid_) {
            Coords.CoordsBuilder builder = processedImage.getCoords().copy();
            if (processorDimension_.equals(FrameCombinerPlugin.PROCESSOR_DIMENSION_TIME)) {
               builder.time(processedFrameIndex);
            } else if (processorDimension_.equals(FrameCombinerPlugin.PROCESSOR_DIMENSION_Z)) {
               builder.z(processedFrameIndex);
            }
            processedImage = processedImage.copyAtCoords(builder.build());
            processedFrameIndex += 1;
         }

         // Output processed image
         context.outputImage(processedImage);
      }

      currentFrameIndex += 1;

   }

   /**
    * Clear the buffer.
    */
   public void clear() {
      for (int i = 0; i < numberOfImagesToProcess_; i++) {
         bufferImages_[i] = null;
      }
      bufferImages_ = null;
   }

   /**
    * Process the images in the buffer and return the processed image.
    *
    * @return The processed image.
    * @throws Exception If the processing fails.
    */
   public Image processBufferImages() throws Exception {

      if (processorAlgo_.equals(FrameCombinerPlugin.PROCESSOR_ALGO_MEAN)) {
         return meanProcessImages(false);
      } else if (processorAlgo_.equals(FrameCombinerPlugin.PROCESSOR_ALGO_SUM)) {
         return meanProcessImages(true);
      } else if (processorAlgo_.equals(FrameCombinerPlugin.PROCESSOR_ALGO_MAX)) {
         return extremaProcessImages("max");
      } else if (processorAlgo_.equals(FrameCombinerPlugin.PROCESSOR_ALGO_MIN)) {
         return extremaProcessImages("min");
      } else {
         throw new Exception("FrameCombiner : Algorithm called " + processorAlgo_
               + " is not implemented or not found.");
      }

   }

   /**
    * Process the images in the buffer and return the processed image.
    *
    * @param onlySum If `true` then only the sum of the images will be calculated.
    * @return The processed image.
    */
   public Image meanProcessImages(boolean onlySum) {

      // Could be moved outside processImage() ?
      Image img = bufferImages_[0];
      int width = img.getWidth();
      int height = img.getHeight();
      int bytesPerPixel = img.getBytesPerPixel();
      int numComponents = img.getNumComponents();
      Coords coords = img.getCoords();
      Metadata metadata = img.getMetadata();

      Object resultPixels = null;

      if (bytesPerPixel == 1) {

         // Create new array
         float[] newPixels = new float[width * height];
         byte[] newPixelsFinal = new byte[width * height];

         // Sum up all pixels from bufferImages
         for (int i = 0; i < numberOfImagesToProcess_; i++) {

            // Get current frame pixels
            img = bufferImages_[i];
            byte[] imgPixels = (byte[]) img.getRawPixels();

            // Iterate over all pixels
            for (int index = 0; index < newPixels.length; index++) {
               newPixels[index] = (float) (newPixels[index] + (int) (imgPixels[index] & 0xff));
            }
         }

         // Divide by length to get the mean
         for (int index = 0; index < newPixels.length; index++) {
            if (onlySum) {
               newPixelsFinal[index] = (byte) (int) (newPixels[index]);
            } else {
               newPixelsFinal[index] = (byte) (int) (newPixels[index] / numberOfImagesToProcess_);
            }
         }

         resultPixels = newPixelsFinal;

      } else if (bytesPerPixel == 2) {

         // Create new array
         float[] newPixels = new float[width * height];
         short[] newPixelsFinal = new short[width * height];

         // Sum up all pixels from bufferImages
         for (int i = 0; i < numberOfImagesToProcess_; i++) {

            // Get current frame pixels
            img = bufferImages_[i];
            short[] imgPixels = (short[]) img.getRawPixels();

            // Iterate over all pixels
            for (int index = 0; index < newPixels.length; index++) {
               newPixels[index] = (float) (newPixels[index] + (int) (imgPixels[index] & 0xffff));
            }
         }

         // Divide by length to get the mean
         for (int index = 0; index < newPixels.length; index++) {
            if (onlySum) {
               newPixelsFinal[index] = (short) (int) (newPixels[index]);
            } else {
               newPixelsFinal[index] = (short) (int) (newPixels[index] / numberOfImagesToProcess_);
            }
         }

         resultPixels = newPixelsFinal;

      }

      // Create the processed image
      return studio_.data().createImage(resultPixels, width, height,
            bytesPerPixel, numComponents, coords, metadata);

   }

   /**
    * Process the images in the buffer and return the processed image.
    *
    * @param extremaType The type of extrema to calculate (max or min).
    * @return The processed image.
    * @throws Exception If the processing fails.
    */
   public Image extremaProcessImages(String extremaType) throws Exception {

      // Could be moved outside processImage() ?
      Image img = bufferImages_[0];
      int width = img.getWidth();
      int height = img.getHeight();
      int bytesPerPixel = img.getBytesPerPixel();
      int numComponents = img.getNumComponents();
      Coords coords = img.getCoords();
      Metadata metadata = img.getMetadata();

      Object resultPixels = null;

      if (bytesPerPixel == 1) {

         // Create new array
         float[] newPixels = new float[width * height];
         byte[] newPixelsFinal = new byte[width * height];

         float currentValue;
         float actualValue;

         // Init the new array (already zero, so no need to set for max)
         if (extremaType.equals("min")) {
            Arrays.fill(newPixels, Byte.MAX_VALUE);
         }

         // Iterate over all frames
         for (int i = 0; i < numberOfImagesToProcess_; i++) {
            // Get current frame pixels
            img = bufferImages_[i];
            short[] imgPixels = (short[]) img.getRawPixels();

            // Iterate over all pixels
            for (int index = 0; index < newPixels.length; index++) {
               currentValue = (float) (int) (imgPixels[index] & 0xffff);
               actualValue = (float) newPixels[index];

               if (extremaType.equals("max")) {
                  newPixels[index] =  Math.max(currentValue, actualValue);
               } else { // min
                  newPixels[index] = Math.min(currentValue, actualValue);
               }
            }
         }

         // Convert to short
         for (int index = 0; index < newPixels.length; index++) {
            newPixelsFinal[index] = (byte) newPixels[index];
         }

         resultPixels = newPixelsFinal;

      } else if (bytesPerPixel == 2) {

         // Create new array
         float[] newPixels = new float[width * height];
         short[] newPixelsFinal = new short[width * height];

         float currentValue;
         float actualValue;

         // Init the new array
         // if (extremaType.equals("max")) { // no need to set new array to zero in Java
         if (extremaType.equals("min")) {
            Arrays.fill(newPixels, Byte.MAX_VALUE);
         }

         // Iterate over all frames
         for (int i = 0; i < numberOfImagesToProcess_; i++) {

            // Get current frame pixels
            img = bufferImages_[i];
            short[] imgPixels = (short[]) img.getRawPixels();

            // Iterate over all pixels
            for (int index = 0; index < newPixels.length; index++) {
               currentValue = (float) (int) (imgPixels[index] & 0xffff);
               actualValue = (float) newPixels[index];

               if (extremaType.equals("max")) {
                  newPixels[index] = Math.max(currentValue, actualValue);
               } else {
                  newPixels[index] =  Math.min(currentValue, actualValue);
               }
            }
         }

         // Convert to short
         for (int index = 0; index < newPixels.length; index++) {
            newPixelsFinal[index] = (short) newPixels[index];
         }

         resultPixels = newPixelsFinal;

      }

      // Create the processed image
      return studio_.data().createImage(resultPixels, width, height,
            bytesPerPixel, numComponents, coords, metadata);

   }
}

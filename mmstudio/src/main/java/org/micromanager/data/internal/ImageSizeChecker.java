package org.micromanager.data.internal;

import org.micromanager.data.Image;
import org.micromanager.data.ImagesDifferInSizeException;
import org.micromanager.data.SummaryMetadata;

public class ImageSizeChecker {

   public static void checkImageSizes(Image image1, Image image2) {
      if (image1.getHeight() != image2.getHeight()) {
         throw new ImagesDifferInSizeException();
      }
      if (image1.getWidth() != image2.getWidth()) {
         throw new ImagesDifferInSizeException();
      }
      if (image1.getBytesPerPixel() != image2.getBytesPerPixel()) {
         throw new ImagesDifferInSizeException();
      }
   }

   public static void checkImageSizeInSummary(SummaryMetadata summaryMetadata, Image image)
           throws ImagesDifferInSizeException {
      if (summaryMetadata.getImageWidth() != null && summaryMetadata.getImageWidth() != 0) {
         if (summaryMetadata.getImageWidth() != image.getWidth()) {
            throw new ImagesDifferInSizeException();
         }
      }
      if (summaryMetadata.getImageHeight() != null && summaryMetadata.getImageHeight() != 0) {
         if (summaryMetadata.getImageHeight() != image.getHeight()) {
            throw new ImagesDifferInSizeException();
         }
      }

   }
}

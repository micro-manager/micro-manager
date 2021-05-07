package org.micromanager.data.internal;

import org.micromanager.data.Image;
import org.micromanager.data.ImagesDifferInSizeException;

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
}

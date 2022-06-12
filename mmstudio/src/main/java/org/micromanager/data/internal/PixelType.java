/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.data.internal;

import com.google.common.base.Preconditions;

/**
 * @author mark
 */
public enum PixelType {
   // Names are used in file formats so don't change

   GRAY8(1, 1, 1) {
      @Override
      public int imageJConstant() {
         return 0;
      }
   },
   GRAY16(2, 2, 1) {
      @Override
      public int imageJConstant() {
         return 1;
      }
   },
   /**
    * RGB 888 format.
    * <p>
    * Misleadingly, when saved to TIFF this is RGB24 with no extra byte per
    * pixel.
    * <p>
    * TODO Make sure the following statement is correct.
    * <p>
    * As a Java array, this is an {@code int[]} with the samples stored in
    * _-R-G-B order (MSB to LSB), or a {@code byte[]} with the samples stored
    * in _-R-G-B order.
    * <p>
    * As a native (C) array, this is a {@code uint8_t[]} with the samples
    * stored in B-G-R-_ order -- at least on little-endian systems.
    * NS 2018/02/05: This seems to be the order RGB images are stored in our
    * ByteBuffers.  Since we use RGB in our UI, order the components this way
    * here.  TODO: evaluate if this the right place for component ordering
    */
   RGB32(4, 1, 3, new int[] {2, 1, 0}) {
      @Override
      public int imageJConstant() {
         return 4;
      }
   },
   ;

   private final int bpp_;
   private final int bpc_;
   private final int nCompo_;
   private final int[] offsets_;

   private PixelType(int bytesPerPixel, int bytesPerComponent,
                     int numComponents) {
      this(bytesPerPixel, bytesPerComponent, numComponents, null);
   }

   private PixelType(int bytesPerPixel, int bytesPerComponent,
                     int numComponents, int[] componentOffsets) {
      // Check an assumption frequeltly made all over our code
      Preconditions.checkArgument(bytesPerPixel % bytesPerComponent == 0,
            "Bytes per pixel must be multiple of bytes per component");

      if (componentOffsets == null) {
         componentOffsets = new int[numComponents];
         for (int i = 0; i < numComponents; i++) {
            componentOffsets[i] = i;
         }
      }

      bpp_ = bytesPerPixel;
      bpc_ = bytesPerComponent;
      nCompo_ = numComponents;
      offsets_ = componentOffsets.clone();
   }

   public final int getBytesPerPixel() {
      return bpp_;
   }

   public final int getBytesPerComponent() {
      return bpc_;
   }

   public final int getNumberOfComponents() {
      return nCompo_;
   }

   /**
    * Get the position of a component sample within a pixel .
    *
    * @param component the component
    * @return offset of component within pixel, in number of samples (not
    * bytes)
    */
   public final int getComponentSampleOffset(int component) {
      return offsets_[component];
   }

   public static PixelType valueFor(int bytesPerPixel, int bytesPerComponent,
                                    int numberOfComponents) {
      // TODO: this code looks very suspicious, but it may use these nasty fall throughs on purpose
      switch (numberOfComponents) {
         case 1:
            switch (bytesPerComponent) {
               case 1:
                  switch (bytesPerPixel) {
                     case 1:
                        return GRAY8;
                  }
               case 2:
                  switch (bytesPerPixel) {
                     case 2:
                        return GRAY16;
                  }
            }
         case 3:
            switch (bytesPerComponent) {
               case 1:
                  switch (bytesPerPixel) {
                     case 4:
                        return RGB32;
                  }
            }
      }
      throw new UnsupportedOperationException(String.format(
            "Unsupported pixel type: %d bytes per pixel, %d bytes per component, %d components",
            bytesPerPixel, bytesPerComponent, numberOfComponents));
   }

   public static PixelType valueOfImageJConstant(int ijType) {
      switch (ijType) {
         case 0: // ImagePlus.GRAY8:
            return GRAY8;

         case 1: // ImagePlus.GRAY16
            return GRAY16;

         case 4: // ImagePlus.COLOR_RGB
            return RGB32;

         case 2: // ImagePlus.GRAY32 (float)
         case 3: // ImagePlus.COLOR_256
         default:
            throw new UnsupportedOperationException(
                  "Unsupported ImageJ pixel type: " + ijType);
      }
   }

   public int imageJConstant() {
      throw new UnsupportedOperationException(String.format(
            "Pixel type %s cannot be converted to ImageJ pixel type", name()));
   }
}

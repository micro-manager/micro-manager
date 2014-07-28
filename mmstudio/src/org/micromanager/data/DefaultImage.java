package org.micromanager.data;

import mmcorej.TaggedImage;

import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.meta.ImgPlus;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.Metadata;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 * This class represents a single image from a single camera. It contains
 * the image pixel data, metadata (in the form of a Metadata instance), and
 * the image's position as part of a larger dataset (in the form of an
 * Coords instance).
 */
public class DefaultImage implements Image {
   private ImgPlus pixels_;
   private Metadata metadata_;
   private Coords coords_;

   /**
    * @param taggedImage A TaggedImage to base the Image on.
    */
   public DefaultImage(TaggedImage tagged) throws JSONException, MMScriptException {
      JSONObject tags = tagged.tags;
      metadata_ = new DefaultMetadata.Builder()
            .camera(MDUtils.getChannelName(tags))
            .ROI(MDUtils.getROI(tags))
            .binning(MDUtils.getBinning(tags))
            .pixelSizeUm(MDUtils.getPixelSizeUm(tags))
            .uuid(MDUtils.getUUID(tags))
            .zStepUm(MDUtils.getZStepUm(tags))
            .elapsedTimeMs(MDUtils.getElapsedTimeMs(tags))
            .comments(MDUtils.getComments(tags))
            .build();

      coords_ = new DefaultCoords.Builder()
            .position("time", MDUtils.getFrameIndex(tags))
            .position("position", MDUtils.getPositionIndex(tags))
            .position("slice", MDUtils.getSliceIndex(tags))
            .position("channel", MDUtils.getChannelIndex(tags))
            .build();

      pixels_ = generateImgPlusFromPixels(tagged.pix, 
            MDUtils.getWidth(tags), MDUtils.getHeight(tags),
            MDUtils.getBytesPerPixel(tags));
   }

   /**
    * @param pixels Assumed to be a Java array of either bytes or shorts.
    */
   public DefaultImage(Object pixels, int width, int height, int bytesPerPixel) 
         throws IllegalArgumentException {
      metadata_ = new DefaultMetadata.Builder().build();
      coords_ = new DefaultCoords.Builder().build();

      pixels_ = generateImgPlusFromPixels(pixels, width, height, bytesPerPixel);
   }

   private ImgPlus generateImgPlusFromPixels(Object pixels, int width, 
         int height, int bytesPerPixel)
         throws IllegalArgumentException {
      long[] dimensions = new long[] {width, height};
      if (pixels instanceof short[]) {
         if (bytesPerPixel == 8) {
            // Indicates an RGBA datatype. Currently I don't believe we're 
            // using short-based RGBA anywhere.
            throw new IllegalArgumentException("No 64-bit RGBA support yet.");
         }
         // Assume grayscale.
         return new ImgPlus<UnsignedShortType>(
               ArrayImgs.unsignedShorts((short[]) pixels, dimensions));
      }
      else if (pixels instanceof byte[]) {
         if (bytesPerPixel == 4) {
            // Indicates an RGBA type. imglib2 uses ARGB though. For now we
            // aren't bothering to re-order pixels, which could potentially
            // cause problems down the road.
            // TODO: Reorder pixels to the proper ordering.
            ReportingUtils.logMessage("Casting RGBA to ARGB; pixels may be misleading!");
            return new ImgPlus<ARGBType>(
                  ArrayImgs.argbs((int[]) pixels, dimensions));
         }
         else { // Grayscale image
            return new ImgPlus<UnsignedByteType>(
                  ArrayImgs.unsignedBytes((byte[]) pixels, dimensions));
         }
      }
      else {
         throw new IllegalArgumentException("Unsupported image array type.");
      }
   }

   @Override
   public ImgPlus getPixels() {
      return pixels_;
   }

   @Override
   public Metadata getMetadata() {
      return metadata_;
   }

   @Override
   public Coords getCoords() {
      return coords_;
   }

   /**
    * For backwards compatibility, convert to TaggedImage.
    */
   @Override
   public TaggedImage legacyToTaggedImage() {
      ArrayImg temp = (ArrayImg) pixels_.firstElement();
      ArrayDataAccess accessor = (ArrayDataAccess) temp.update(null);
      Object pixelData = accessor.getCurrentStorageArray();
      JSONObject tags = metadata_.legacyToJSON();
      return new TaggedImage(pixelData, tags);
   }
}

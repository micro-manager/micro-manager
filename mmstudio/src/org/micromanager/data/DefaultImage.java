package org.micromanager.data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import mmcorej.TaggedImage;

import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.meta.ImgPlus;
import net.imglib2.RandomAccess;
import net.imglib2.type.NativeType;
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
      DefaultMetadata.Builder builder = new DefaultMetadata.Builder();
      try {
         builder.camera(MDUtils.getChannelName(tags));
      }
      catch (JSONException e) {}
      try {
         builder.ROI(MDUtils.getROI(tags));
      }
      catch (JSONException e) {}
      try {
         builder.binning(MDUtils.getBinning(tags));
      }
      catch (JSONException e) {}
      try {
         builder.bitDepth(MDUtils.getBitDepth(tags));
      }
      catch (JSONException e) {}
      try {
         builder.pixelSizeUm(MDUtils.getPixelSizeUm(tags));
      }
      catch (JSONException e) {}
      try {
         builder.uuid(MDUtils.getUUID(tags));
      }
      catch (JSONException e) {}
      try {
         builder.zStepUm(MDUtils.getZStepUm(tags));
      }
      catch (JSONException e) {}
      try {
         builder.elapsedTimeMs(MDUtils.getElapsedTimeMs(tags));
      }
      catch (JSONException e) {}
      try {
         builder.comments(MDUtils.getComments(tags));
      }
      catch (JSONException e) {}
      metadata_ = builder.build();

      DefaultCoords.Builder cBuilder = new DefaultCoords.Builder();
      try {
         cBuilder.position("time", MDUtils.getFrameIndex(tags));
      }
      catch (JSONException e) {}
      try {
         cBuilder.position("position", MDUtils.getPositionIndex(tags));
      }
      catch (JSONException e) {}
      try {
         cBuilder.position("z", MDUtils.getSliceIndex(tags));
      }
      catch (JSONException e) {}
      try {
         cBuilder.position("channel", MDUtils.getChannelIndex(tags));
      }
      catch (JSONException e) {}
      coords_ = cBuilder.build();

      pixels_ = generateImgPlusFromPixels(tagged.pix, 
            MDUtils.getWidth(tags), MDUtils.getHeight(tags),
            MDUtils.getBytesPerPixel(tags));
   }

   /**
    * @param pixels Assumed to be a Java array of either bytes or shorts.
    */
   public DefaultImage(Object pixels, int width, int height, int bytesPerPixel,
         Coords coords, Metadata metadata) 
         throws IllegalArgumentException {
      metadata_ = metadata;
      coords_ = coords;

      pixels_ = generateImgPlusFromPixels(pixels, width, height, bytesPerPixel);
   }

   public DefaultImage(DefaultImage source, Coords coords, Metadata metadata) {
      metadata_ = metadata;
      coords_ = coords;
      pixels_ = generateImgPlusFromPixels(source.getRawPixels(),
            source.getWidth(), source.getHeight(), source.getBytesPerPixel());
   }

   /**
    * NOTE: if you want to add additional datatypes at this stage, make certain
    * you also update the getBytesPerPixel() function to be able to extract the
    * appropriate bytes per pixel from the ArrayImg later, and the 
    * getIntensityAt() function for similar reasons.
    */
   private ImgPlus generateImgPlusFromPixels(Object pixels, int width, 
         int height, int bytesPerPixel)
         throws IllegalArgumentException {
      long[] dimensions = new long[] {width, height};
      if (pixels instanceof byte[]) {
         if (bytesPerPixel == 4) {
            // RGB type. The argbs() method only takes int[] or long[] though,
            // so we have to do some type conversion. Adapted from
            // http://stackoverflow.com/questions/11437203/byte-array-to-int-array
            // Additionally, imglib2 uses ARGB, not RGBA, so our pixels may be
            // in the wrong order.
            // TODO: fix pixel order.
            IntBuffer intBuf = ByteBuffer.wrap((byte[]) pixels).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
            int[] temp = new int[intBuf.remaining()];
            intBuf.get(temp);
            ReportingUtils.logMessage("Casting RGBA to ARGB; pixels may be misleading!");
            return new ImgPlus<ARGBType>(ArrayImgs.argbs(temp, dimensions));
         }
         // Otherwise assume grayscale.
         return new ImgPlus<UnsignedByteType>(
               ArrayImgs.unsignedBytes((byte[]) pixels, dimensions));
      }
      else if (pixels instanceof short[]) {
         // Assume grayscale.
         return new ImgPlus<UnsignedShortType>(
               ArrayImgs.unsignedShorts((short[]) pixels, dimensions));
      }
      else if (pixels instanceof int[]) {
         // TODO: assuming RGBA type as no MM cameras currently support 32-bit
         // grayscale. This branch will execute when cloning an existing
         // DefaultImage.
         return new ImgPlus<ARGBType>(
               ArrayImgs.argbs((int[]) pixels, dimensions));
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

   @Override
   public Image copyAtCoords(Coords coords) {
      return new DefaultImage(this, coords, metadata_);
   }

   @Override
   public Image copyWithMetadata(Metadata metadata) {
      return new DefaultImage(this, coords_, metadata);
   }

   @Override
   public Image copyWith(Coords coords, Metadata metadata) {
      return new DefaultImage(this, coords, metadata);
   }

   /**
    * Return a raw Object of our pixel data, i.e. a reference to the 
    * Java array (or whatever) that backs our ImgPlus.
    */
   @Override
   public Object getRawPixels() {
      ArrayDataAccess accessor = (ArrayDataAccess) ((ArrayImg) pixels_.getImg()).update(null);
      return accessor.getCurrentStorageArray();
   }

   /**
    * Return the intensity of the pixel at the specified XY position, as a 
    * double (regardless of the actual format of the pixel).
    */
   @Override
   public double getIntensityAt(int x, int y) {
      RandomAccess accessor = pixels_.randomAccess();
      accessor.move(new int[] {x, y});
      Object result = accessor.get();
      if (result instanceof UnsignedByteType) {
         return (byte) ((UnsignedByteType) result).get();
      }
      else if (result instanceof UnsignedShortType) {
         return (short) ((UnsignedShortType) result).get();
      }
      else if (result instanceof ARGBType) {
         ReportingUtils.logError("Asked for intensity of RGB image; unsure result is sensible.");
         return (short) ((ARGBType) result).get();
      }
      else {
         ReportingUtils.logError("Unrecognized data type; can't get intensity");
         return -1;
      }
   }

   /**
    * For backwards compatibility, convert to TaggedImage.
    */
   @Override
   public TaggedImage legacyToTaggedImage() {
      return new TaggedImage(getRawPixels(), metadata_.legacyToJSON());
   }

   @Override
   public int getWidth() {
      return (int) pixels_.dimension(0);
   }

   @Override
   public int getHeight() {
      return (int) pixels_.dimension(1);
   }

   public int getBytesPerPixel() {
      int result = -1;
      if (pixels_.firstElement() instanceof UnsignedByteType) {
         result = 1;
      }
      else if (pixels_.firstElement() instanceof UnsignedShortType) {
         result = 2;
      }
      else if (pixels_.firstElement() instanceof ARGBType) {
         result = 4;
      }
      else {
         ReportingUtils.logError("Can't recognize type of our own pixels array.");
      }
      return result;
   }

   public String toString() {
      return String.format("<%dx%d image at %s>", getWidth(), getHeight(), coords_);
   }
}

package org.micromanager.data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

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
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DatastoreLockedException;
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
   private Metadata metadata_;
   private Coords coords_;
   private Object rawPixels_;
   // Width of the image, in pixels
   int pixelWidth_;
   // Height of the image, in pixels
   int pixelHeight_;
   // How many bytes are allocated to each pixel in rawPixels_. This is
   // different from the bits per pixel, which is in the Metadata, and
   // indicates the range of values that the camera can output (e.g. a 12-bit
   // pixel has values in [0, 4095].
   int bytesPerPixel_;
   // How many components are packed into each pixel's worth of data (e.g. an
   // RGB or CMYK image).
   int numComponents_;

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

      rawPixels_ = tagged.pix;
      pixelWidth_ = MDUtils.getWidth(tags);
      pixelHeight_ = MDUtils.getHeight(tags);
      bytesPerPixel_ = MDUtils.getBytesPerPixel(tags);
      numComponents_ = MDUtils.getNumberOfComponents(tags);
   }

   /**
    * @param pixels Assumed to be a Java array of either bytes or shorts.
    */
   public DefaultImage(Object pixels, int width, int height, int bytesPerPixel,
         int numComponents, Coords coords, Metadata metadata) 
         throws IllegalArgumentException {
      metadata_ = metadata;
      coords_ = coords;

      rawPixels_ = pixels;
      pixelWidth_ = width;
      pixelHeight_ = height;
      bytesPerPixel_ = bytesPerPixel;
      numComponents_ = numComponents;
   }

   public DefaultImage(Image source, Coords coords, Metadata metadata) {
      metadata_ = metadata;
      coords_ = coords;
      rawPixels_ = source.getRawPixels();
      pixelWidth_ = source.getWidth();
      pixelHeight_ = source.getHeight();
      bytesPerPixel_ = source.getBytesPerPixel();
      numComponents_ = source.getNumComponents();
   }

   @Override
   public ImgPlus getImgPlus() {
      return generateImgPlusFromPixels(rawPixels_, pixelWidth_, pixelHeight_,
            bytesPerPixel_);
   }

   /**
    * Inspect our pixel array and bytes per pixel, and generate an ImgPlus
    * based on what we find.
    * TODO: our handling of RGB images is imperfect.
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

   @Override
   public Object getRawPixels() {
      return rawPixels_;
   }

   // This is a bit ugly, due to needing to examine the type of rawPixels_,
   // but what else can we do?
   @Override
   public Object getRawPixelsForComponent(int component) {
      Object result;
      int length;
      if (rawPixels_ instanceof byte[]) {
         length = ((byte[]) rawPixels_).length / bytesPerPixel_;
         result = (Object) new byte[length];
      }
      else if (rawPixels_ instanceof short[]) {
         length = ((short[]) rawPixels_).length / bytesPerPixel_;
         result = (Object) new short[length];
      }
      else {
         ReportingUtils.logError("Unrecognized pixel buffer type.");
         return null;
      }
      for (int i = 0; i < length; ++i) {
         int sourceIndex = i * bytesPerPixel_ + component;
         if (rawPixels_ instanceof byte[]) {
            ((byte[]) result)[i] = ((byte[]) rawPixels_)[sourceIndex];
         }
         else if (rawPixels_ instanceof short[]) {
            ((short[]) result)[i] = ((short[]) rawPixels_)[sourceIndex];
         }
      }
      return result;
   }

   @Override
   public long getIntensityAt(int x, int y) {
      return getComponentIntensityAt(x, y, 0);
   }

   @Override
   public long getComponentIntensityAt(int x, int y, int component) {
      long result = 0;
      int divisor = numComponents_;
      int exponent = 8;
      if (rawPixels_ instanceof short[]) {
         divisor *= 2;
         exponent = 16;
      }
      for (int i = 0; i < bytesPerPixel_ / divisor; ++i) {
         int index = x * pixelWidth_ + y + component + i;
         if (rawPixels_ instanceof byte[]) {
            result += ((byte[]) rawPixels_)[index];
         }
         else if (rawPixels_ instanceof short[]) {
            result += ((short[]) rawPixels_)[index];
         }
         // NB Java will let you use "<<=" in this situation.
         result = result << exponent;
      }
      return result;
   }

   @Override
   public String getIntensityStringAt(int x, int y) {
      if (numComponents_ == 1) {
         return String.format("%d", getIntensityAt(x, y));
      }
      else {
         String result = "[";
         for (int i = 0; i < numComponents_; ++i) {
            result += String.format("%d", getComponentIntensityAt(x, y, i));
            if (i != numComponents_ - 1) {
               result += "/";
            }
         }
         return result + "]";
      }
   }

   /**
    * Split this multi-component Image into several single-component Images
    * and add them to the Datastore. They will be positioned based on our
    * current position, with the channel incrementing by 1 for each new
    * component.
    */
   public List<Image> splitMultiComponentIntoStore(Datastore store) throws DatastoreLockedException {
      ArrayList<Image> result = new ArrayList<Image>();
      if (numComponents_ == 1) {
         // No need to do anything fancy.
         store.putImage(this);
         result.add(this);
         return result;
      }
      for (int i = 0; i < numComponents_; ++i) {
         Object pixels = getRawPixelsForComponent(i);
         Image newImage = new DefaultImage(pixels, pixelWidth_, pixelHeight_,
               bytesPerPixel_ / numComponents_, 1,
               coords_.copy().position("channel", coords_.getPositionAt("channel") + i).build(),
               metadata_);
         store.putImage(newImage);
         result.add(newImage);
      }
      return result;
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
      return pixelWidth_;
   }

   @Override
   public int getHeight() {
      return pixelHeight_;
   }

   @Override
   public int getBytesPerPixel() {
      return bytesPerPixel_;
   }

   @Override
   public int getNumComponents() {
      return numComponents_;
   }

   public String toString() {
      return String.format("<%dx%d image at %s>", getWidth(), getHeight(), coords_);
   }
}

package org.micromanager.data.internal;

import ij.ImagePlus;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import mmcorej.TaggedImage;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreLockedException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.internal.utils.DirectBuffers;
import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class represents a single image from a single camera. It contains
 * the image pixel data, metadata (in the form of a Metadata instance), and
 * the image's index as part of a larger dataset (in the form of an
 * Coords instance).
 *
 * For efficiency during high-speed acquisitions, we store the image data in
 * a ByteBuffer or ShortBuffer. However, ImageJ wants to work with image data
 * in the form of byte[], short[], or int[] arrays (depending on pixel type).
 * getRawPixels(), the method exposed in the Image interface to access pixel
 * data, returns an ImageJ-style array, while getPixelBuffer (which is not
 * exposed in the API) returns the raw buffer.
 * TODO: add method to generate an ImagePlus from the image.
 */
public class DefaultImage implements Image {
   private DefaultMetadata metadata_;
   private Coords coords_;
   private Buffer rawPixels_;
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
    * @param tagged A TaggedImage to base the Image on.
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
         builder.elapsedTimeMs(MDUtils.getElapsedTimeMs(tags));
      }
      catch (JSONException e) {}
      try {
         builder.comments(MDUtils.getComments(tags));
      }
      catch (JSONException e) {}
      try {
         builder.imageNumber(MDUtils.getSequenceNumber(tags));
      }
      catch (JSONException e) {}
      metadata_ = builder.build();

      DefaultCoords.Builder cBuilder = new DefaultCoords.Builder();
      try {
         cBuilder.time(MDUtils.getFrameIndex(tags));
      }
      catch (JSONException e) {}
      try {
         cBuilder.stagePosition(MDUtils.getPositionIndex(tags));
      }
      catch (JSONException e) {}
      try {
         cBuilder.z(MDUtils.getSliceIndex(tags));
      }
      catch (JSONException e) {}
      try {
         cBuilder.channel(MDUtils.getChannelIndex(tags));
      }
      catch (JSONException e) {}
      coords_ = cBuilder.build();

      rawPixels_ = DirectBuffers.bufferFromArray(tagged.pix);
      if (rawPixels_.capacity() == 0) {
         throw new IllegalArgumentException("Pixel data has length 0.");
      }
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
      metadata_ = (DefaultMetadata) metadata;
      if (metadata_ == null) {
         // Don't allow images with null metadata.
         metadata_ = new DefaultMetadata.Builder().build();
      }
      coords_ = coords;

      rawPixels_ = DirectBuffers.bufferFromArray(pixels);
      if (rawPixels_.capacity() == 0) {
         throw new IllegalArgumentException("Pixel data has length 0.");
      }
      pixelWidth_ = width;
      pixelHeight_ = height;
      bytesPerPixel_ = bytesPerPixel;
      numComponents_ = numComponents;
   }

   public DefaultImage(Image source, Coords coords, Metadata metadata) {
      metadata_ = (DefaultMetadata) metadata;
      coords_ = coords;
      if (source instanceof DefaultImage) {
         // Just copy their Buffer over directly.
         rawPixels_ = ((DefaultImage) source).getPixelBuffer();
      }
      else {
         rawPixels_ = DirectBuffers.bufferFromArray(source.getRawPixels());
      }
      if (rawPixels_.capacity() == 0) {
         throw new IllegalArgumentException("Pixel data has length 0.");
      }
      pixelWidth_ = source.getWidth();
      pixelHeight_ = source.getHeight();
      bytesPerPixel_ = source.getBytesPerPixel();
      numComponents_ = source.getNumComponents();
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
    * Note this returns a byte[], short[], or int[] array, not a ByteBuffer,
    * ShortBuffer, or IntBuffer. Use getPixelBuffer() for that.
    */
   @Override
   public Object getRawPixels() {
      return DirectBuffers.arrayFromBuffer(rawPixels_);
   }

   public Buffer getPixelBuffer() {
      return rawPixels_;
   }

   // This is a bit ugly, due to needing to examine the type of rawPixels_,
   // but what else can we do?
   @Override
   public Object getRawPixelsForComponent(int component) {
      Object result;
      int length;
      if (rawPixels_ instanceof ByteBuffer) {
         length = ((ByteBuffer) rawPixels_).capacity() / bytesPerPixel_;
         result = (Object) new byte[length];
      }
      else if (rawPixels_ instanceof ShortBuffer) {
         length = ((ShortBuffer) rawPixels_).capacity() / bytesPerPixel_;
         result = (Object) new short[length];
      }
      else {
         ReportingUtils.logError("Unrecognized pixel buffer type.");
         return null;
      }
      for (int i = 0; i < length; ++i) {
         int sourceIndex = i * bytesPerPixel_ + component;
         if (rawPixels_ instanceof ByteBuffer) {
            ((byte[]) result)[i] = ((ByteBuffer) rawPixels_).get(sourceIndex);
         }
         else if (rawPixels_ instanceof ShortBuffer) {
            ((short[]) result)[i] = ((ShortBuffer) rawPixels_).get(sourceIndex);
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
      int pixelIndex = y * pixelWidth_ + x + component;
      if (pixelIndex < 0 || pixelIndex >= rawPixels_.capacity()) {
         throw new IllegalArgumentException(
               String.format("Asked for pixel at (%d, %d) outside of pixel array size of %d", x, y, rawPixels_.capacity()));
      }
      long result = 0;
      int divisor = numComponents_;
      int exponent = 8;
      if (rawPixels_ instanceof ShortBuffer) {
         divisor *= 2;
         exponent = 16;
      }
      for (int i = 0; i < bytesPerPixel_ / divisor; ++i) {
         // NB Java will let you use "<<=" in this situation.
         result = result << exponent;
         int index = y * pixelWidth_ + x + component + i;
         // Java doesn't have unsigned number types, so we have to manually
         // convert; otherwise large numbers will set the sign bit and show
         // as negative.
         int addend = 0;
         if (rawPixels_ instanceof ByteBuffer) {
            addend = ImageUtils.unsignedValue(
                  ((ByteBuffer) rawPixels_).get(index));
         }
         else if (rawPixels_ instanceof ShortBuffer) {
            addend = ImageUtils.unsignedValue(
                  ((ShortBuffer) rawPixels_).get(index));
         }
         result += addend;
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
    * current index, with the channel incrementing by 1 for each new
    * component.
    * TODO: this will work horribly if there are any cameras located "after"
    * this camera along the channel axis, since it blindly inserts new images
    * at C, C+1...C+N where C is its channel index and N is the number of
    * components.
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
               coords_.copy().channel(coords_.getChannel() + i).build(),
               metadata_);
         store.putImage(newImage);
         result.add(newImage);
      }
      return result;
   }

   /**
    * For backwards compatibility, convert to TaggedImage.
    */
   public TaggedImage legacyToTaggedImage() {
      JSONObject tags = metadata_.toJSON();
      // Fill in fields that we know about and that our metadata doesn't.
      try {
         MDUtils.setFrameIndex(tags, coords_.getTime());
         MDUtils.setSliceIndex(tags, coords_.getZ());
         MDUtils.setChannelIndex(tags, coords_.getChannel());
         MDUtils.setPositionIndex(tags, coords_.getStagePosition());
         int type = getImageJPixelType();
         MDUtils.setPixelType(tags, type);
         // Create a redundant copy of index information in a format that
         // lets us store all axis information.
         JSONObject fullCoords = new JSONObject();
         for (String axis : coords_.getAxes()) {
            fullCoords.put(axis, coords_.getIndex(axis));
         }
         tags.put("completeCoords", fullCoords);
      }
      catch (JSONException e) {
         ReportingUtils.logError("Unable to set image indices: " + e);
      }
      return new TaggedImage(getRawPixels(), tags);
   }

   public int getImageJPixelType() {
      int bytesPerPixel = getBytesPerPixel();
      int numComponents = getNumComponents();
      if (bytesPerPixel == 4) {
         if (numComponents == 3) {
            return ImagePlus.COLOR_RGB;
         }
         else {
            return ImagePlus.GRAY32;
         }
      }
      else if (bytesPerPixel == 2) {
         return ImagePlus.GRAY16;
      }
      else if (bytesPerPixel == 1) {
         return ImagePlus.GRAY8;
      }
      ReportingUtils.logError(String.format("Unrecognized pixel type with %d bytes per pixel and %d components", bytesPerPixel, numComponents));
      return -1;
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

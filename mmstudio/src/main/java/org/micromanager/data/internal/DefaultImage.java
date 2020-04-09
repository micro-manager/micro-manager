///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.data.internal;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import mmcorej.TaggedImage;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class represents a single image from a single camera. It contains
 * the image pixel data, metadata (in the form of a Metadata instance), and
 * the image's index as part of a larger dataset (in the form of a Coords instance).
 *
 * We store the image data in a ByteBuffer or ShortBuffer. However, ImageJ
 * wants to work with image data in the form of byte[], short[], or
 * int[] arrays (depending on pixel type).
 * getRawPixels(), the method exposed in the Image interface to access pixel
 * data, returns an ImageJ-style array, while getPixelBuffer (which is not
 * exposed in the API) returns the raw buffer.
 */
public final class DefaultImage implements Image {
   private DefaultMetadata metadata_;
   private Coords coords_;
   private Buffer rawPixels_;

   // Width of the image, in pixels
   int pixelWidth_;
   // Height of the image, in pixels
   int pixelHeight_;

   private final PixelType pixelType_;

   /**
    * Generate a DefaultImage from a TaggedImage. Note that this method will
    * result in Micro-Manager assuming the image data came from
    * the microscope that is currently running (i.e. this is a bad method to use
    * for loading saved images). If you want to avoid that, then you need to
    * manually reconstruct the Metadata for the TaggedImage and use the
    * constructor that this method calls.
    *
    * PixelData from the TaggedImage will be used directly, i.e., they are not copied.
    * @param tagged A TaggedImage to base the Image on.
    */
   public DefaultImage(TaggedImage tagged) throws IllegalArgumentException {
      this(tagged, null, null);
   }

   /**
    * Generate a DefaultImage from a TaggedImage using the
    * supplied coords and metadata.
    *
    * PixelData from the TaggedImage will be used directly, i.e., they are not copied.
    *
    * @param tagged A TaggedImage to base the Image on. Pixels are not copied.
    * @param coords Coords to be used for this new Image. When null, tags in input image
    *               are interpreted to best guess the desired Coord.
    * @param metadata Metadata to be used for this new Image. When null, tags from input image
    *                 are used to construct metadata.
    */
   public DefaultImage(TaggedImage tagged, Coords coords, Metadata metadata)
         throws IllegalArgumentException {
      String json = tagged.tags.toString();
      JsonElement je;
      try {
         je = new JsonParser().parse(json);
      }
      catch (Exception unlikely) {
         throw new IllegalArgumentException("Failed to parse JSON created from TaggedImage tags", unlikely);
      }

      if (metadata == null) {
         try {
            metadata = DefaultMetadata.fromPropertyMap(
                    NonPropertyMapJSONFormats.metadata().fromGson(je));
         }
         catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert TaggedImage tags to metadata", e);
         }
      }

      if (coords == null) {
         try {
            PropertyMap pmap = NonPropertyMapJSONFormats.coords().fromGson(je);
            coords = Coordinates.fromPropertyMap(pmap);
         }
         catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert TaggedImage tags to coords", e);
         }
      }

      PropertyMap formatPmap;
      try {
         formatPmap = NonPropertyMapJSONFormats.imageFormat().fromGson(je);
      }
      catch (Exception e) {
         throw new IllegalArgumentException("Failed to convert TaggedImage tags to image size and pixel format");
      }

      metadata_ = (DefaultMetadata) metadata;
      coords_ = coords;

      pixelWidth_ = formatPmap.getInteger(PropertyKey.WIDTH.key(), 0);
      pixelHeight_ = formatPmap.getInteger(PropertyKey.HEIGHT.key(), 0);
      if (pixelWidth_ <= 0 || pixelHeight_ <= 0) {
         throw new IllegalArgumentException("Zero or negative image size");
      }

      pixelType_ = formatPmap.getStringAsEnum(PropertyKey.PIXEL_TYPE.key(), PixelType.class, null);
      if (pixelType_ == null) {
         throw new IllegalArgumentException("Missing pixel type");
      }

      rawPixels_ = BufferTools.wrapArray(tagged.pix, pixelType_.getBytesPerComponent());

      if (rawPixels_.capacity() == 0) {
         throw new IllegalArgumentException("Pixel data has length 0");
      }
   }

   /**
    * Generates a DefaultImage from pixels, image info in a PropertyMap and the
    * supplied coords and metadata.
    *
    * Input pixels will be used directly (i.e., they are not copied).
    *
    * @param pixels Image pixels.  Should be a Java array of bytes or shorts (not null).
    * @param format PropertyMap specifying image width, height, and pixelType (not null).
    * @param coords Coords to be used for this new image (can be null).
    * @param metadata Metadata to be used this new image (can be null).
    * @throws IllegalArgumentException
    */
   public DefaultImage(Object pixels, PropertyMap format, Coords coords,
         Metadata metadata) throws IllegalArgumentException {
      this(pixels,
              format.getInteger(PropertyKey.WIDTH.key(), 0),
              format.getInteger(PropertyKey.HEIGHT.key(), 0),
              format.getStringAsEnum(PropertyKey.PIXEL_TYPE.key(),
                      PixelType.class, null).getBytesPerPixel(),
              format.getStringAsEnum(PropertyKey.PIXEL_TYPE.key(),
                      PixelType.class, null).getNumberOfComponents(),
              coords,
              metadata);
   }

   /**
    * Generates a DefaultImage from pixels, minimal image info, and the
    * supplied coords and metadata.
    *
    * Input pixels will be used directly (i.e., they are not copied).
    *
    * @param pixels Image pixels.  Should be a Java array of bytes or shorts (not null).
    * @param coords Coords to be used for this new image (can be null).
    * @param metadata Metadata to be used this new image (can be null).
    * @throws IllegalArgumentException
    */
   public DefaultImage(Object pixels, int width, int height, int bytesPerPixel,
         int numComponents, Coords coords, Metadata metadata) 
         throws IllegalArgumentException {
      Preconditions.checkNotNull(pixels);
      metadata_ = metadata == null ? new DefaultMetadata.Builder().build() :
              (DefaultMetadata) metadata;
      coords_ = coords == null ? Coordinates.builder().build() : coords;

      int bpc;
      if (pixels instanceof byte[]) bpc = 1;
      else if (pixels instanceof short[]) bpc = 2;
      else if (pixels instanceof int[] &&  bytesPerPixel == 4 &&
              numComponents == 3) {
         bpc = 1;
      } else throw new UnsupportedOperationException("Unsupported pixel data type");

      rawPixels_ = BufferTools.wrapArray(pixels, bpc);

      if (rawPixels_ == null || rawPixels_.capacity() < width * height) {
         throw new IllegalArgumentException("Invalid pixel data " + pixels);
      }
      pixelWidth_ = width;
      pixelHeight_ = height;
      pixelType_ = PixelType.valueFor(bytesPerPixel, bpc, numComponents);
   }

   /**
    * Creates a new image object that shares the pixels of the source image
    * Attaches the provided coords and metadata
    * @param source Image to be copied
    * @param coords will be used for this image
    * @param metadata Metadat for this new image
    */
   public DefaultImage(Image source, Coords coords, Metadata metadata) {
      metadata_ = (DefaultMetadata) metadata;
      coords_ = coords;

      int bytesPerComponent = 0;
      if (source.getRawPixels() instanceof byte[]) { bytesPerComponent = 1; }
      else if (source.getRawPixels() instanceof short[]) { bytesPerComponent = 2; }
      rawPixels_ = BufferTools.wrapArray(source.getRawPixels(), bytesPerComponent);

      if (rawPixels_.capacity() == 0) {
         throw new IllegalArgumentException("Pixel data has length 0.");
      }
      pixelWidth_ = source.getWidth();
      pixelHeight_ = source.getHeight();

      int bpc;
      if (rawPixels_ instanceof ByteBuffer) bpc = 1;
      else if (rawPixels_ instanceof ShortBuffer) bpc = 2;
      else throw new UnsupportedOperationException("Unsupported pixel data type");
      pixelType_ = PixelType.valueFor(source.getBytesPerPixel(), bpc,
              source.getNumComponents());


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
      if (rawPixels_.hasArray()) {
          return rawPixels_.array();
      }
      return BufferTools.arrayFromBuffer(rawPixels_);
   }

   @Override
   public Object getRawPixelsCopy() {
      Object original = getRawPixels();
      Object copy;
      int length;
      if (original instanceof byte[]) {
         byte[] tmp = (byte[]) original;
         length = tmp.length;
         copy = new byte[length];
      }
      else if (original instanceof short[]) {
         short[] tmp = (short[]) original;
         length = tmp.length;
         copy = new short[length];
      }
      else if (original instanceof int[]) {
         int[] tmp = (int[]) original;
         length = tmp.length;
         copy = new int[length];
      }
      else {
         throw new RuntimeException("Unrecognized pixel type " + original.getClass());
      }
      System.arraycopy(original, 0, copy, 0, length);
      return copy;
   }

   public Buffer getPixelBuffer() {
      return rawPixels_;
   }

   // TODO Use ImgLib2
   @Override
   public Object getRawPixelsForComponent(int component) {
      int samplesPerPixel = pixelType_.getBytesPerPixel() / pixelType_.getBytesPerComponent();
      int length = rawPixels_.capacity() / samplesPerPixel;
      int offset = pixelType_.getComponentSampleOffset(component);
      Object result;
      if (rawPixels_ instanceof ByteBuffer) {
         result = (Object) new byte[length];
      }
      else if (rawPixels_ instanceof ShortBuffer) {
         result = (Object) new short[length];
      }
      else {
         ReportingUtils.logError("Unrecognized pixel buffer type.");
         return null;
      }
      for (int i = 0; i < length; ++i) {
         int sourceIndex = i * samplesPerPixel + offset;
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
      Preconditions.checkElementIndex(x, pixelWidth_);
      Preconditions.checkElementIndex(y, pixelHeight_);
      Preconditions.checkElementIndex(component, pixelType_.getNumberOfComponents());

      int samplesPerPixel = pixelType_.getBytesPerPixel() / pixelType_.getBytesPerComponent();
      int offset = pixelType_.getComponentSampleOffset(component);

      int pixelIndex = y * pixelWidth_ + x;
      int sampleIndex = pixelIndex * samplesPerPixel + offset;
      switch (pixelType_.getBytesPerComponent()) {
         case 1:
            return ImageUtils.unsignedValue(((ByteBuffer) rawPixels_).get(sampleIndex));
         case 2:
            return ImageUtils.unsignedValue(((ShortBuffer) rawPixels_).get(sampleIndex));
         default:
            throw new AssertionError("Unimplemented sample size");
      }
   }

   @Override
   public long[] getComponentIntensitiesAt(int x, int y) {
      int n = getNumComponents();
      long[] ret = new long[n];
      for (int i = 0; i < n; ++i) {
         ret[i] = getComponentIntensityAt(x, y, i);
      }
      return ret;
   }

   @Override
   public String getIntensityStringAt(int x, int y) {
      if (getNumComponents() == 1) {
         return String.format("%d", getIntensityAt(x, y));
      }
      else {
         String result = "(";
         for (int i = 0; i < getNumComponents(); ++i) {
            result += String.format("%d", getComponentIntensityAt(x, y, i));
            if (i != 0) {
               result += ", ";
            }
         }
         return result + ")";
      }
   }

   public PropertyMap formatToPropertyMap() {
      return PropertyMaps.builder().
            putInteger(PropertyKey.WIDTH.key(), getWidth()).
            putInteger(PropertyKey.HEIGHT.key(), getHeight()).
            putEnumAsString(PropertyKey.PIXEL_TYPE.key(), pixelType_).
            build();
   }

   /*
    * Needed for file I/O code. TODO Delete
    */
   @Deprecated
   public TaggedImage legacyToTaggedImage() {
      JsonObject jo = new JsonObject();
      NonPropertyMapJSONFormats.imageFormat().addToGson(jo,
            formatToPropertyMap());
      NonPropertyMapJSONFormats.coords().addToGson(jo,
            ((DefaultCoords) coords_).toPropertyMap());
      NonPropertyMapJSONFormats.metadata().addToGson(jo,
            metadata_.toPropertyMap());
      Gson gson = new GsonBuilder().disableHtmlEscaping().create();
      String json = gson.toJson(jo);

      JSONObject tags;
      try {
         tags = new JSONObject(json);
      }
      catch (JSONException e) {
         throw new AssertionError("Json.org failed to parse Gson-generated JSON");
      }
      return new TaggedImage(getRawPixels(), tags);
   }

   public PixelType getPixelType() {
      return pixelType_;
   }

   @Override
   @Deprecated
   public int getImageJPixelType() {
      return pixelType_.imageJConstant();
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
      return pixelType_.getBytesPerPixel();
   }

   @Override
   public int getBytesPerComponent() {
      return pixelType_.getBytesPerComponent();
   }

   @Override
   public int getNumComponents() {
      return pixelType_.getNumberOfComponents();
   }

   @Override
   public String toString() {
      return String.format("<%dx%dx%d image (%d bytes per pixel) at %s>",
            getWidth(), getHeight(), getNumComponents(),
            getBytesPerPixel(), getCoords());
   }
}
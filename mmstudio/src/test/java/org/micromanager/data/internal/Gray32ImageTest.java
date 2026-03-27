package org.micromanager.data.internal;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.display.internal.imagestats.BoundsRectAndMask;
import org.micromanager.display.internal.imagestats.ImageStats;
import org.micromanager.display.internal.imagestats.ImageStatsProcessor;
import org.micromanager.display.internal.imagestats.ImageStatsRequest;
import org.micromanager.display.internal.imagestats.ImagesAndStats;

/**
 * Tests for GRAY32 (32-bit float) image support.
 * All tests in this class should FAIL before the GRAY32 implementation
 * and PASS after it.
 */
public class Gray32ImageTest {

   // ------------------------------------------------------------------
   // PixelType enum tests
   // ------------------------------------------------------------------

   @Test
   public void testPixelTypeValueFor() {
      PixelType pt = PixelType.valueFor(4, 4, 1);
      Assert.assertEquals(PixelType.GRAY32, pt);
   }

   @Test
   public void testPixelTypeValueOfImageJConstant() {
      // ImagePlus.GRAY32 == 2
      PixelType pt = PixelType.valueOfImageJConstant(2);
      Assert.assertEquals(PixelType.GRAY32, pt);
   }

   @Test
   public void testPixelTypeImageJConstant() {
      Assert.assertEquals(2, PixelType.GRAY32.imageJConstant());
   }

   @Test
   public void testPixelTypeDimensions() {
      Assert.assertEquals(4, PixelType.GRAY32.getBytesPerPixel());
      Assert.assertEquals(4, PixelType.GRAY32.getBytesPerComponent());
      Assert.assertEquals(1, PixelType.GRAY32.getNumberOfComponents());
   }

   // ------------------------------------------------------------------
   // DefaultImage construction tests
   // ------------------------------------------------------------------

   private static float[] makeTestPixels(int w, int h) {
      float[] px = new float[w * h];
      // Known values including negative, fractional, zero, and large
      px[0] = -1.5f;
      px[1] = 0.001f;
      px[2] = 0.0f;
      px[3] = 1000000.0f;
      px[4] = Float.MAX_VALUE / 2;
      // Fill rest with pattern
      for (int i = 5; i < px.length; i++) {
         px[i] = i * 0.5f;
      }
      return px;
   }

   private static DefaultImage makeFloatImage(int w, int h) {
      float[] px = makeTestPixels(w, h);
      Coords coords = new DefaultCoords.Builder().build();
      Metadata metadata = new DefaultMetadata.Builder().build();
      return new DefaultImage(px, w, h, 4, 1, coords, metadata);
   }

   @Test
   public void testFloatImageConstruction() {
      // Should not throw
      DefaultImage img = makeFloatImage(16, 16);
      Assert.assertNotNull(img);
   }

   @Test
   public void testFloatImageGetRawPixelsReturnsFloatArray() {
      DefaultImage img = makeFloatImage(16, 16);
      Object pixels = img.getRawPixels();
      Assert.assertTrue("getRawPixels() should return float[]",
            pixels instanceof float[]);
   }

   @Test
   public void testFloatImageDimensions() {
      DefaultImage img = makeFloatImage(16, 24);
      Assert.assertEquals(16, img.getWidth());
      Assert.assertEquals(24, img.getHeight());
      Assert.assertEquals(4, img.getBytesPerPixel());
      Assert.assertEquals(1, img.getNumComponents());
   }

   @Test
   public void testFloatImagePixelType() {
      DefaultImage img = makeFloatImage(16, 16);
      Assert.assertEquals(PixelType.GRAY32, img.getPixelType());
   }

   // ------------------------------------------------------------------
   // Pixel access tests
   // ------------------------------------------------------------------

   @Test
   public void testFloatImageGetComponentIntensityAt() {
      int w = 8, h = 8;
      float[] px = new float[w * h];
      px[0] = 42.9f;
      px[1] = -7.3f;
      px[w + 3] = 100.0f;
      Coords coords = new DefaultCoords.Builder().build();
      Metadata metadata = new DefaultMetadata.Builder().build();
      DefaultImage img = new DefaultImage(px, w, h, 4, 1, coords, metadata);

      // getComponentIntensityAt returns (long) cast of float value
      Assert.assertEquals((long) 42.9f, img.getComponentIntensityAt(0, 0, 0));
      Assert.assertEquals((long) (-7.3f), img.getComponentIntensityAt(1, 0, 0));
      Assert.assertEquals((long) 100.0f, img.getComponentIntensityAt(3, 1, 0));
   }

   @Test
   public void testFloatImageGetIntensityStringContainsDecimalPoint() {
      int w = 4, h = 4;
      float[] px = new float[w * h];
      px[0] = 3.14f;
      Coords coords = new DefaultCoords.Builder().build();
      Metadata metadata = new DefaultMetadata.Builder().build();
      DefaultImage img = new DefaultImage(px, w, h, 4, 1, coords, metadata);

      String s = img.getIntensityStringAt(0, 0);
      Assert.assertTrue("Intensity string for float image should contain decimal point, got: " + s,
            s.contains("."));
   }

   // ------------------------------------------------------------------
   // Copy tests
   // ------------------------------------------------------------------

   @Test
   public void testFloatImageGetRawPixelsCopyReturnsFloatArray() {
      DefaultImage img = makeFloatImage(8, 8);
      Object copy = img.getRawPixelsCopy();
      Assert.assertTrue("getRawPixelsCopy() should return float[]",
            copy instanceof float[]);
   }

   @Test
   public void testFloatImageGetRawPixelsCopyIsIndependent() {
      float[] original = makeTestPixels(8, 8);
      Coords coords = new DefaultCoords.Builder().build();
      Metadata metadata = new DefaultMetadata.Builder().build();
      DefaultImage img = new DefaultImage(original, 8, 8, 4, 1, coords, metadata);

      float[] copy = (float[]) img.getRawPixelsCopy();
      Assert.assertNotSame("Copy should be a different array", original, copy);
      Assert.assertArrayEquals("Copy should have same values", original, copy, 0.0f);
   }

   // ------------------------------------------------------------------
   // TIFF round-trip tests
   // ------------------------------------------------------------------

   @Test
   public void testFloatImageTiffRoundTrip() throws Exception {
      int w = 512, h = 512;
      float[] original = makeTestPixels(w, h);
      Coords coords = new DefaultCoords.Builder().build();
      Metadata metadata = new DefaultMetadata.Builder().build();
      Image image = new DefaultImage(original, w, h, 4, 1, coords, metadata);

      File tempDir = Files.createTempDir();
      String path = tempDir.getPath() + "/float_test";

      // Write
      DefaultDatastore writeStore = new DefaultDatastore(null);
      StorageMultipageTiff writeStorage = new StorageMultipageTiff(
            null, writeStore, path, true, false, false);
      writeStore.setStorage(writeStorage);
      writeStore.setSummaryMetadata(new DefaultSummaryMetadata.Builder().build());
      writeStore.putImage(image);
      writeStore.freeze();
      writeStorage.close();

      // Read back
      DefaultDatastore readStore = new DefaultDatastore(null);
      StorageMultipageTiff readStorage = new StorageMultipageTiff(
            null, readStore, path, false, false, false);

      Image loadedImage = readStorage.getImage(coords);
      Assert.assertNotNull("Loaded image should not be null", loadedImage);
      Assert.assertEquals(4, loadedImage.getBytesPerPixel());
      Assert.assertEquals(1, loadedImage.getNumComponents());

      Object loadedPixels = loadedImage.getRawPixels();
      Assert.assertTrue("Loaded pixels should be float[]", loadedPixels instanceof float[]);

      float[] loadedFloats = (float[]) loadedPixels;
      Assert.assertEquals("Pixel count mismatch", original.length, loadedFloats.length);
      for (int i = 0; i < original.length; i++) {
         Assert.assertEquals("Pixel " + i + " mismatch", original[i], loadedFloats[i], 0.0f);
      }

      readStorage.close();
   }

   @Test
   public void testFloatTiffHasSampleFormatTag() throws Exception {
      int w = 512, h = 512;
      float[] pixels = new float[w * h];
      for (int i = 0; i < pixels.length; i++) pixels[i] = i;
      Coords coords = new DefaultCoords.Builder().build();
      Metadata metadata = new DefaultMetadata.Builder().build();
      Image image = new DefaultImage(pixels, w, h, 4, 1, coords, metadata);

      File tempDir = Files.createTempDir();
      String path = tempDir.getPath() + "/sample_format_test";

      DefaultDatastore writeStore = new DefaultDatastore(null);
      StorageMultipageTiff writeStorage = new StorageMultipageTiff(
            null, writeStore, path, true, false, false);
      writeStore.setStorage(writeStorage);
      writeStore.setSummaryMetadata(new DefaultSummaryMetadata.Builder().build());
      writeStore.putImage(image);
      writeStore.freeze();
      writeStorage.close();

      // Find the TIFF file and scan for SampleFormat tag (339 = 0x0153)
      File tiffFile = new File(path + "/NDTiff.tif");
      if (!tiffFile.exists()) {
         // Try single-file naming
         File[] files = new File(path).listFiles(
               f -> f.getName().endsWith(".tif") || f.getName().endsWith(".tiff"));
         Assert.assertNotNull("No TIFF files found in " + path, files);
         Assert.assertTrue("No TIFF files found in " + path, files.length > 0);
         tiffFile = files[0];
      }

      boolean foundSampleFormat3 = scanTiffForSampleFormat(tiffFile, 3);
      Assert.assertTrue("TIFF should contain SampleFormat=3 (IEEE float) tag", foundSampleFormat3);
   }

   /**
    * Scan a TIFF file's IFDs looking for SampleFormat tag (339) with a given value.
    * Returns true if found.
    */
   private boolean scanTiffForSampleFormat(File file, int expectedValue) throws IOException {
      try (FileChannel ch = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
         ByteBuffer header = ByteBuffer.allocate(8);
         ch.read(header, 0);
         header.rewind();

         // Byte order
         ByteOrder order;
         short bom = header.getShort();
         if (bom == 0x4949) {
            order = ByteOrder.LITTLE_ENDIAN;
         } else if (bom == 0x4D4D) {
            order = ByteOrder.BIG_ENDIAN;
         } else {
            return false; // Not a TIFF
         }
         header.order(order);
         header.position(4);
         long ifdOffset = Integer.toUnsignedLong(header.getInt());

         // Walk IFDs
         while (ifdOffset != 0 && ifdOffset < ch.size()) {
            ByteBuffer countBuf = ByteBuffer.allocate(2).order(order);
            ch.read(countBuf, ifdOffset);
            countBuf.rewind();
            int numEntries = Short.toUnsignedInt(countBuf.getShort());

            int entrySize = 12;
            ByteBuffer entries = ByteBuffer.allocate(numEntries * entrySize).order(order);
            ch.read(entries, ifdOffset + 2);
            entries.rewind();

            for (int i = 0; i < numEntries; i++) {
               int tag = Short.toUnsignedInt(entries.getShort());
               int type = Short.toUnsignedInt(entries.getShort());
               int count = entries.getInt();
               int valueOrOffset = entries.getInt();

               // SampleFormat tag = 339 (0x0153); type 3 = SHORT
               if (tag == 339 && type == 3 && count == 1) {
                  // For SHORT type with count=1, value is left-justified in the 4-byte field
                  int value = (order == ByteOrder.LITTLE_ENDIAN)
                        ? (valueOrOffset & 0xFFFF)
                        : (valueOrOffset >>> 16);
                  if (value == expectedValue) {
                     return true;
                  }
               }
            }

            // Read next IFD offset
            ByteBuffer nextBuf = ByteBuffer.allocate(4).order(order);
            ch.read(nextBuf, ifdOffset + 2 + numEntries * entrySize);
            nextBuf.rewind();
            ifdOffset = Integer.toUnsignedLong(nextBuf.getInt());
         }
      }
      return false;
   }

   // ------------------------------------------------------------------
   // ImageStats tests
   // ------------------------------------------------------------------

   @Test
   public void testFloatImageStatsNonNull() throws Exception {
      int w = 8, h = 8;
      float[] pixels = new float[w * h];
      for (int i = 0; i < pixels.length; i++) pixels[i] = i * 10.5f;
      Coords coords = new DefaultCoords.Builder().build();
      Metadata metadata = new DefaultMetadata.Builder().build();
      Image image = new DefaultImage(pixels, w, h, 4, 1, coords, metadata);

      ImageStatsProcessor processor = ImageStatsProcessor.create();
      try {
         ImageStatsRequest request = ImageStatsRequest.create(
               coords,
               Collections.singletonList(image),
               BoundsRectAndMask.unselected());
         ImagesAndStats result = processor.process(1L, request, false);
         Assert.assertNotNull("process() should return non-null", result);
         ImageStats stats = result.getResult().get(0);
         Assert.assertNotNull("ImageStats should not be null for float image", stats);
         Assert.assertTrue("Pixel count should be > 0",
               stats.getComponentStats(0).getPixelCount() > 0);
         Assert.assertEquals("Should have 1 component", 1, stats.getNumberOfComponents());
      } finally {
         processor.shutdown();
      }
   }

   @Test
   public void testFloatImageStatsExcludesNaN() throws Exception {
      int w = 4, h = 4;
      float[] pixels = new float[w * h];
      int nanCount = 0;
      for (int i = 0; i < pixels.length; i++) {
         if (i % 4 == 0) {
            pixels[i] = Float.NaN;
            nanCount++;
         } else {
            pixels[i] = i * 1.0f;
         }
      }
      int expectedCount = pixels.length - nanCount;

      Coords coords = new DefaultCoords.Builder().build();
      Metadata metadata = new DefaultMetadata.Builder().build();
      Image image = new DefaultImage(pixels, w, h, 4, 1, coords, metadata);

      ImageStatsProcessor processor = ImageStatsProcessor.create();
      try {
         ImageStatsRequest request = ImageStatsRequest.create(
               coords,
               Collections.singletonList(image),
               BoundsRectAndMask.unselected());
         ImagesAndStats result = processor.process(1L, request, false);
         ImageStats stats = result.getResult().get(0);
         Assert.assertNotNull(stats);
         Assert.assertEquals("NaN pixels should be excluded from count",
               expectedCount, stats.getComponentStats(0).getPixelCount());
      } finally {
         processor.shutdown();
      }
   }

   // ------------------------------------------------------------------
   // Regression: existing pixel types still work
   // ------------------------------------------------------------------

   @Test
   public void testGray8TiffRoundTrip() throws Exception {
      byte[] px = new byte[512 * 512];
      for (int i = 0; i < px.length; i++) px[i] = (byte) i;
      Coords coords = new DefaultCoords.Builder().build();
      Metadata metadata = new DefaultMetadata.Builder().build();
      Image image = new DefaultImage(px, 512, 512, 1, 1, coords, metadata);
      try {
         File tempDir = Files.createTempDir();
         String path = tempDir.getPath() + "/g8";
         DefaultDatastore writeStore = new DefaultDatastore(null);
         StorageMultipageTiff writeStorage = new StorageMultipageTiff(
               null, writeStore, path, true, false, false);
         writeStore.setStorage(writeStorage);
         writeStore.setSummaryMetadata(new DefaultSummaryMetadata.Builder().build());
         writeStore.putImage(image);
         writeStore.freeze();
         writeStorage.close();

         DefaultDatastore readStore = new DefaultDatastore(null);
         StorageMultipageTiff readStorage = new StorageMultipageTiff(
               null, readStore, path, false, false, false);
         Image loadedImg = readStorage.getImage(coords);
         Assert.assertNotNull(loadedImg);
         Assert.assertEquals(1, loadedImg.getBytesPerPixel());
         Assert.assertArrayEquals(px, (byte[]) loadedImg.getRawPixels());
         readStorage.close();
      } catch (Exception e) {
         Assert.fail("GRAY8 TIFF round-trip failed: " + e);
      }
   }

   @Test
   public void testGray16TiffRoundTrip() throws Exception {
      short[] px = new short[512 * 512];
      for (int i = 0; i < px.length; i++) px[i] = (short) (i * 100);
      Coords coords = new DefaultCoords.Builder().build();
      Metadata metadata = new DefaultMetadata.Builder().build();
      Image image = new DefaultImage(px, 512, 512, 2, 1, coords, metadata);
      try {
         File tempDir = Files.createTempDir();
         String path = tempDir.getPath() + "/g16";
         DefaultDatastore writeStore = new DefaultDatastore(null);
         StorageMultipageTiff writeStorage = new StorageMultipageTiff(
               null, writeStore, path, true, false, false);
         writeStore.setStorage(writeStorage);
         writeStore.setSummaryMetadata(new DefaultSummaryMetadata.Builder().build());
         writeStore.putImage(image);
         writeStore.freeze();
         writeStorage.close();

         DefaultDatastore readStore = new DefaultDatastore(null);
         StorageMultipageTiff readStorage = new StorageMultipageTiff(
               null, readStore, path, false, false, false);
         Image loadedImg = readStorage.getImage(coords);
         Assert.assertNotNull(loadedImg);
         Assert.assertEquals(2, loadedImg.getBytesPerPixel());
         Assert.assertArrayEquals(px, (short[]) loadedImg.getRawPixels());
         readStorage.close();
      } catch (Exception e) {
         Assert.fail("GRAY16 TIFF round-trip failed: " + e);
      }
   }
}

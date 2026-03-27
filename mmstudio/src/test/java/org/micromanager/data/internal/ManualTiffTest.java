///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data testing
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
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

import com.google.common.io.Files;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Test;
import org.micromanager.MultiStagePosition;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.internal.MMStudio;

/**
 * This class tests reading and writing TIFFs from manual acquisitions.
 */
public class ManualTiffTest {
   /**
    * Maps file paths to expected summary metadatas.
    */
   private static final HashMap<String, SummaryMetadata> SUMMARIES;
   /**
    * Maps file paths to descriptors of the images we expect to find there.
    * We test the image metadata and spot-test the image data.
    */
   private static final HashMap<String, ArrayList<HelperImageInfo>> IMAGES;

   /**
    * Path for one of the file sets we use. This is a manually-created (i.e.
    * not using MDA) singleplane TIFF acquisition.
    */
   private static final String ALPHA2_PATH = java.nio.file.Paths.get(
         System.getProperty("user.dir"),
         "src", "test", "resources", "org", "micromanager", "data", "internal",
         "alpha_2.0_singleplane_manual").toString();

   private static final String COMMENT_KEY = "comment";
   private static final String IMAGE_COMMENT = "This is an image comment";
   private static final String SUMMARY_COMMENT = "This is a summary comment";

   static {
      SUMMARIES = new HashMap<>();
      IMAGES = new HashMap<>();

      DefaultSummaryMetadata.Builder summary = new DefaultSummaryMetadata.Builder();
      summary.prefix("thisIsAPrefix")
         .userName("John Doe").profileName("John's Profile")
         //.microManagerVersion("made-up version")
         //.metadataVersion("manual metadata")
         .computerName("my arduino")
         .directory("/dev/null")
         .channelGroup("Some channel group")
         .channelNames(new String[] {"Alpha", "Beta", "Romeo"})
         .zStepUm(123456789.012345).waitInterval(-1234.5678)
         .customIntervalsMs(new Double[] {12.34, 56.78})
         .axisOrder(new String[] {"axis5", "axis3", "axis97"})
         .intendedDimensions((new DefaultCoords.Builder()).index("axis5", 2).index("axis3", 9).index("axis97", 8).build())
         .startDate("The age of Aquarius")
         .stagePositions(new MultiStagePosition[] {
            new MultiStagePosition("some xy stage", 24.3, 43.2, "some z stage", 1.01),
            new MultiStagePosition("some other xy stage", 99.8, 88.9, "some other z stage", 2.02)
         })
         .userData((PropertyMaps.builder()).putString("Ha ha I'm some user data", "and I'm the value").
                 putInteger("I'm a number", 42).build());
      SUMMARIES.put(ALPHA2_PATH, summary.build());

      DefaultCoords.Builder imageCoords = new DefaultCoords.Builder();
      imageCoords.c(0).p(0).t(0).z(0);
      DefaultMetadata.Builder imageMetadata = new DefaultMetadata.Builder();
      imageMetadata.binning(1).bitDepth(16)
         .positionName("Pos0").pixelSizeUm(1.0)
         .camera("My camera")
         .elapsedTimeMs(4334.3443)
         .exposureMs(new Double(8888))
       //.ijType(-5)
         .imageNumber(new Long(9999))
       // .keepShutterOpenChannels(false).keepShutterOpenSlices(true)
         .pixelAspect(.001).pixelSizeUm(new Double(55555555))
         .positionName("Pos0").receivedTime("1920-01-01 12:34:56")
         .ROI(new Rectangle(0, 0, 512, 512))
         // NB these are not exhaustive, because I can only take so much
         // data entry. We do not fail the test if the data we look at has
         // more information than what we look for, only for missing data
         // or mismatches.
         .scopeData((PropertyMaps.builder())
               .putString("Objective-Name", "DObjective")
               .putString("Path-HubID", "DHub")
               .putString("Core-Focus", "Z")
               .putString("Path-Label", "State-0")
               .putString("Camera-RotateImages", "0")
               .putString("Z-Name", "DStage")
               .putString("Camera-TestProperty1", "0.0000")
               .putString("Shutter-State", "0")
               .putString("Camera-Exposure", "10.0000").build())
         .userData((PropertyMaps.builder())
               .putStringList("Stooges",
                  new String[] {"Larry", "Moe", "Curly"})
               .putIntegerList("randoms", 42, 17)
               .putLong("fixed", new Long(4867))
               .putLongList("also fixed", new Long(48), new Long(67))
               .putDouble("random/100", .42)
               .putDoubleList("a key", 59.44, 44.59)
               .putBoolean("data entry is fun", false)
               .putBooleanList("have some bools", true, false, false, true).build())
         .uuid(new UUID(42, 17))
         .xPositionUm(new Double(128)).yPositionUm(new Double(256))
         .zPositionUm(new Double(512));

      IMAGES.put(ALPHA2_PATH, new ArrayList<HelperImageInfo>());
      IMAGES.get(ALPHA2_PATH).add(new HelperImageInfo(imageCoords.build(),
               imageMetadata.build(), -232768128));
   }

   private void testSummary(String path, SummaryMetadata summary) {
      SummaryMetadata ref = SUMMARIES.get(path);
      Assert.assertEquals("Summary prefix", ref.getPrefix(), summary.getPrefix());
      Assert.assertEquals("Summary user name", ref.getUserName(), summary.getUserName());
      Assert.assertEquals("Summary profile name", ref.getProfileName(), summary.getProfileName());
      Assert.assertEquals("Summary MM version", ref.getMicroManagerVersion(), summary.getMicroManagerVersion());
      Assert.assertEquals("Summary metadata version", ref.getMetadataVersion(), summary.getMetadataVersion());
      Assert.assertEquals("Summary computer name", ref.getComputerName(), summary.getComputerName());
      Assert.assertEquals("Summary directory", ref.getDirectory(), summary.getDirectory());
      Assert.assertEquals("Summary channel group", ref.getChannelGroup(), summary.getChannelGroup());
      Assert.assertArrayEquals("Summary channel name", ref.getChannelNames(), summary.getChannelNames());
      Assert.assertEquals("Summary Z-step", ref.getZStepUm(), summary.getZStepUm());
      Assert.assertEquals("Summary wait interval", ref.getWaitInterval(), summary.getWaitInterval());
      Assert.assertArrayEquals("Summary custom interval", ref.getCustomIntervalsMs(), summary.getCustomIntervalsMs());
      Assert.assertArrayEquals("Summary axis order", ref.getAxisOrder(), summary.getAxisOrder());
      Assert.assertEquals("Summary intended dims", ref.getIntendedDimensions(), summary.getIntendedDimensions());
      Assert.assertEquals("Summary start date", ref.getStartDate(), summary.getStartDate());
      Assert.assertArrayEquals("Summary stage positions", ref.getStagePositions(), summary.getStagePositions());
      Assert.assertEquals("Summary user data", ref.getUserData(), summary.getUserData());
   }

   private void testImages(String path, Datastore store) {
      for (HelperImageInfo info : IMAGES.get(path)) {
         info.test(store);
      }
   }
/*
   public void testComments(Datastore store) {
      try {
         Annotation annotation = store.loadAnnotation("comments.txt");
         Assert.assertEquals(annotation.getGeneralAnnotation().getString(
                  COMMENT_KEY, null), SUMMARY_COMMENT);
         Coords coords = new DefaultDataManager().getCoordsBuilder().time(0).stagePosition(0).channel(0).z(0).build();
         Assert.assertEquals(annotation.getImageAnnotation(coords).getString(
                  COMMENT_KEY, null), IMAGE_COMMENT);
      }
      catch (IOException e) {
         Assert.fail("Couldn't load comments annotation: " + e);
      }
   }
*/

   // Tests proper loading of a stored singleplane TIFF file.
   @Test
   public void testSinglePlaneTIFFLoad() {
      testTIFFSaveLoad(Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES);
   }

   @Test
   public void testMultipageTIFFSaveLoad() {
      testTIFFSaveLoad(Datastore.SaveMode.MULTIPAGE_TIFF);
   }

   @Test
   public void testSinglePlaneTIFFSaveLoadByte() {
      testTIFFSaveLoadPixelType(Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES, PixelTypeCase.GRAY8);
   }

   @Test
   public void testMultipageTIFFSaveLoadByte() {
      testTIFFSaveLoadPixelType(Datastore.SaveMode.MULTIPAGE_TIFF, PixelTypeCase.GRAY8);
   }

   @Test
   public void testSinglePlaneTIFFSaveLoadShort() {
      testTIFFSaveLoadPixelType(Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES, PixelTypeCase.GRAY16);
   }

   @Test
   public void testMultipageTIFFSaveLoadShort() {
      testTIFFSaveLoadPixelType(Datastore.SaveMode.MULTIPAGE_TIFF, PixelTypeCase.GRAY16);
   }

   @Test
   public void testSinglePlaneTIFFSaveLoadFloat() {
      testTIFFSaveLoadPixelType(Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES, PixelTypeCase.GRAY32);
   }

   @Test
   public void testMultipageTIFFSaveLoadFloat() {
      testTIFFSaveLoadPixelType(Datastore.SaveMode.MULTIPAGE_TIFF, PixelTypeCase.GRAY32);
   }

   @Test
   public void testSinglePlaneTIFFSaveLoadRGB() {
      testTIFFSaveLoadPixelType(Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES, PixelTypeCase.RGB32);
   }

   @Test
   public void testMultipageTIFFSaveLoadRGB() {
      testTIFFSaveLoadPixelType(Datastore.SaveMode.MULTIPAGE_TIFF, PixelTypeCase.RGB32);
   }

   private enum PixelTypeCase {
      GRAY8, GRAY16, GRAY32, RGB32
   }

   private Image makeTestImage(PixelTypeCase type, Coords coords, Metadata metadata) {
      final int width = 24;
      final int height = 16;
      switch (type) {
         case GRAY8: {
            byte[] pixels = new byte[width * height];
            for (int i = 0; i < height; ++i) {
               for (int j = 0; j < width; ++j) {
                  pixels[i * width + j] = (byte) (i * 10 + j);
               }
            }
            return new DefaultImage(pixels, width, height, 1, 1, coords, metadata);
         }
         case GRAY16: {
            short[] pixels = new short[width * height];
            for (int i = 0; i < height; ++i) {
               for (int j = 0; j < width; ++j) {
                  pixels[i * width + j] = (short) (i * 100 + j);
               }
            }
            return new DefaultImage(pixels, width, height, 2, 1, coords, metadata);
         }
         case GRAY32: {
            float[] pixels = new float[width * height];
            for (int i = 0; i < height; ++i) {
               for (int j = 0; j < width; ++j) {
                  pixels[i * width + j] = (i - height / 2) * 10.5f + j * 0.1f;
               }
            }
            return new DefaultImage(pixels, width, height, 4, 1, coords, metadata);
         }
         case RGB32: {
            int[] pixels = new int[width * height];
            for (int i = 0; i < height; ++i) {
               for (int j = 0; j < width; ++j) {
                  int r = (i * 5) & 0xFF;
                  int g = (j * 7) & 0xFF;
                  int b = ((i + j) * 3) & 0xFF;
                  pixels[i * width + j] = (r << 16) | (g << 8) | b;
               }
            }
            return new DefaultImage(pixels, width, height, 4, 3, coords, metadata);
         }
         default:
            throw new IllegalArgumentException("Unknown pixel type: " + type);
      }
   }

   private Metadata metadataForPixelType(PixelTypeCase type) {
      Metadata.Builder base = IMAGES.get(ALPHA2_PATH).get(0).getMetadata().copyBuilderWithNewUUID();
      switch (type) {
         case GRAY8:  return base.bitDepth(8).build();
         case GRAY16: return base.bitDepth(16).build();
         case GRAY32: return base.bitDepth(32).build();
         case RGB32:  return base.bitDepth(8).build();
         default:     return base.build();
      }
   }

   private void testTIFFSaveLoadPixelType(Datastore.SaveMode saveMode, PixelTypeCase type) {
      DefaultDataManager manager = new DefaultDataManager(MMStudio.getInstance());
      Coords coords = manager.getCoordsBuilder().z(0).time(0).channel(0).stagePosition(0).build();
      Metadata metadata = metadataForPixelType(type);
      Image image = makeTestImage(type, coords, metadata);
      HelperImageInfo info = new HelperImageInfo(coords, metadata,
            HelperImageInfo.hashPixels(image));

      Datastore store = manager.createRAMDatastore();
      try {
         store.setSummaryMetadata(SUMMARIES.get(ALPHA2_PATH));
         store.putImage(image);
      } catch (DatastoreFrozenException e) {
         Assert.fail("Unable to add images or set summary metadata: " + e);
      } catch (DatastoreRewriteException e) {
         Assert.fail("Unable to add images or set summary metadata: " + e);
      } catch (IOException io) {
         Assert.fail("IOException while adding image to store " + io);
      }

      File tempDir = Files.createTempDir();
      String path = tempDir.getPath() + "/test";
      try {
         store.save(saveMode, path);
         store.setSavePath(path);
      } catch (Exception e) {
         Assert.fail("IOException while saving store: " + e);
      }

      System.out.println("Loading " + type + " data from " + path);
      try {
         Datastore loadedStore = manager.loadData(path, true);
         info.test(loadedStore);
         loadedStore.freeze();
         loadedStore.close();
      } catch (IOException e) {
         Assert.fail("Unable to load " + type + " datastore: " + e);
      }
      tempDir.deleteOnExit();
   }

   // Creates a new multipage TIFF, saves it, loads it, and verifies the
   // results are as expected. For convenience, re-uses the summary metadata
   // and image metadata used by the testSinglePlaneTIFFLoad() method.
   public void testTIFFSaveLoad(Datastore.SaveMode saveMode) {
      DefaultDataManager manager = new DefaultDataManager(MMStudio.getInstance());
      Datastore store = manager.createRAMDatastore();
      // Manufacture an Image.
      short[] pixels = new short[16*24];
      HashMap<Coords, Integer> values = new HashMap<Coords, Integer>();
      for (int i = 0; i < 16; ++i) {
         for (int j = 0; j < 24; ++j) {
            pixels[i * 24 + j] = (short) (i * 100 + j);
         }
      }
      Coords.CoordsBuilder builder = manager.getCoordsBuilder();
      builder.z(0).time(0).channel(0).stagePosition(0);
      Metadata metadata = IMAGES.get(ALPHA2_PATH).get(0).getMetadata();
      Image image = new DefaultImage(pixels, 24, 16, 2, 1, builder.build(),
            metadata);
      HelperImageInfo info = new HelperImageInfo(builder.build(), metadata,
            HelperImageInfo.hashPixels(image));
      try {
         // need to set SummaryMetadata before inserting images
         SummaryMetadata summary = SUMMARIES.get(ALPHA2_PATH);
         store.setSummaryMetadata(summary);
         store.putImage(image);
      }
      catch (DatastoreFrozenException e) {
         Assert.fail("Unable to add images or set summary metadata: " + e);
      }
      catch (DatastoreRewriteException e) {
         Assert.fail("Unable to add images or set summary metadata: " + e);
      }
      catch (IOException io) {
         Assert.fail("IOException while adding image to store " + io);
      }
      //try {
         //Annotation annotation = store.loadAnnotation("comments.txt");
         PropertyMap.PropertyMapBuilder commentsBuilder = manager.getPropertyMapBuilder();
         //annotation.setImageAnnotation(image.getCoords(),
         //      commentsBuilder.putString(COMMENT_KEY, IMAGE_COMMENT).build());
         //annotation.setGeneralAnnotation(commentsBuilder.putString(COMMENT_KEY,
         //         SUMMARY_COMMENT).build());
      //}
      //catch (IOException e) {
      //   Assert.fail("Couldn't create comments annotation: " + e);
      //}
      File tempDir = Files.createTempDir();
      String path = tempDir.getPath() + "/test";
      try {
         store.save(saveMode, path);
         store.setSavePath(path);
      } catch (IOException io) {
         Assert.fail("IOException while saving store " + io);
      }

      System.out.println("Loading data from " + path);
      try {
         Datastore loadedStore = manager.loadData(path, true);
         testSummary(ALPHA2_PATH, loadedStore.getSummaryMetadata());
         info.test(loadedStore);
         loadedStore.freeze();
         loadedStore.close();
      }
      catch (IOException e) {
         Assert.fail("Unable to load newly-generated datastore: " + e);
      }
      tempDir.deleteOnExit();
   }
}

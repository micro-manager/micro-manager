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

import org.junit.Test;
import org.junit.Assert;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;

import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultDataManager;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultPropertyMap;
import org.micromanager.data.internal.DefaultSummaryMetadata;

import org.micromanager.MultiStagePosition;

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
   private static final HashMap<String, ArrayList<ImageInfo>> IMAGES;

   /**
    * Path for one of the file sets we use. This is a manually-created (i.e.
    * not using MDA) singleplane TIFF acquisition.
    */
   private static final String ALPHA2_PATH = System.getProperty("user.dir") + "/src/test/resources/org/micromanager/data/internal/alpha_2.0_singleplane_manual";

   private static class ImageInfo {
      private Coords coords_;
      private Metadata metadata_;
      // Maps x/y coordinates to the pixel intensity at that location.
      private HashMap<Coords, Integer> values_;
      public ImageInfo(Coords coords, Metadata metadata,
            HashMap<Coords, Integer> values) {
         coords_ = coords;
         metadata_ = metadata;
         values_ = values;
      }

      public Metadata getMetadata() {
         return metadata_;
      }

      public void test(Datastore store) {
         Image image = store.getImage(coords_);
         Assert.assertNotNull("Image at " + coords_ + " is null", image);
         for (Coords c : values_.keySet()) {
            Assert.assertEquals(
                  (int) values_.get(c),
                  image.getIntensityAt(c.getIndex("x"), c.getIndex("y")));
         }
         Metadata metadata = image.getMetadata();
         Assert.assertEquals(metadata_.getBinning(), metadata.getBinning());
         Assert.assertEquals(metadata_.getBitDepth(), metadata.getBitDepth());
         Assert.assertEquals(metadata_.getCamera(), metadata.getCamera());
         Assert.assertEquals(metadata_.getChannelName(), metadata.getChannelName());
         Assert.assertEquals(metadata_.getComments(), metadata.getComments());
         Assert.assertEquals(metadata_.getElapsedTimeMs(), metadata.getElapsedTimeMs());
         Assert.assertEquals(metadata_.getEmissionLabel(), metadata.getEmissionLabel());
         Assert.assertEquals(metadata_.getExcitationLabel(), metadata.getExcitationLabel());
         Assert.assertEquals(metadata_.getExposureMs(), metadata.getExposureMs());
         Assert.assertEquals(metadata_.getGridColumn(), metadata.getGridColumn());
         Assert.assertEquals(metadata_.getGridRow(), metadata.getGridRow());
         Assert.assertEquals(metadata_.getIjType(), metadata.getIjType());
         Assert.assertEquals(metadata_.getImageNumber(), metadata.getImageNumber());
         Assert.assertEquals(metadata_.getInitialPositionList(), metadata.getInitialPositionList());
         Assert.assertEquals(metadata_.getKeepShutterOpenChannels(), metadata.getKeepShutterOpenChannels());
         Assert.assertEquals(metadata_.getKeepShutterOpenSlices(), metadata.getKeepShutterOpenSlices());
         Assert.assertEquals(metadata_.getPixelAspect(), metadata.getPixelAspect());
         Assert.assertEquals(metadata_.getPixelSizeUm(), metadata.getPixelSizeUm());
         Assert.assertEquals(metadata_.getPixelType(), metadata.getPixelType());
         Assert.assertEquals(metadata_.getPositionName(), metadata.getPositionName());
         Assert.assertEquals(metadata_.getReceivedTime(), metadata.getReceivedTime());
         Assert.assertEquals(metadata_.getROI(), metadata.getROI());
         Assert.assertEquals(metadata_.getSource(), metadata.getSource());
         Assert.assertEquals(metadata_.getStartTimeMs(), metadata.getStartTimeMs());
         Assert.assertEquals(metadata_.getScopeData(), metadata.getScopeData());
         Assert.assertEquals(metadata_.getUserData(), metadata.getUserData());
         Assert.assertEquals(metadata_.getUUID(), metadata.getUUID());
         Assert.assertEquals(metadata_.getXPositionUm(), metadata.getXPositionUm());
         Assert.assertEquals(metadata_.getYPositionUm(), metadata.getYPositionUm());
         Assert.assertEquals(metadata_.getZPositionUm(), metadata.getZPositionUm());
      }
   }

   static {
      SUMMARIES = new HashMap<String, SummaryMetadata>();
      IMAGES = new HashMap<String, ArrayList<ImageInfo>>();

      DefaultSummaryMetadata.Builder summary = new DefaultSummaryMetadata.Builder();
      summary.name("alpha-2 manual").prefix("thisIsAPrefix")
         .userName("John Doe").profileName("John's Profile")
         .microManagerVersion("made-up version")
         .metadataVersion("manual metadata").computerName("my arduino")
         .directory("/dev/null")
         .comments("This is some manually made-up comments")
         .channelGroup("Some channel group")
         .channelNames(new String[] {"Alpha", "Beta", "Romeo"})
         .zStepUm(123456789.012345).waitInterval(-1234.5678)
         .customIntervalsMs(new Double[] {12.34, 56.78})
         .axisOrder(new String[] {"axis 5", "axis 3", "axis 97"})
         .intendedDimensions((new DefaultCoords.Builder()).index("axis 5", 2).index("axis 3", 9).index("axis 97", 8).build())
         .startDate("The age of Aquarius")
         .stagePositions(new MultiStagePosition[] {
            new MultiStagePosition("some xy stage", 24.3, 43.2, "some z stage", 1.01),
            new MultiStagePosition("some other xy stage", 99.8, 88.9, "some other z stage", 2.02)
         })
         .userData((new DefaultPropertyMap.Builder()).putString("Ha ha I'm some user data", "and I'm the value").putInt("I'm a number", 42).build());
      SUMMARIES.put(ALPHA2_PATH, summary.build());

      DefaultCoords.Builder imageCoords = new DefaultCoords.Builder();
      imageCoords.channel(0).stagePosition(0).time(0).z(0);
      DefaultMetadata.Builder imageMetadata = new DefaultMetadata.Builder();
      imageMetadata.binning(1).bitDepth(16).pixelType("GRAY16")
         .positionName("Pos0").pixelSizeUm(1.0)
         .camera("My camera").channelName("some channel")
         .comments("These are comments on an image!")
         .elapsedTimeMs(4334.3443).emissionLabel("whatever GFP outputs")
         .excitationLabel("something you should wear goggles for")
         .exposureMs(new Double(8888)).gridColumn(97).gridRow(79).ijType(-5)
         .imageNumber(new Long(9999))
         .initialPositionList(
               new MultiStagePosition("a third xy stage", 98, 76, "a third z stage", 54))
         .keepShutterOpenChannels(false).keepShutterOpenSlices(true)
         .pixelAspect(.001).pixelSizeUm(new Double(55555555))
         .pixelType("GRAY16").positionName("Pos0")
         .receivedTime("1920-01-01 12:34:56")
         .ROI(new Rectangle(0, 0, 512, 512)).source("A keyboard")
         .startTimeMs(new Double(1928))
         // NB these are not exhaustive, because I can only take so much
         // data entry. We do not fail the test if the data we look at has
         // more information than what we look for, only for missing data
         // or mismatches.
         .scopeData((new DefaultPropertyMap.Builder())
               .putString("Objective-Name", "DObjective")
               .putString("Path-HubID", "DHub")
               .putString("Core-Focus", "Z")
               .putString("Path-Label", "State-0")
               .putString("Camera-RotateImages", "0")
               .putString("Z-Name", "DStage")
               .putString("Camera-TestProperty1", "0.0000")
               .putString("Shutter-State", "0")
               .putString("Camera-Exposure", "10.0000").build())
         .userData((new DefaultPropertyMap.Builder())
               .putStringArray("Stooges",
                  new String[] {"Larry", "Moe", "Curly"})
               .putIntArray("randoms", new Integer[] {42, 17})
               .putLong("fixed", new Long(4867))
               .putLongArray("also fixed", new Long[] {new Long(48), new Long(67)})
               .putDouble("random/100", .42)
               .putDoubleArray("a key", new Double[] {59.44, 44.59})
               .putBoolean("data entry is fun", false)
               .putBooleanArray("have some bools", new Boolean[] {true, false, false, true}).build())
         .uuid(new UUID(42, 17))
         .xPositionUm(new Double(128)).yPositionUm(new Double(256))
         .zPositionUm(new Double(512));

      HashMap<Coords, Integer> values = new HashMap<Coords, Integer>();
      DefaultCoords.Builder coords = new DefaultCoords.Builder();
      values.put(coords.index("x", 0).index("y", 0).build(), 3276);
      values.put(coords.index("y", 8).build(), 5093);
      values.put(coords.index("x", 4).build(), 1459);
      values.put(coords.index("x", 11).build(), 3276);
      values.put(coords.index("y", 13).build(), 2065);
      IMAGES.put(ALPHA2_PATH, new ArrayList<ImageInfo>());
      IMAGES.get(ALPHA2_PATH).add(new ImageInfo(imageCoords.build(),
               imageMetadata.build(), values));
   }

   private void testSummary(String path, SummaryMetadata summary) {
      SummaryMetadata ref = SUMMARIES.get(path);
      Assert.assertEquals(ref.getName(), summary.getName());
      Assert.assertEquals(ref.getPrefix(), summary.getPrefix());
      Assert.assertEquals(ref.getUserName(), summary.getUserName());
      Assert.assertEquals(ref.getProfileName(), summary.getProfileName());
      Assert.assertEquals(ref.getMicroManagerVersion(), summary.getMicroManagerVersion());
      Assert.assertEquals(ref.getMetadataVersion(), summary.getMetadataVersion());
      Assert.assertEquals(ref.getComputerName(), summary.getComputerName());
      Assert.assertEquals(ref.getDirectory(), summary.getDirectory());
      Assert.assertEquals(ref.getComments(), summary.getComments());
      Assert.assertEquals(ref.getChannelGroup(), summary.getChannelGroup());
      Assert.assertArrayEquals(ref.getChannelNames(), summary.getChannelNames());
      Assert.assertEquals(ref.getZStepUm(), summary.getZStepUm());
      Assert.assertEquals(ref.getWaitInterval(), summary.getWaitInterval());
      Assert.assertArrayEquals(ref.getCustomIntervalsMs(), summary.getCustomIntervalsMs());
      Assert.assertArrayEquals(ref.getAxisOrder(), summary.getAxisOrder());
      Assert.assertEquals(ref.getIntendedDimensions(), summary.getIntendedDimensions());
      Assert.assertEquals(ref.getStartDate(), summary.getStartDate());
      Assert.assertArrayEquals(ref.getStagePositions(), summary.getStagePositions());
      Assert.assertEquals(ref.getUserData(), summary.getUserData());
   }

   private void testImages(String path, Datastore store) {
      for (ImageInfo info : IMAGES.get(path)) {
         info.test(store);
      }
   }

   // Tests proper loading of a stored singleplane TIFF file.
   @Test
   public void testSinglePlaneTIFFLoad() {
      DefaultDataManager manager = new DefaultDataManager();
      for (String path : SUMMARIES.keySet()) {
         try {
            Datastore data = manager.loadData(path, true);
            testSummary(path, data.getSummaryMetadata());
            testImages(path, data);
         }
         catch (IOException e) {
            Assert.fail("Unable to load required data file " + path);
         }
      }
   }

   // Creates a new multipage TIFF, saves it, loads it, and verifies the
   // results are as expected. For convenience, re-uses the summary metadata
   // and image metadata used by the testSinglePlaneTIFFLoad() method.
   @Test
   public void testMultipageTIFFSaveLoad() {
      DefaultDataManager manager = new DefaultDataManager();
      Datastore store = manager.createRAMDatastore();
      // Manufacture an Image.
      short[] pixels = new short[16*24];
      HashMap<Coords, Integer> values = new HashMap<Coords, Integer>();
      Coords.CoordsBuilder valBuilder = manager.getCoordsBuilder();
      for (int i = 0; i < 16; ++i) {
         valBuilder.index("y", i);
         for (int j = 0; j < 24; ++j) {
            valBuilder.index("x", j);
            short val = (short) (i * 100 + j);
            values.put(valBuilder.build(), new Integer(val));
            pixels[i * 24 + j] = val;
         }
      }
      Coords.CoordsBuilder builder = manager.getCoordsBuilder();
      builder.z(0).time(0).channel(0).stagePosition(0);
      Metadata metadata = IMAGES.get(ALPHA2_PATH).get(0).getMetadata();
      Image image = new DefaultImage(pixels, 24, 16, 2, 1, builder.build(),
            metadata);
      ImageInfo info = new ImageInfo(builder.build(), metadata, values);
      try {
         store.putImage(image);
         SummaryMetadata summary = SUMMARIES.get(ALPHA2_PATH);
         store.setSummaryMetadata(summary);
      }
      catch (DatastoreFrozenException e) {
         Assert.fail("Unable to add images or set summary metadata: " + e);
      }
      File tempDir = Files.createTempDir();
      store.save(Datastore.SaveMode.MULTIPAGE_TIFF, tempDir.getPath());
      store.setSavePath(tempDir.toString());
      store = null;

      System.out.println("Loading data from " + tempDir.getPath());
      try {
         Datastore loadedStore = manager.loadData(tempDir.getPath(), true);
         testSummary(ALPHA2_PATH, loadedStore.getSummaryMetadata());
         info.test(loadedStore);
      }
      catch (IOException e) {
         Assert.fail("Unable to load newly-generated datastore: " + e);
      }
   }
}

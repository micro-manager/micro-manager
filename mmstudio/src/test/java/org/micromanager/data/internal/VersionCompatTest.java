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

import ij.ImagePlus;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Test;
import org.junit.Assert;

import org.micromanager.data.Annotation;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
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
import org.micromanager.PropertyMap;

/**
 * This class tests that we properly transfer data between versions
 * (1.4 -> 2.0). It relies on an acquisition that was run immediately after
 * startup using the 1.4.23 MDA interface with the following parameters:
 * - 64x64 DemoCamera
 * - 2 timepoints 5000ms apart
 * - 4 stage positions arranged in a 2x2 grid, 64 pix. apart and centered on
 *   the origin (so at -32, -32; 32, -32 -- just use Create Grid and make
 *   a centered 2x2 grid)
 * - Z start of 0, end of 5, step of 5
 * - Channel 1 is Cy5, exposure time 25ms
 * - Channel 2 is DAPI, exposure time 50ms
 * - Acquisition comments of "acqcom"
 * Due to the size of this acquisition (80MB) it can't be stored in the
 * repository.
 */
public class VersionCompatTest {
   /**
    * Maps image coordinates to hashes of the pixel data expected to be found
    * there.
    * Hashes generated via the imagePixelHash.bsh script in our test/resources
    * directory.
    */
   private static final HashMap<Coords, Integer> imageHashes_ = new HashMap<Coords, Integer>();
   private static final HashMap<Coords, Double> imageElapsedTimes_ = new HashMap<Coords, Double>();
   private static final HashMap<Coords, String> imageReceivedTimes_ = new HashMap<Coords, String>();
   private static final HashMap<Coords, UUID> imageUUIDs_ = new HashMap<Coords, UUID>();
   static {
      // Set up the above hashes. These values all need to be in the same
      // order!
      Coords[] coords = new Coords[] {
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=0,position=0"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=0,position=1"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=0,position=2"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=0,position=3"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=1,position=0"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=1,position=1"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=1,position=2"),
         DefaultCoords.fromNormalizedString("time=0,z=0,channel=1,position=3"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=0,position=0"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=0,position=1"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=0,position=2"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=0,position=3"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=1,position=0"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=1,position=1"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=1,position=2"),
         DefaultCoords.fromNormalizedString("time=0,z=1,channel=1,position=3"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=0,position=0"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=0,position=1"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=0,position=2"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=0,position=3"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=1,position=0"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=1,position=1"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=1,position=2"),
         DefaultCoords.fromNormalizedString("time=1,z=0,channel=1,position=3"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=0,position=0"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=0,position=1"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=0,position=2"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=0,position=3"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=1,position=0"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=1,position=1"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=1,position=2"),
         DefaultCoords.fromNormalizedString("time=1,z=1,channel=1,position=3")
      };

      int[] hashes = new int[] {
         68068096, 846458624, 68068096, 846458624, 652540416, -2145024512,
            652540416, -2145024512, 1346601280, -617443520, 1346601280,
            -617443520, -2008150080, 1984162752, -2008150080, 1984162752,
            68068096, 846458624, 68068096, 846458624, 652540416, -2145024512,
            652540416, -2145024512, 1346601280, -617443520, 1346601280,
            -617443520, -2008150080, 1984162752, -2008150080, 1984162752,
      };

      double[] times = new double[] {
         441, 1258, 2053, 2558, 810, 1779, 2309, 2808, 608, 1535, 2166, 2667,
            1007, 1933, 2439, 2932, 5315, 5757, 6212, 6670, 5544, 5989, 6448,
            6866, 5403, 5843, 6296, 6742, 5651, 6096, 6572, 6968
      };

      String[] dates = new String[] {
         "2016-02-11 09:00:16 -0800", "2016-02-11 09:00:17 -0800",
         "2016-02-11 09:00:18 -0800", "2016-02-11 09:00:18 -0800",
         "2016-02-11 09:00:16 -0800", "2016-02-11 09:00:17 -0800",
         "2016-02-11 09:00:18 -0800", "2016-02-11 09:00:18 -0800",
         "2016-02-11 09:00:16 -0800", "2016-02-11 09:00:17 -0800",
         "2016-02-11 09:00:18 -0800", "2016-02-11 09:00:18 -0800",
         "2016-02-11 09:00:16 -0800", "2016-02-11 09:00:17 -0800",
         "2016-02-11 09:00:18 -0800", "2016-02-11 09:00:18 -0800",
         "2016-02-11 09:00:21 -0800", "2016-02-11 09:00:21 -0800",
         "2016-02-11 09:00:22 -0800", "2016-02-11 09:00:22 -0800",
         "2016-02-11 09:00:21 -0800", "2016-02-11 09:00:21 -0800",
         "2016-02-11 09:00:22 -0800", "2016-02-11 09:00:22 -0800",
         "2016-02-11 09:00:21 -0800", "2016-02-11 09:00:21 -0800",
         "2016-02-11 09:00:22 -0800", "2016-02-11 09:00:22 -0800",
         "2016-02-11 09:00:21 -0800", "2016-02-11 09:00:22 -0800",
         "2016-02-11 09:00:22 -0800", "2016-02-11 09:00:22 -0800",
      };

      UUID[] uuids = new UUID[] {
         UUID.fromString("dc65000e-d108-48b6-a75f-85e260b246bf"),
         UUID.fromString("a72e6929-b6cb-4da6-8e89-4b35dc566227"),
         UUID.fromString("4638a388-c416-4e31-9a3c-eaee2ceda561"),
         UUID.fromString("497ed6b7-6651-4d32-a24e-83c2af37614b"),
         UUID.fromString("184cc92d-7d13-4acd-bf06-165d6da2c33a"),
         UUID.fromString("56dc552c-544e-4ca8-aba2-ac8cbd9bea45"),
         UUID.fromString("56c3357e-09b3-464d-bbd6-6512ab2274e4"),
         UUID.fromString("0e077ba7-ad1e-44a2-98fc-86094a2cf53d"),
         UUID.fromString("7b83c26b-4002-41a9-b3ec-fbfc149a00f5"),
         UUID.fromString("d06e5570-5468-4de0-9cdd-639146556e00"),
         UUID.fromString("280104a8-3206-46b3-b5eb-4d61743d3cd1"),
         UUID.fromString("c6d38783-bdd9-4626-968d-5194e3316334"),
         UUID.fromString("a1fcf347-ed26-4bed-a11c-ba0cdb102ca4"),
         UUID.fromString("6d407e07-3325-4a50-ac17-d016ceb9381e"),
         UUID.fromString("8eba4e78-cd5e-4eab-9ff9-26c500fed26b"),
         UUID.fromString("144f89b4-7676-46ef-a725-2df0911a64ad"),
         UUID.fromString("4b144c73-e03a-4aa2-acde-9db9f618ac2c"),
         UUID.fromString("fd87c0f8-6c5f-4d74-a09d-99b513a4ec55"),
         UUID.fromString("a693cf0d-5aa8-479c-a8f4-8a86a7e8dfb0"),
         UUID.fromString("221e3a09-1327-4daf-a7f8-c39e220a6045"),
         UUID.fromString("551c34e0-6f78-4cc7-a66d-8892297caeba"),
         UUID.fromString("01c15121-919d-4143-a367-9a90cbc5e0e5"),
         UUID.fromString("5b536d48-30c0-4a32-9b8b-6b05838c736c"),
         UUID.fromString("003829c0-65c0-4c98-b09b-4ba9c5e641d7"),
         UUID.fromString("be1b1212-12ff-4a9a-8a83-0ef68af98d84"),
         UUID.fromString("359f4caf-3c43-4378-99f2-2b0cac575210"),
         UUID.fromString("883a2d61-701d-40f1-bf07-ec7cfc6d8891"),
         UUID.fromString("3293bba3-a85e-4bb0-b27c-d7a892af44ee"),
         UUID.fromString("1fe603c4-1b4a-48db-95e1-71bd4cf5980e"),
         UUID.fromString("c235b2e7-b550-4850-90a9-50dadc1a4c84"),
         UUID.fromString("150b4af0-a4f0-4223-ad66-f0af21a5cd29"),
         UUID.fromString("fb027cfe-34c0-4efb-bd0a-e9734d305516"),
      };

      for (int i = 0; i < coords.length; ++i) {
         imageHashes_.put(coords[i], hashes[i]);
         imageElapsedTimes_.put(coords[i], times[i]);
         imageReceivedTimes_.put(coords[i], dates[i]);
         imageUUIDs_.put(coords[i], uuids[i]);
      }
   }

   /**
    * Load the 1.4 dataset and verify that it matches expectations. Then save
    * it to disk using the 2.0 format.
    */
   @Test
   public void test14Load() throws IOException {
      DefaultDataManager manager = new DefaultDataManager();
      Datastore store = manager.loadData(
            "/Users/chriswei/proj/vale/data/testData/1.4compatTest", true);
      testStore(store);

      File tempDir = Files.createTempDir();
      store.save(Datastore.SaveMode.MULTIPAGE_TIFF, tempDir.getPath());
      store.setSavePath(tempDir.toString());
      store = manager.loadData(tempDir.getAbsolutePath(), true);
      testStore(store);
   }

   private void testStore(Datastore store) {
      SummaryMetadata summary = store.getSummaryMetadata();
      Assert.assertArrayEquals("Channel names", summary.getChannelNames(),
            new String[] {"Cy5", "DAPI"});
      Assert.assertEquals("Z step", summary.getZStepUm(),
            5.0, .00001);
      for (Coords coords : store.getUnorderedImageCoords()) {
         Image image = store.getImage(coords);
         Assert.assertEquals("Pixel hash for " + coords,
               (int) imageHashes_.get(coords),
               HelperImageInfo.hashPixels(image));
         Metadata metadata = image.getMetadata();

         int position = coords.getStagePosition();
         int column = (position == 0 | position == 3) ? 0 : 1;
         int row = position < 2 ? 0 : 1;
         Assert.assertEquals("X position for " + coords,
               column * 64 - 32.0, metadata.getXPositionUm(), .00001);
         Assert.assertEquals("Y position for " + coords,
               row * 64 - 32.0, metadata.getYPositionUm(), .00001);
         Assert.assertEquals("Z position for " + coords,
               metadata.getZPositionUm(),
               coords.getZ() == 0 ? 0.0 : 5.0, .00001);

         // TODO: our test file does not test the sequence number
         // (ImageNumber), initial position list,
         // keepShutterOpen[Channels|Slices], pixelAspect, startTimeMs,
         // ijType (not set in 1.4)
         Assert.assertEquals("Binning for " + coords,
               (int) metadata.getBinning(), 1);
         Assert.assertEquals("Bitdepth for " + coords,
               (int) metadata.getBitDepth(), 16);
         Assert.assertEquals("Camera for " + coords, metadata.getCamera(), "");
         Assert.assertEquals("elapsedTimeMs for " + coords,
               metadata.getElapsedTimeMs(), imageElapsedTimes_.get(coords));
         Assert.assertEquals("Exposure time for " + coords,
               coords.getChannel() == 0 ? 25.0 : 50.0,
               metadata.getExposureMs(), .00001);
         Assert.assertEquals("pixelSizeUm for " + coords,
               metadata.getPixelSizeUm(), 1.0, .00001);
         Assert.assertEquals("pixelType for " + coords,
               metadata.getPixelType(), "GRAY16");
         Assert.assertEquals("positionName for " + coords,
               metadata.getPositionName(),
               String.format("1-Pos_%03d_%03d", column, row));
         Assert.assertEquals("receivedTime for " + coords,
               metadata.getReceivedTime(), imageReceivedTimes_.get(coords));
         Assert.assertEquals("ROI for " + coords, metadata.getROI(),
               new Rectangle(0, 0, 64, 64));
         Assert.assertEquals("source for " + coords, metadata.getSource(),
               "Camera");
         Assert.assertEquals("uuid for " + coords, metadata.getUUID(),
               imageUUIDs_.get(coords));
     }
   }
}

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
   static {
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=0,channel=0,position=0"), 68068096);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=0,channel=0,position=1"), 846458624);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=0,channel=0,position=2"), 68068096);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=0,channel=0,position=3"), 846458624);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=0,channel=1,position=0"), 652540416);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=0,channel=1,position=1"), -2145024512);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=0,channel=1,position=2"), 652540416);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=0,channel=1,position=3"), -2145024512);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=1,channel=0,position=0"), 1346601280);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=1,channel=0,position=1"), -617443520);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=1,channel=0,position=2"), 1346601280);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=1,channel=0,position=3"), -617443520);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=1,channel=1,position=0"), -2008150080);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=1,channel=1,position=1"), 1984162752);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=1,channel=1,position=2"), -2008150080);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=0,z=1,channel=1,position=3"), 1984162752);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=0,channel=0,position=0"), 68068096);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=0,channel=0,position=1"), 846458624);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=0,channel=0,position=2"), 68068096);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=0,channel=0,position=3"), 846458624);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=0,channel=1,position=0"), 652540416);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=0,channel=1,position=1"), -2145024512);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=0,channel=1,position=2"), 652540416);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=0,channel=1,position=3"), -2145024512);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=1,channel=0,position=0"), 1346601280);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=1,channel=0,position=1"), -617443520);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=1,channel=0,position=2"), 1346601280);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=1,channel=0,position=3"), -617443520);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=1,channel=1,position=0"), -2008150080);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=1,channel=1,position=1"), 1984162752);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=1,channel=1,position=2"), -2008150080);
      imageHashes_.put(DefaultCoords.fromNormalizedString("time=1,z=1,channel=1,position=3"), 1984162752);
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
      SummaryMetadata summary = store.getSummaryMetadata();
      Assert.assertArrayEquals("Channel names", summary.getChannelNames(),
            new String[] {"Cy5", "DAPI"});
      for (Coords coords : store.getUnorderedImageCoords()) {
         Image image = store.getImage(coords);
         Assert.assertEquals("Pixel hash for " + coords,
               (int) imageHashes_.get(coords),
               HelperImageInfo.hashPixels(image));
         Metadata metadata = image.getMetadata();
         Assert.assertEquals("Exposure time for " + coords,
               coords.getChannel() == 0 ? 25.0 : 50.0,
               metadata.getExposureMs(), .00001);
         Assert.assertEquals("Z position for " + coords,
               metadata.getZPositionUm(),
               coords.getZ() == 0 ? 0.0 : 5.0, .00001);

         int position = coords.getStagePosition();
         Assert.assertEquals("X position for " + coords,
               (position == 0 || position == 3) ? -32.0 : 32.0,
               metadata.getXPositionUm(), .00001);
         Assert.assertEquals("Y position for " + coords,
               position < 2 ? -32.0 : 32.0,
               metadata.getYPositionUm(), .00001);
      }
   }
}

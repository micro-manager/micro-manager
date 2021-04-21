///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Data testing
// -----------------------------------------------------------------------------
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

import java.io.IOException;
import org.junit.Assert;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;

/** This class holds testable information about a single image. */
public class HelperImageInfo {
  private final Coords coords_;
  private final Metadata metadata_;
  private final int pixelHash_;

  // Maps x/y coordinates to the pixel intensity at that location.
  public HelperImageInfo(Coords coords, Metadata metadata, int pixelHash) {
    coords_ = coords;
    metadata_ = metadata;
    pixelHash_ = pixelHash;
  }

  public Metadata getMetadata() {
    return metadata_;
  }

  /**
   * This method should match the imagePixelHash.bsh script.
   *
   * @param image for which to calculate the hash
   * @return hash for given image
   */
  public static int hashPixels(Image image) {
    Object pixelsArr = image.getRawPixels();
    if (pixelsArr instanceof short[]) {
      int result = 0;
      short[] pixels = (short[]) pixelsArr;
      for (int i = 0; i < pixels.length; ++i) {
        result = result * 23 + pixels[i];
      }
      return result;
    } else if (pixelsArr instanceof byte[]) {
      int result = 0;
      byte[] pixels = (byte[]) pixelsArr;
      for (int i = 0; i < pixels.length; ++i) {
        result = result * 23 + pixels[i];
      }
      return result;
    } else {
      Assert.fail("Unrecognized pixel type");
      return 0;
    }
  }

  public void test(Datastore store) {
    try {
      Image image = store.getImage(coords_);
      Assert.assertNotNull("Image at " + coords_ + " is null", image);
      Assert.assertEquals(pixelHash_, hashPixels(image));
      Metadata metadata = image.getMetadata();
      Assert.assertEquals(metadata_.getBinning(), metadata.getBinning());
      Assert.assertEquals(metadata_.getBitDepth(), metadata.getBitDepth());
      Assert.assertEquals(metadata_.getCamera(), metadata.getCamera());
      Assert.assertEquals(metadata_.getElapsedTimeMs(0.0), metadata.getElapsedTimeMs(0.0));
      Assert.assertEquals(metadata_.getExposureMs(), metadata.getExposureMs());
      // Assert.assertEquals(metadata_.getIjType(), metadata.getIjType());
      Assert.assertEquals(metadata_.getImageNumber(), metadata.getImageNumber());
      //  Assert.assertEquals(metadata_.getKeepShutterOpenChannels(),
      // metadata.getKeepShutterOpenChannels());
      // Assert.assertEquals(metadata_.getKeepShutterOpenSlices(),
      // metadata.getKeepShutterOpenSlices());
      Assert.assertEquals(metadata_.getPixelAspect(), metadata.getPixelAspect());
      Assert.assertEquals(metadata_.getPixelSizeUm(), metadata.getPixelSizeUm());
      Assert.assertEquals(metadata_.getPositionName(""), metadata.getPositionName(""));
      Assert.assertEquals(metadata_.getReceivedTime(), metadata.getReceivedTime());
      Assert.assertEquals(metadata_.getROI(), metadata.getROI());
      Assert.assertEquals(metadata_.getScopeData(), metadata.getScopeData());
      Assert.assertEquals(metadata_.getUserData(), metadata.getUserData());
      Assert.assertEquals(metadata_.getUUID(), metadata.getUUID());
      Assert.assertEquals(metadata_.getXPositionUm(), metadata.getXPositionUm());
      Assert.assertEquals(metadata_.getYPositionUm(), metadata.getYPositionUm());
      Assert.assertEquals(metadata_.getZPositionUm(), metadata.getZPositionUm());
    } catch (IOException io) {
      Assert.fail("Failed to read image from Datastore");
    }
  }
}

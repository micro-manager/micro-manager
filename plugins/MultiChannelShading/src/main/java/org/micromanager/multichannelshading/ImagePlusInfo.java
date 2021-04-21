///////////////////////////////////////////////////////////////////////////////
// FILE:          ImagePlusInfo.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MultiChannelShading plugin
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2014
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

package org.micromanager.multichannelshading;

import clearcl.ClearCLBuffer;
import clearcl.ClearCLContext;
import coremem.enums.NativeTypeEnum;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultImage;

/**
 * Utility class that bundles an ImagePlus with the binning setting and the roi (of the full frame,
 * binned image) of the ImagePlus.
 *
 * @author nico
 */
public class ImagePlusInfo extends ImagePlus {
  private final int binning_;
  private final Rectangle roi_;
  private final Map<ClearCLContext, ClearCLBuffer> clBuffers_;

  public ImagePlusInfo(ImagePlus ip, int binning, Rectangle roi) {
    super(ip.getTitle(), ip.getProcessor());
    binning_ = binning;
    roi_ = roi;
    clBuffers_ = new HashMap<ClearCLContext, ClearCLBuffer>(1);
  }

  public ImagePlusInfo(ImagePlus ip) {
    this(ip, 1, new Rectangle(0, 0, ip.getWidth(), ip.getHeight()));
  }

  public ImagePlusInfo(ImageProcessor ip) {
    super("", ip);
    binning_ = 1;
    roi_ = new Rectangle(0, 0, ip.getWidth(), ip.getHeight());
    clBuffers_ = new HashMap<ClearCLContext, ClearCLBuffer>(1);
  }

  public int getBinning() {
    return binning_;
  }

  public Rectangle getOriginalRoi() {
    return roi_;
  }

  /**
   * Provides access to pixeldata of this image on the GPU GPU data are cached, i.e. if no copy on
   * the GPU is available, data will be copied there and cached for later use. TODO: investigate
   * garbage collection/removal
   *
   * @param cclContext - openCL Context where we want the pixel data
   * @return - Pixel Data in the given openCL context
   */
  public ClearCLBuffer getCLBuffer(ClearCLContext cclContext) {
    if (!clBuffers_.containsKey(cclContext)) {
      ClearCLBuffer clBuffer = null;
      if (super.getProcessor() instanceof ByteProcessor) {
        clBuffer =
            cclContext.createBuffer(
                NativeTypeEnum.UnsignedByte, super.getWidth() * super.getHeight());
        clBuffer.readFrom(ByteBuffer.wrap((byte[]) super.getProcessor().getPixels()), true);
      } else if (super.getProcessor() instanceof ShortProcessor) {
        clBuffer =
            cclContext.createBuffer(
                NativeTypeEnum.UnsignedShort, super.getWidth() * super.getHeight());
        clBuffer.readFrom(ShortBuffer.wrap((short[]) super.getProcessor().getPixels()), true);
      } else if (super.getProcessor() instanceof FloatProcessor) {
        clBuffer =
            cclContext.createBuffer(NativeTypeEnum.Float, super.getWidth() * super.getHeight());
        clBuffer.readFrom(FloatBuffer.wrap((float[]) super.getProcessor().getPixels()), true);
      }

      // TODO: other pixel types...
      clBuffers_.put(cclContext, clBuffer);
    }
    return clBuffers_.get(cclContext);
  }
}

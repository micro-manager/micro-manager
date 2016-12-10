// Copyright (C) 2017 Open Imaging, Inc.
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

package org.micromanager.display.internal.imagestats;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.micromanager.data.Image;

/**
 * An image together with parameters for statistics computation.
 * @author Mark A. Tsuchida
 */
public final class ImageStatsRequest implements BoundedPriorityElement {
   private final List<Image> images_ = new ArrayList<Image>();

   // TODO Create this from a builder

   public static ImageStatsRequest create(List<Image> images,
         Rectangle roiRect, byte[] roiMask)
   {
      return new ImageStatsRequest(images, roiRect, roiMask);
   }

   private ImageStatsRequest(List<Image> images, Rectangle roiRect,
         byte[] roiMask) {
      // TODO Check paraemters carefully
      images_.addAll(images);
      // TODO
   }

   @Override
   public int getPriority() {
      // We prioritize sets with more images.
      return getNumberOfImages();
   }

   public int getNumberOfImages() {
      return images_.size();
   }

   public Image getImage(int index) {
      return images_.get(index);
   }

   public List<Image> getImages() {
      return Collections.unmodifiableList(images_);
   }

   public int getMaxBinCountPowerOf2() {
      return 8; // TODO
   }

   public Rectangle getROIRect() {
      return null; // TODO
   }

   public byte[] getROIMask() {
      return null; // TODO
   }
}

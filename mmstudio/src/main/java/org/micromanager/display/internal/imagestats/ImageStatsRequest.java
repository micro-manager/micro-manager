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

import com.google.common.base.Preconditions;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;

/**
 * An image together with parameters for statistics computation.
 * @author Mark A. Tsuchida
 */
public final class ImageStatsRequest {
   private final Coords nominalCoords_;
   private final List<Image> images_ = new ArrayList<>();
   private final BoundsRectAndMask roi_;

   public static ImageStatsRequest create(Coords nominalCoords,
         List<Image> images,
         BoundsRectAndMask roi)
   {
      return new ImageStatsRequest(nominalCoords, images, roi);
   }

   private ImageStatsRequest(Coords nominalCoords,
         List<Image> images,
         BoundsRectAndMask roi)
   {
      Preconditions.checkNotNull(nominalCoords);
      Preconditions.checkNotNull(images);
      nominalCoords_ = nominalCoords;
      images_.addAll(images);
      roi_ = roi;
   }

   public Coords getNominalCoords() {
      return nominalCoords_;
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
      return 16; // TODO Should be configurable
   }

   public Rectangle getROIBounds() {
      return roi_.getBounds();
   }

   public byte[] getROIMask() {
      return roi_.getMask();
   }
}

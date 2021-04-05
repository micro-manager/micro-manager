// Copyright (C) 2016-2017 Open Imaging, Inc.
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

package org.micromanager.display.internal.animate;

import com.google.common.collect.Lists;
import org.micromanager.data.Coords;
import org.micromanager.data.Coords.CoordsBuilder;
import org.micromanager.data.internal.DefaultCoords;

import java.util.Collection;
import java.util.List;

/**
 * Manages the position within a dataset displayed during animated playback.
 *
 * Keeps track of the current position of the animation, and computes the
 * position that should be displayed next.
 *
 * @author Mark A. Tsuchida
 */
public class DataCoordsAnimationState implements AnimationStateDelegate<Coords> {
   /**
    * Interface for the delegate supplying presence/absence of data.
    * All methods must be thread safe.
    */
   public interface CoordsProvider {
      List<String> getOrderedAxes();
      int getMaximumExtentOfAxis(String axis); // O(1) recommended
      boolean coordsExist(Coords c);
      Collection<String> getAnimatedAxes();
   }

   private final CoordsProvider delegate_;
   private Coords animationCoords_ = new DefaultCoords.Builder().build();

   // Fractional part of last advancement, tracked to avoid frame rate error.
   private double cumulativeFrameCountError_ = 0.0;

   public static DataCoordsAnimationState create(CoordsProvider delegate) {
      if (delegate == null) {
         throw new NullPointerException();
      }
      return new DataCoordsAnimationState(delegate);
   }

   private DataCoordsAnimationState(CoordsProvider delegate) {
      delegate_ = delegate;
   }

   @Override
   public synchronized Coords getAnimationPosition() {
      return animationCoords_;
   }

   @Override
   public synchronized void setAnimationPosition(Coords position) {
      animationCoords_ = position.copyBuilder().build();
      cumulativeFrameCountError_ = 0.0;
   }

   @Override
   public synchronized Coords advanceAnimationPosition(double frames) {
      return advanceAnimationPositionImpl(frames, true);
   }

   private Coords advanceAnimationPositionImpl(double frames,
         boolean skipNonExistent)
   {
      final Coords prevPos = animationCoords_;
      final List<String> axes = delegate_.getOrderedAxes();
      // TODO: this is coming from the summarymetadata.  If the axes list was not
      // not set, bad thing will happen downstream.  At the very least log
      // empty axes list
      final Collection<String> animatedAxes = delegate_.getAnimatedAxes();

      if (animatedAxes.isEmpty()) {
         return animationCoords_;
      }

      // We want to advance by the given fractional frames, but if we just
      // round to integer, error will accumulate and result in erroneous
      // framerate. So keep track of the rounding error.
      frames -= cumulativeFrameCountError_;
      int framesToAdvance = Math.max(0, (int) Math.round(frames));
      cumulativeFrameCountError_ = framesToAdvance - frames;
      if (framesToAdvance == 0) {
         return null;
      }

      CoordsBuilder cb = new DefaultCoords.Builder();
      for (String axis : Lists.reverse(axes)) {
         int prevIndex = prevPos.getIndex(axis);
         if (!animatedAxes.contains(axis) || framesToAdvance == 0) {
            cb.index(axis, prevIndex);
            continue;
         }
         int axisLength = delegate_.getMaximumExtentOfAxis(axis) + 1;
         int unwrappedNewIndex = prevIndex + framesToAdvance;
         cb.index(axis, unwrappedNewIndex % axisLength);
         framesToAdvance = unwrappedNewIndex / axisLength;
      }
      animationCoords_ = cb.build();

      // Skip forward to first extant coords. But guard against the possibility
      // that we will never find
      if (skipNonExistent && !axes.isEmpty()) {
         Coords start = animationCoords_;
         while (!delegate_.coordsExist(animationCoords_)) {
            advanceAnimationPositionImpl(1.0, false);
            if (animationCoords_.equals(start)) {
               // All coords are nonexistent; revert to original position
               animationCoords_ = prevPos;
               break;
            }
         }
      }

      return animationCoords_;
   }

}
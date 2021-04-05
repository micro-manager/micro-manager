// Copyright (C) 2016 Open Imaging, Inc.
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

/**
 * An object that computes the next data position to render in an animated
 * display.
 *
 * This object encapsulates the concept of a position within a dataset and acts
 * as a cursor for animating the display of data.
 *
 * Because datasets can add data points or structure over time, there are the
 * concepts of partial and full data positions. A partial position may specify
 * only part of the information to select an actual data point
 *
 * @param <P> the type representing a position in the dataset
 * @author Mark A. Tsuchida
 */
public interface AnimationStateDelegate<P> {
   /**
    * Get the current data position.
    *
    * The returned position is a full position with respect to the current
    * shape of the data.
    *
    * @return the current data position
    */
   P getAnimationPosition();

   /**
    * Set the current data position.
    *
    * <p>
    * The current data position held by this object does not change other than
    * by this method and {@code advanceAnimationPosition}.
    *
    * @param position the new current data position
    */
   void setAnimationPosition(P position);

   /**
    * Advance the current (last rendered) data position by the given number of
    * frames.
    * <p>
    * The number of frames can be theoretical and thus need not be an integer,
    * but should correspond to number of data points.
    * <p>
    * If the position is not to change (because {@code frames} is less than
    * 0.5), then null should be returned.
    * <p>
    * The current data position held by this object does not change other than
    * by this method and {@code setAnimationPosition}.
    *
    * @param frames frames to advance
    * @return the new data position, or null if no change
    */
   P advanceAnimationPosition(double frames);

}
///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.data;

import java.util.List;

/**
 * The multi-dimensional coordinate of a 2D image within a dataset.
 *
 * This is typically used to represent the time point, stage position, z slice,
 * and channel indices of images.
 *
 * <p> The {@code Coords} object is a mapping from axes (represented by strings) to
 * positive integer indices. Because an arbitrary number of axes may be included
 * in the Coords, the index zero is meaningless and will be omitted.  Therefore,
 * the axis index for any index not included in the Coords will be zero.  The
 * Coords of an image that is zero for all axes is an empty Coords. </p>
 *
 * <p>The equals operator can be relied upon to establish whether two
 * different Coords contain the same axes and the same indices to those axes.</p>
 *
 * <p>Although methods are included to support any arbitrary axes, such usage is
 * not yet fully supported. <strong>If you do use custom axes, give them names
 * that include upper case letters</strong> so that they do not clash with
 * standard axes added in the future.</p>
 * <p>
 * {@code Coords} objects are immutable.
 *
 * @author Chris Weisiger, Mark A. Tsuchida
 */
public interface Coords {
   /** Axis label for the time point (frame) axis. */
   String TIME_POINT = "time";

   /**
    * Same as {@code TIME_POINT} or {@code T}.
    * @deprecated Use discouraged because it reads like a physical time rather
    * than the time point index that it is.
    */

   @Deprecated String TIME = TIME_POINT;

   /** Axis label for the time point (frame) axis (short form).
    * Same as {@code TIME_POINT}. */
   String T = TIME_POINT;

   /** Axis label for the stage position axis. */
   String STAGE_POSITION = "position";

   /** Axis label for the stage position axis (short form).
    * Same as {@code STAGE_POSTITION}. */
   String P = STAGE_POSITION;

   /** Axis label for the Z slice axis. */
   String Z_SLICE = "z";

   /** Axis label for the Z slice axis (short form).
    * Same as {@code Z_SLICE}. 
    */
   String Z = Z_SLICE;

   /** Axis label for the channel axis. */
   String CHANNEL = "channel";

   /** Axis label for the channel axis (short form). Same as {@code CHANNEL}. */
   String C = CHANNEL;

   interface Builder extends CoordsBuilder {
      @Override Coords build();

      /**
       * Set the channel index.
       * Equivalent to {@code index(Coords.CHANNEL, channel)}.
       *
       * @param channel channel index (0-based)
       * @return this
       */
      @Override Builder channel(int channel);

      /** 
       * Shorthand for {@link #channel(int) channel}.
       * @param channel channel index (0-based)
       * @return  this
       */
      Builder c(int channel);

      /**
       * Set the time point (frame) index.
       * Equivalent to {@code index(Coords.TIME_POINT, frame)}.
       *
       * @param frame time point (frame) index (0-based)
       * @return this
       */
      Builder timePoint(int frame);

      /**
       * Same as {@link #timePoint(int) timePoint}.
       * @param timepoint (0-based)
       * @return this
       * @deprecated Due to being confusing with physical time.
       */
      @Override 
      @Deprecated 
      Builder time(int timepoint);

      /** 
       * Shorthand for {@link #time(int) time}.
       * @param timepoint (0-based)
       * @return this
       */
      Builder t(int timepoint);

      /**
       * Set the Z slice index.
       * Equivalent to {@code index(Coords.Z_SLICE, slice)}.
       *
       * @param slice z slice index (0-based)
       * @return this
       */
      Builder zSlice(int slice);

      /** 
       * Shorthand for {@link #zSlice(int)}.
       * Set the Z slice index.
       * Equivalent to {@code index(Coords.Z_SLICE, slice)}.
       *
       * @param slice z slice index (0-based)
       * @return this
       */ 
      @Override Builder z(int slice);

      /**
       * Set the stage position index.
       * Equivalent to {@code index(Coords.STAGE_POSITION, index)}.
       *
       * @param index stage position index (0-based)
       * @return this
       */
      @Override Builder stagePosition(int index);

      /** 
       * Shorthand for {@link #stagePosition(int) stagePosition}.
       * Set the stage position index.
       * Equivalent to {@code index(Coords.STAGE_POSITION, index)}.
       *
       * @param index stage position index (0-based)
       * @return this
       */
      Builder p(int index);

      /**
       * Set the index along a given axis.
       *
       * If you set a negative value, the axis will be removed.
       *
       * @param axis coordinate axis, such as {@code Coords.CHANNEL}
       * @param index 0-based index
       * @return this
       */
      @Override Builder index(String axis, int index);

      /**
       * Remove the specified axis.
       *
       * @param axis coordinate axis, such as {@code Coords.CHANNEL}
       * @return this
       */
      @Override Builder removeAxis(String axis);

      /**
       * Offset the given axis by a given count.
       *
       * @param axis coordinate axis, such as {@code Coords.CHANNEL}
       * @param offset offset to be applied to {@code axis}
       * @return this
       * @throws IllegalArgumentException if {@code axis} does not exist
       * @throws IndexOutOfBoundsException if applying the offset would result
       * in a negative index.
       */
      @Override Builder offset(String axis, int offset)
            throws IllegalArgumentException, IndexOutOfBoundsException;
   }

   interface CoordsBuilder {
      Coords build();
      CoordsBuilder channel(int channel);
      CoordsBuilder time(int time);
      CoordsBuilder z(int z);
      CoordsBuilder stagePosition(int stagePosition);
      CoordsBuilder index(String axis, int index);
      CoordsBuilder removeAxis(String axis);
      CoordsBuilder offset(String axis, int offset) throws IllegalArgumentException;
   }

   /**
    * Get the index for the given axis.
    * 
    * @param axis coordinate axis such as {@code Coords.CHANNEL}
    * @return index along {@code axis}, or {@code 0} if {@code axis} does not
    * exist
    */
   int getIndex(String axis);

   /**
    * Get the channel index.
    *
    * Equivalent to {@code getIndex(Coords.CHANNEL)}.
    * 
    * @return channel index, or {@code 0} if this {@code Coords} doesn't
    * contain a channel index.
    */
   int getChannel();

   /** 
    * Shorthand for {@link #getChannel() getChannel}.
    * @return channel index, or {@code 0} if this {@code Coords} doesn't
    * contain a channel index.
    */
   int getC();

   /**
    * Get the time point (frame) index.
    *
    * Equivalent to {@code getIndex(Coords.TIME_POINT)}.
    *
    * @return time point index, or {@code 0} if this {@code Coords} doesn't
    * contain a time point index.
    */
   int getTimePoint();

   /** 
    * Same as {@link #getTimePoint() getTimePoint}.
    * @return time index (0-based)
    * @deprecated Due to looking like the physical time rather than an index. Use {@link #getTimePoint() getTmePoint}
    */
   @Deprecated
   int getTime();

   /** 
    * Shorthand for {@link #getTimePoint() getTimePoint}.
    *
    * @return time point index, or {@code 0} if this {@code Coords} doesn't
    * contain a time point index.
    */
   int getT();

   /**
    * Get the Z slice index.
    * 
    * Equivalent to {@code getIndex(Coords.Z_SLICE)}.
    * 
    * @return Z slice index, or {@code 0} if this {@code Coords} doesn't
    * contain a Z slice index.
    */
   int getZSlice();

   /** Shorthand for {@link #getZSlice() getZSlice}
    * 
    * @return Z slice index, or {@code 0} if this {@code Coords} doesn't
    * contain a Z slice index.
    */
   int getZ();

   /**
    * Get the stage position index.
    *
    * Equivalent to {@code getIndex(Coords.STAGE_POSITION)}.
    * 
    * @return stage position index, or {@code 0} if this {@code Coords}
    * doesn't contain a stage position index.
    */
   int getStagePosition();

   /** Shorthand for {@link #getStagePosition() getStagePosition}.
    * 
    * @return stage position index, or {@code 0} if this {@code Coords}
    * doesn't contain a stage position index.
    */
   int getP();

   /**
    * Return all axes that this {@code Coords} has an index for.
    *
    * @return List of all axis
    */
   List<String> getAxes();

   /**
    * Returns whether this coords has the given axis.
    * @param axis the axis to test for presence
    * @return true if this coords includes {@code axis}
    */
   boolean hasAxis(String axis);

   boolean hasTimePointAxis();
   boolean hasT();
   boolean hasStagePositionAxis();
   boolean hasP();
   boolean hasZSliceAxis();
   boolean hasZ();
   boolean hasChannelAxis();
   boolean hasC();

   /**
    * @param alt the instance to compare with
    * @return whether this instance is a superspace coords of {@code other}
    * @deprecated Use equality (after removing specific axes) instead
    */
   @Deprecated
   boolean matches(Coords alt);

   /**
    * Provides a Builder pre-loaded with a copy of this Coords
    * @return copyBuilder
    */
   Builder copyBuilder();

   /**
    * @return Builder
    * @deprecated Use {@link #copyBuilder() copyBuilder} instead
    */
   @Deprecated
   CoordsBuilder copy();


   /**
    * Removes the axes provided as varargs from this Coord
    * @param axes One or more Strings naming the axes to be removed
    * @return Copy of this Coords without the listed axes
    */
   Coords copyRemovingAxes(String... axes);

   /**
    * Name of this function is very unclear.  It seems that its functionality is
    * to provide a copy of the given Coords, but only for the axes provided
    * in the input strings.
    * A more useful name may be: copyProvidedAxes, or copyAxes
    * @param axes Names of axes to be represented in the output
    * @return Copy of this Coords, but only with the subset of axes provided in
    *          the axes param
    */
   Coords copyRetainingAxes(String... axes);
}
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
 * The Coords class tracks the position of an image in the dataset. This
 * position is represented as a mapping of Strings to non-negative ints. Coords
 * are immutable; construct a Coords using a CoordsBuilder. You are not expected
 * to implement your own Coords class.
 * Coords can be compared to each other using &lt;, &gt;, ==, etc. Axes are
 * compared in alphabetical order, and the indices of the respective Coords
 * will determine which one is "less" or "greater".
 * 
 * If you need to generate a new Coords, use the getCoordsBuilder() method of
 * the DataManager class, or call the copy() method of an existing Coords
 * instance.
 *
 * This class uses a Builder pattern. Please see
 * https://micro-manager.org/wiki/Using_Builders
 * for more information.
 */
public interface Coords {

   public static final String CHANNEL = "channel";
   public static final String TIME = "time";
   public static final String STAGE_POSITION = "position";
   public static final String Z = "z";

   interface CoordsBuilder {

      /**
       * Construct a Coords from the CoordsBuilder. Call this once you have set
       * all properties for the Coords.
       * 
       * @return newly build Coords object
       */
      Coords build();

      /**
       * Convenience function, equivalent to index(Coords.CHANNEL, channel)
       * 
       * @param channel channel number (0-based)
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls
       */
      CoordsBuilder channel(int channel);

      /**
       * Convenience function, equivalent to index(Coords.TIME, time)
       * 
       * @param time time (frame) number (0-based)
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls
       */
      CoordsBuilder time(int time);

      /**
       * Convenience function, equivalent to index(Coords.Z, z)
       * 
       * @param z z slice number (0-based)
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls      */
      CoordsBuilder z(int z);

      /**
       * Convenience function, equivalent to index(Coords.STAGE_POSITION,
       * 
       * @param stagePosition position number (0-based)
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls
       */
      CoordsBuilder stagePosition(int stagePosition);

      /**
       * Set the index of the Coords along the provided axis to the specified
       * value. If you set a negative value, then the axis will be removed from
       * the Coords.
       * 
       * @param axis Coords axis, such as Coords.CHANNEL
       * @param index 0-based position index
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls
       */
      CoordsBuilder index(String axis, int index);

      /**
       * Removes the specified axis from the Coords. Equivalent to calling
       * index(axis, -1).
       * 
       * @param axis Coords axis such as Coords.CHANNEL
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls
       */
      CoordsBuilder removeAxis(String axis);

      /**
       * Apply an offset to a pre-existing index.
       *
       * @param axis Coords axis such as Coords.CHANNEL
       * @param offset offset to be applied to the current index
       * @throws IllegalArgumentException If there is no pre-existing value for
       * this axis, or if adding the offset would result in a negative index.
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls
       */
      CoordsBuilder offset(String axis, int offset) throws IllegalArgumentException;
   }

   /**
    * Given an axis label (for example "t" or "z" or "polarization"), returns
    * the index of this Coords along that axis. Returns -1 if this Coords has
    * no defined index along the axis; otherwise, values are assumed to be
    * non-negative integers.
    * 
    * @param axis Coords axis such as Coords.CHANNEL
    * @return index at that axis in this Coords
    */
   public int getIndex(String axis);

   /**
    * Convenience function, equivalent to getIndex(Coords.CHANNEL)
    * 
    * @return position of of this Coords at the Channel axis
    */
   public int getChannel();

   /**
    * Convenience function, equivalent to getIndex(Coords.TIME)
    * 
    * @return position of this Coords at the axis Coords.TIME
    */
   public int getTime();

   /**
    * Convenience function, equivalent to getIndex(Coords.Z)
    * 
    * @return position of this Coords at the axis Coords.TZ
    */
   public int getZ();

   /**
    * Convenience function, equivalent to getIndex(Coords.STAGE_POSITION)
    * 
    * @return position of this Coords at the axis Coords.POSITION
    */
   public int getStagePosition();

   /**
    * Returns a list of all axes that this Coords has an index for.
    * 
    * @return List with String representing all axis in this Coords
    */
   public List<String> getAxes();

   /**
    * Return true if, for every index in the provided Coords, we have a
    * matching and equal index in ourself. Returns false if either any
    * index in the provided Coords differs from our own index, or we have
    * no index for an axis that is specified in the provided Coords.
    * 
    * @param alt Coords to be compared with the current one
    * @return true if it contains the same axis and all axis are at the same 
    * position
    */
   public boolean matches(Coords alt);

   /**
    * Generate a new CoordsBuilder based on the values for this Coords.
    * 
    * @return new CoordsBuilder based on the values for this Coords
    */
   public CoordsBuilder copy();
}

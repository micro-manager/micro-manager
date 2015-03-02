package org.micromanager.data;

import java.util.List;

/**
 * The Coords class tracks the position of an image in the dataset. This
 * position is represented as a mapping of Strings to non-negative ints. Coords
 * are immutable; construct a Coords using a CoordsBuilder. You are not expected
 * to implement your own Coords class. If you need to generate a new Coords, use
 * the getCoordsBuilder() method of the DataManager class, or call the copy()
 * method of an existing Coords instance.
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
       * Convenience function, equivalent to position(Coords.CHANNEL, channel)
       * 
       * @param channel channel number (0-based)
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls
       */
      CoordsBuilder channel(int channel);

      /**
       * Convenience function, equivalent to position(Coords.TIME, time)
       * 
       * @param time time (frame) number (0-based)
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls
       */
      CoordsBuilder time(int time);

      /**
       * Convenience function, equivalent to position(Coords.Z, z)
       * 
       * @param z z slice number (0-based)
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls      */
      CoordsBuilder z(int z);

      /**
       * Convenience function, equivalent to position(Coords.STAGE_POSITION,
       * 
       * @param stagePosition position number (0-based)
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls
       */
      CoordsBuilder stagePosition(int stagePosition);

      /**
       * Set the position of the Coords along the provided axis to the specified
       * value. If you set a negative value, then the axis will be removed from
       * the Coords.
       * 
       * @param axis Coords axis, such as Coords.CHANNEL
       * @param position 0-based position index
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls
       */
      CoordsBuilder position(String axis, int position);

      /**
       * Removes the specified axis from the Coords. Equivalent to calling
       * position(axis, -1).
       * 
       * @param axis Coords axis such as Coords.CHANNEL
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls
       */
      CoordsBuilder removeAxis(String axis);

      /**
       * Apply an offset to a pre-existing position.
       *
       * @param axis Coords axis such as Coords.CHANNEL
       * @param offset offset to be applied to the current position
       * @throws IllegalArgumentException If there is no pre-existing value for
       * this axis, or if adding the offset would result in a negative position.
       * @return this instance of the CoordsBuilder, so that you can chain 
       * function calls
       */
      CoordsBuilder offset(String axis, int offset) throws IllegalArgumentException;

      /**
       * Return the position for the Builder at the specified axis, as per
       * Coords.getPositionAt(), below.
       * 
       * @param axis Coords axis such as Coords.CHANNEL
       * @return position for the Builder at the specified axis
       */
      int getPositionAt(String axis);
   }

   /**
    * Given an axis label (for example "t" or "z" or "polarization"), returns
    * the position of this Coords along that axis. Returns -1 if this Coords has
    * no defined position along the axis; otherwise, values are assumed to be
    * non-negative integers.
    * 
    * @param axis Coords axis such as Coords.CHANNEL
    * @return position at that axis in this Coords
    */
   public int getPositionAt(String axis);

   /**
    * Convenience function, equivalent to getPositionAt(Coords.CHANNEL)
    * 
    * @return position of of this Coords at the Channel axis
    */
   public int getChannel();

   /**
    * Convenience function, equivalent to getPositionAt(Coords.TIME)
    * 
    * @return position of this Coords at the axis Coords.TIME
    */
   public int getTime();

   /**
    * Convenience function, equivalent to getPositionAt(Coords.Z)
    * 
    * @return position of this Coords at the axis Coords.TZ
    */
   public int getZ();

   /**
    * Convenience function, equivalent to getPositionAt(Coords.STAGE_POSITION)
    * 
    * @return position of this Coords at the axis Coords.POSITION
    */
   public int getStagePosition();

   /**
    * Returns a list of all axes that this Coords has a position for.
    * 
    * @return List with String representing all axis in this Coords
    */
   public List<String> getAxes();

   /**
    * Return true if, for every position in the provided Coords, we have a
    * matching and equal position in ourself. Returns false if either any
    * position in the provided Coords differs from our own position, or we have
    * no position for an axis that is specified in the provided Coords.
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

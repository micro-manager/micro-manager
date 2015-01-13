package org.micromanager.data;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * The Coords class tracks the position of an image in the dataset. They are
 * immutable; construct a Coords using a CoordsBuilder.
 * You are not expected to implement your own Coords class. If you need to
 * generate a new Coords, use the getCoordsBuilder() method of the
 * DataManager class, or call the copy() method of an existing Coords instance.
 */
public interface Coords {
   
   public static final String CHANNEL = "channel";
   public static final String TIME = "time";
   public static final String STAGE_POSITION = "stagePosition";
   public static final String Z = "z";
   interface CoordsBuilder {
      /**
       * Construct a Coords from the CoordsBuilder. Call this once you have
       * set all properties for the Coords.
       */
      Coords build();

      /** Convenience function, equivalent to
        * position(Coords.CHANNEL, channel) */
      CoordsBuilder channel(int channel);
      /** Convenience function, equivalent to position(Coords.TIME, time) */
      CoordsBuilder time(int time);
      /** Convenience function, equivalent to position(Coords.Z, z) */
      CoordsBuilder z(int z);
      /** Convenience function, equivalent to
        * position(Coords.STAGE_POSITION, stagePosition) */
      CoordsBuilder stagePosition(int stagePosition);

      /**
       * Set the position of the Coords along the provided axis to the 
       * specified value.
       */
      CoordsBuilder position(String axis, int position);

      /**
       * Apply an offset to a pre-existing position.
       * @throws IllegalArgumentException If there is no pre-existing value for
       *         this axis, or if adding the offset would result in a negative
       *         position.
       */
      CoordsBuilder offset(String axis, int offset) throws IllegalArgumentException;

      /**
       * Indicate that this image is at the end of an axis of the dataset.
       * For example, if you were collecting a Z-stack with 10 slices per
       * stack, then the 10th image would have this set for the "Z" axis.
       */
      CoordsBuilder isAxisEndFor(String axis);

      /**
       * Return the position for the Builder at the specified axis, as per
       * Coords.getPositionAt(), below.
       */
      int getPositionAt(String axis);
   }
   
   /**
    * Given an axis label (for example "t" or "z" or "polarization"), returns
    * the position of this Coords along that axis. Returns -1 if this Coords
    * has no defined position along the axis; otherwise, values are assumed
    * to be non-negative integers. 
    */
   public int getPositionAt(String axis);

   /** Convenience function, equivalent to getPositionAt(Coords.CHANNEL) */
   public int getChannel();

   /** Convenience function, equivalent to getPositionAt(Coords.TIME) */
   public int getTime();

   /** Convenience function, equivalent to getPositionAt(Coords.Z) */
   public int getZ();

   /** Convenience function, equivalent to
     * getPositionAt(Coords.STAGE_POSITION) */
   public int getStagePosition();

   /**
    * Returns true if this Coords marks the end of an axis of iteration in the
    * experiment, false otherwise. For example, if the experiment is collecting
    * Z-stacks with 10 images per stack, then any image with a value of Z=9
    * would return true when called with getIsAxisEndFor("z").
    * Note that a Coords being at the *beginning* of an axis is simply 
    * indicated by the position being 0 for that axis.
    */
   public boolean getIsAxisEndFor(String axis);

   /**
    * Returns a (possibly empty) set of all axes that this Coords is at the
    * end of.
    */
   public Set<String> getTerminalAxes();

   /**
    * Returns a list of all axes that this Coords has a position for.
    */
   public List<String> getAxes();

   /**
    * Return true if, for every position in the provided Coords, we have a
    * matching and equal position in ourself. Returns false if either any
    * position in the provided Coords differs from our own position, or we
    * have no position for an axis that is specified in the provided Coords.
    */
   public boolean matches(Coords alt);

   /**
    * Generate a new CoordsBuilder based on the values for this Coords.
    */
   public CoordsBuilder copy();
}

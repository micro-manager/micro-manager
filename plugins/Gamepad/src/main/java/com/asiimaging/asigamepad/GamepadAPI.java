package com.asiimaging.asigamepad;

/**
 * A basic API used to write Beanshell scripts, the plugin needs to be open for
 * these methods to work.
 */
public class GamepadAPI {

   /**
    * The assignable values in the Axis Assignment Table.
    */
   public enum Axis {

      LEFT_THUMBSTICK_X(0),
      LEFT_THUMBSTICK_Y(1),
      RIGHT_THUMBSTICK_X(2),
      RIGHT_THUMBSTICK_Y(3),
      LEFT_TRIGGER(4),
      RIGHT_TRIGGER(5),
      DPAD(6);

      private final int value;

      Axis(final int value) {
         this.value = value;
      }

      public int getValue() {
         return value;
      }
   }

   private static AxisTable axisTable;

   /**
    * Sets the AxisTable that subsequent methods will modify.
    *
    * @param table the AxisTable to modify
    */
   public static void setAxisTable(final AxisTable table) {
      axisTable = table;
   }

   /**
    * Returns the value of the multiplier for the specified axis.
    *
    * @param axis the axis to query
    * @return the value of the multiplier for the specified axis
    */
   public static float getAxisMultiplier(final Axis axis) {
      return Float.parseFloat(axisTable.table_.getValueAt(axis.getValue(), 3).toString());
   }

   /**
    * Sets the value of the multiplier for the specified axis.
    *
    * @param axis  the axis to set
    * @param value the multiplier value
    */
   public static void setAxisMultiplier(final Axis axis, final float value) {
      axisTable.table_.setValueAt(String.valueOf(value), axis.getValue(), 3);
   }

   /**
    * Sets the value of the multiplier for the specified axis.
    *
    * @param axis  the axis to set
    * @param value the multiplier value
    */
   public static void setAxisMultiplier(final Axis axis, final double value) {
      axisTable.table_.setValueAt(String.valueOf((float) value), axis.getValue(), 3);
   }

}

package org.micromanager.patternoverlay;

import java.awt.Color;

/**
 *  Color options for dropdown selection.
 *
 *  @author Matthijs
 */
public class ColorOption {

   private final Color   color_;      // Color object represented by this option.
   private final String  name_;       // Description of this option as it appears in drop-down.


   /**
    *  ColorOption constructor; expects a Color and its associated name.
    *  
    *  @param color
    *  @param name
    */
   public ColorOption(Color color, String name) {
      color_ = color;
      name_  = name;
   }


   /**
    *  Retrieve the Color object associated with this option.
    *
    *  @return This color.
    */
   public Color getColor () {
      return color_;
   }


   /**
    *  Retrieve the name of this color. This overrides the toString method, since
    *  that is what is being called by the drop-down box to populate the fields.
    *
    *  @return     Name of the color this option represents.
    */
   @Override
   public String toString () {
      return name_;
   }
}


package org.micromanager.patternoverlay;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains only static constants used in this package. 
 * @author Jon
 */
class Constants {

   // pref strings
   static final String TYPE_BOX_IDX = "typeBoxIdx";
   static final String COLOR_BOX_IDX = "colorBoxIdx";
   static final String SIZE_SLIDER = "sizeSlider";
   static final String WIN_LOC_X = "winLocX";
   static final String WIN_LOC_Y = "winLocY";
   
   static final ColorOption [] COLOR_OPTION_ARRAY = {
      new ColorOption(Color.RED,     "Red"),
      new ColorOption(Color.MAGENTA, "Magenta"),
      new ColorOption(Color.YELLOW,  "Yellow"),
      new ColorOption(Color.GREEN,   "Green"),
      new ColorOption(Color.BLUE,    "Blue"),        
      new ColorOption(Color.CYAN,    "Cyan"),
      new ColorOption(Color.BLACK,   "Black"),
      new ColorOption(Color.WHITE,   "White"),
   };
   static final Map<Integer, Color> LOOKUP_COLOR_BY_INDEX;
   static {
      LOOKUP_COLOR_BY_INDEX = new HashMap<Integer, Color>();
      for (int iii=0; iii < COLOR_OPTION_ARRAY.length; ++iii) {
         LOOKUP_COLOR_BY_INDEX.put(iii, COLOR_OPTION_ARRAY[iii].getColor());
      }
   }
   
}

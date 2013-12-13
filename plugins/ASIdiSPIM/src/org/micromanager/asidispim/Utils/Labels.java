///////////////////////////////////////////////////////////////////////////////
//FILE:          DevicesPanel.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim.Utils;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Static labels that translate between enums as used in the code and
 * strings that are displayed to the user
 * Mappings can be looked up either way by statically defined (final) Maps
 * 
 * @author nico
 * @author Jon
 */
public class Labels {
   public static enum Sides {A, B};
   
   public static final Map<Sides, String> SIDESMAP = 
           new EnumMap<Sides, String>(Sides.class);
   static {
      SIDESMAP.put(Sides.A, "A");
      SIDESMAP.put(Sides.B, "B");
   }
   
   public static enum Directions {
      X("X"), Y("Y");
      private final String text;
      Directions(String text) {
         this.text = text;
      }
      public String toString(Object... o) {
         return String.format(text, o);
      }
   };
   
   public static final Map<Directions, String> DIRECTIONS = 
           new EnumMap<Directions, String>(Directions.class);
   static {
      DIRECTIONS.put(Directions.X, "X");
      DIRECTIONS.put(Directions.Y, "Y");
   }
   
   public static final Map<String, Directions> REVDIRECTIONS = 
           new HashMap<String, Directions>();
   static {
      REVDIRECTIONS.put("X", Directions.X);
      REVDIRECTIONS.put("Y", Directions.Y);
   }
   
   public static enum Terms {MICROMIRROR, XYSTAGE, IMAGINGPIEZO, 
      ILLUMINATIONPIEZO, SHEETPOSITION, SHEETOFFSET};
   public static final Map<Terms, String> TERMSMAP = 
           new EnumMap<Terms, String>(Terms.class);
   static {
      TERMSMAP.put(Terms.MICROMIRROR,"MicroMirror");
      TERMSMAP.put(Terms.XYSTAGE, "XY Stage");
      TERMSMAP.put(Terms.IMAGINGPIEZO, "ImagingPiezo");
      TERMSMAP.put(Terms.ILLUMINATIONPIEZO, "IlluminationPiezo");
      TERMSMAP.put(Terms.SHEETPOSITION, "Sheet Position");
      TERMSMAP.put(Terms.SHEETOFFSET, "Sheet Offset");
   }
   public static final Map<String, Terms> REVTERMSMAP = 
           new HashMap<String, Terms>();
   static {
      Iterator<Terms> it = TERMSMAP.keySet().iterator();
      while (it.hasNext()) {
         Terms term = it.next();
         REVTERMSMAP.put(TERMSMAP.get(term), term);
      }
   }
   
   /**
    * types of properties that Micro-Manager supports 
    * @author Jon
    */
   public static enum PropTypes { STRING, FLOAT, INTEGER };
   
   /**
    * associative class to store information about MicroManager properties
    * @author Jon
    */
   public static class Property {
	   public String pluginName;
	   public String adapterName;
	   public String pluginDevice;
	   public PropTypes propType;
	   
	   /**
	    * 
	    * @param pluginName property name in Java; used as key to hashmap
	    * @param adapterName property name in ASITiger.h
	    * @param pluginDevice device name in Java
	    * @param propType STRING, FLOAT, or INTEGER
	    */
	   public Property(String pluginName, String adapterName, String pluginDevice, PropTypes propType) {
		   this.pluginName = pluginName;
		   this.adapterName = adapterName;
		   this.pluginDevice = pluginDevice;
		   this.propType = propType;
	   }
   }
}

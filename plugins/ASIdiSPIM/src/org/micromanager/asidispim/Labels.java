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

package org.micromanager.asidispim;

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
   
   public static final Map<Directions, String> DIRECTIONSMAP = 
           new EnumMap<Directions, String>(Directions.class);
   static {
      DIRECTIONSMAP.put(Directions.X, "X");
      DIRECTIONSMAP.put(Directions.Y, "Y");
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
}

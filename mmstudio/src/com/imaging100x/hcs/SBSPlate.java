///////////////////////////////////////////////////////////////////////////////
//FILE:           MetadataDlg.java
//PROJECT:        Micro-Manager-S
//SUBSYSTEM:      high content screening
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, June 3, 2008
//
//COPYRIGHT:      100X Imaging Inc, www.100ximaging.com, 2008
//                
//LICENSE:        This file is distributed under the GPL license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//CVS:            $Id: MetadataDlg.java 1275 2008-06-03 21:31:24Z nenad $

package com.imaging100x.hcs;

import java.util.Hashtable;

public class SBSPlate {
   private int numColumns_;
   private int numRows_;
   private double wellPitchX_;
   private double wellPitchY_;
   private double sizeXUm_;
   private double sizeYUm_;
   private String id_;
   private String description_;
   private double firstWellX_;
   private double firstWellY_;
   private Hashtable<String, Well> wellMap_;
   
   public static String SBS_96_WELL= "96WELL";
   public static String SBS_384_WELL= "384WELL";
   //public static String CUSTOM = "CUSTOM";
      
   public SBSPlate() {
      // initialize as 96-well plate
      initialize(SBS_96_WELL);
   }
   
   public void initialize(String id) {
      if (id.equals(SBS_96_WELL)){
         id_ = SBS_96_WELL;
         numColumns_ = 12;
         numRows_ = 8;
         sizeXUm_ = 127760.0;
         sizeYUm_ = 85480.0;
         wellPitchX_ = 9000.0;
         wellPitchY_ = 9000.0;
         firstWellX_ = 14380.0;
         firstWellY_ = 11240.0;
      } else {
         id_ = SBS_384_WELL;
         numColumns_ = 24;
         numRows_ = 16;
         sizeXUm_ = 127760.0;
         sizeYUm_ = 85480.0;
         wellPitchX_ = 4500.0;
         wellPitchY_ = 4500.0;
         firstWellX_ = 12130.0;
         firstWellY_ = 8990.0;        
      }
   }
   
   public void load(String path) {
      
   }
   
   public void save(String path) {
      
   }
   
   public String serialize() {
      JSONObject plate = new JSONObject();
      return null;
   }
   
   public void restore(String ser) {
      
   }
   
   public String getID() {
      return new String(id_);
   }
   
   public String getDescription() {
      return new String(description_);
   }
   
   public double getWellXUm(String wellLabel) {
      return 0.0;
   }
   
   public double getWellYUm(String wellLabel) {
      return 0.0;
   }
   
   public String getColumnLabel(int col) {
      return new String();
   }
   
   public String getRowLabel(int col) {
      return new String();
   }
   
   public String getWellLabel(int row, int col) {
      return new String();
   }

   private class Well {
      public String label;
      public int row;
      public int col;
      public double x;
      public double y;
      
      public Well() {
         row = 0;
         col = 0;
         x = 0.0;
         y = 0.0;
         label = new String("Undefined");
      }
   }

}
   


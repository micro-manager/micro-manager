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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.metadata.ImageKey;
import org.micromanager.metadata.MMAcqDataException;

public class SBSPlate {
   private int numColumns_;
   private int numRows_;
   private double wellSpacingX_;
   private double wellSpacingY_;
   private double sizeXUm;
   private double sizeYUm;
   private String id_;
   private String description_;
   private double firstWellX_;
   private double firstWellY_;
   private Hashtable<String, Well> wellMap_;
   
   private static String ROWS = "rows";
   private static String COLS = "cols";
   private static String WELL_SPACING_X = "well_spacing_X";
   private static String WELL_SPACING_Y = "well_spacing_Y";
   private static String PLATE_SIZE_X = "plate_size_X";
   private static String PLATE_SIZE_Y = "plate_size_Y";
   private static String ID = "id";
   private static String DESCRIPTION = "description";
   private static String FIRST_WELL_X = "first_well_x";
   private static String FIRST_WELL_Y = "first_well_y";
   
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
         sizeXUm = 127760.0;
         sizeYUm = 85480.0;
         wellSpacingX_ = 9000.0;
         wellSpacingY_ = 9000.0;
         firstWellX_ = 14380.0;
         firstWellY_ = 11240.0;
      } else {
         id_ = SBS_384_WELL;
         numColumns_ = 24;
         numRows_ = 16;
         sizeXUm = 127760.0;
         sizeYUm = 85480.0;
         wellSpacingX_ = 4500.0;
         wellSpacingY_ = 4500.0;
         firstWellX_ = 12130.0;
         firstWellY_ = 8990.0;        
      }
   }
   
   public void load(String path) throws HCSException {
      StringBuffer contents = new StringBuffer();
      try {
         // read metadata from file            
         BufferedReader input = null;
         input = new BufferedReader(new FileReader(path));
         String line = null;
         while (( line = input.readLine()) != null){
            contents.append(line);
            contents.append(System.getProperty("line.separator"));
         }

         restore(contents.toString());
      } catch (IOException e) {
         throw new HCSException(e);
      }
      
   }
   
   public void save(String path) throws HCSException {     
      try {
         FileWriter fw = new FileWriter(path);
         fw.write(serialize());
         fw.close();
      } catch (IOException e) {
         throw new HCSException("Unable to create plate definition file: " + e.getMessage());
      }      
   }
   
   public String serialize() throws HCSException {
      JSONObject plate = new JSONObject();
      try {
         plate.put(ROWS, numRows_);
         plate.put(COLS, numColumns_);
         plate.put(WELL_SPACING_X, wellSpacingX_);
         plate.put(WELL_SPACING_Y, wellSpacingY_);
         plate.put(PLATE_SIZE_X, sizeXUm);
         plate.put(PLATE_SIZE_Y, sizeYUm);
         plate.put(ID, id_);
         plate.put(DESCRIPTION, description_);
         plate.put(FIRST_WELL_X, firstWellX_);
         plate.put(FIRST_WELL_Y, firstWellY_);
         return plate.toString(3);
      } catch (JSONException e) {
         throw new HCSException(e);
      }
   }
   
   public void restore(String ser) throws HCSException {
      
      JSONObject plate;
      try {
         plate = new JSONObject(ser);
         numRows_ = plate.getInt(ROWS);
         numColumns_ = plate.getInt(COLS);
         wellSpacingX_ = plate.getInt(WELL_SPACING_X);
         wellSpacingY_ = plate.getInt(WELL_SPACING_Y);
         sizeXUm = plate.getInt(PLATE_SIZE_X);
         sizeYUm = plate.getInt(PLATE_SIZE_Y);
         id_ = plate.getString(ID);
         description_ = plate.getString(DESCRIPTION);
         firstWellX_ = plate.getDouble(FIRST_WELL_X);
         firstWellY_ = plate.getDouble(FIRST_WELL_Y);
      } catch (JSONException e) {
         throw new HCSException(e);
      }
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
   


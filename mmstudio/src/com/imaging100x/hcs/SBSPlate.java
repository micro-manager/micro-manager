///////////////////////////////////////////////////////////////////////////////
//FILE:           SBSPlate.java
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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.navigation.StagePosition;

public class SBSPlate {
   private int numColumns_;
   private int numRows_;
   private double wellSpacingX_;
   private double wellSpacingY_;
   private double sizeXUm_;
   private double sizeYUm_;
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
   
   public static final String SBS_96_WELL= "96WELL";
   public static final String SBS_384_WELL= "384WELL";
   public static final String DEFAULT_XYSTAGE_NAME = "XYStage"; 
   //public static String CUSTOM = "CUSTOM";
   
   
   private static char rowAlphabet[] = { 'A','B','C','D','E',
         'F','G','H','I','J',
         'K','L','M','N','O',
         'P','Q','R','S','T',
         'U','V','W','X','Y','Z' };
      
   public SBSPlate() {
      // initialize as 96-well plate
      wellMap_ = new Hashtable<String, Well>();
      initialize(SBS_96_WELL);
   }
   
   public boolean initialize(String id) {
      if (id.equals(SBS_96_WELL)){
         id_ = SBS_96_WELL;
         numColumns_ = 12;
         numRows_ = 8;
         sizeXUm_ = 127760.0;
         sizeYUm_ = 85480.0;
         wellSpacingX_ = 9000.0;
         wellSpacingY_ = 9000.0;
         firstWellX_ = 14380.0;
         firstWellY_ = 11240.0;
      } else {
         id_ = SBS_384_WELL;
         numColumns_ = 24;
         numRows_ = 16;
         sizeXUm_ = 127760.0;
         sizeYUm_ = 85480.0;
         wellSpacingX_ = 4500.0;
         wellSpacingY_ = 4500.0;
         firstWellX_ = 12130.0;
         firstWellY_ = 8990.0;        
      }
      
      try {
         generateWells();
      } catch (HCSException e) {
         e.printStackTrace();
         return false;
      }
      
      return true;
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
         plate.put(PLATE_SIZE_X, sizeXUm_);
         plate.put(PLATE_SIZE_Y, sizeYUm_);
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
         sizeXUm_ = plate.getInt(PLATE_SIZE_X);
         sizeYUm_ = plate.getInt(PLATE_SIZE_Y);
         id_ = plate.getString(ID);
         description_ = plate.getString(DESCRIPTION);
         firstWellX_ = plate.getDouble(FIRST_WELL_X);
         firstWellY_ = plate.getDouble(FIRST_WELL_Y);
      } catch (JSONException e) {
         throw new HCSException(e);
      }
   }
   
   /**
    * Generate a list of well positions using 'snake' pattern.
    * This method assumes a single site at the center of the well.
    * @return
    */
   public WellPositionList[] generatePositions(String xyStageName) {
      WellPositionList posListArray[] = new WellPositionList[numRows_ * numColumns_];
      boolean direction = true;
      int wellCount = 0;
      for (int i=0; i<numRows_; i++) {
         for (int j=0; j<numColumns_; j++) {
            WellPositionList wpl = new WellPositionList();
            PositionList posList = new PositionList();
            
            MultiStagePosition mps = new MultiStagePosition();
            StagePosition sp = new StagePosition();
            sp.numAxes = 2;
            sp.stageName = xyStageName;
            String wellLabel;
            try {
               int colIndex;
               if (direction)
                  colIndex = j+1; // forward
               else
                  colIndex = numColumns_ - j; // reverse
               wellLabel = getWellLabel(i+1, colIndex);
            } catch (HCSException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
               return null;
            }
            
            try {
               sp.x = getWellXUm(wellLabel);
               sp.y = getWellYUm(wellLabel);
               mps.add(sp);
               // TODO: remove a single-site hack below
               mps.setLabel(AcquisitionData.METADATA_SITE_PREFIX + "_0");
               mps.setDefaultXYStage(xyStageName);
               posList.addPosition(mps);
               wpl.setSitePositions(posList);
               wpl.setLabel(wellLabel);
               wpl.setGridCoordinates(i, j);
               posListArray[wellCount++] = wpl;
            } catch (HCSException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
            }
         }
         direction = !direction; // reverse direction
      }
      return posListArray;
   }
   /**
    * Generate a list of well positions using 'snake' pattern.
    * Takes a list of sites and merges them into the well list.
    * Site XY coordinates are assumed to be relative to the well center.
    * @return - an array of well positions
    */
   public WellPositionList[] generatePositions(String xyStageName, PositionList sites) {
      WellPositionList posListArray[] = new WellPositionList[numRows_ * numColumns_];
      boolean direction = true;
      int wellCount = 0;
      
      for (int i=0; i<numRows_; i++) {
         for (int j=0; j<numColumns_; j++) {
            WellPositionList wpl = new WellPositionList();
            String wellLabel;
            try {
               int colIndex;
               if (direction)
                  colIndex = j+1; // forward
               else
                  colIndex = numColumns_ - j; // reverse
               wellLabel = getWellLabel(i+1, colIndex);
            } catch (HCSException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
               return null;
            }
            
            try {
               double wellX = getWellXUm(wellLabel);
               double wellY = getWellYUm(wellLabel);
               PositionList absSites = new PositionList();
               for (int k=0; k<sites.getNumberOfPositions(); k++) {
                  MultiStagePosition mps = sites.getPosition(k);
                  MultiStagePosition absMps = new MultiStagePosition();
                  absMps.setLabel(AcquisitionData.METADATA_SITE_PREFIX + "_" + k);
                  absMps.setDefaultXYStage(xyStageName);
                  // TODO: make sure we get the right XY stage not just the first one
                  StagePosition sp = mps.get(0);
                  StagePosition absSp = new StagePosition();
                  absSp.x = wellX + sp.x;
                  absSp.y = wellY + sp.y;
                  absSp.stageName = xyStageName;
                  absSp.numAxes = 2;
                  absMps.add(absSp);
                  absSites.addPosition(absMps);
               }
               wpl.setSitePositions(absSites);
               wpl.setLabel(wellLabel);
               posListArray[wellCount++] = wpl;
            } catch (HCSException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
            }
         }
         direction = !direction; // reverse direction
      }
      return posListArray;
   }
   
   public String getID() {
      return new String(id_);
   }
   
   public String getDescription() {
      return new String(description_);
   }
   
   public double getWellXUm(String wellLabel) throws HCSException {
      if (wellMap_.containsKey(wellLabel))
         return wellMap_.get(wellLabel).x;
      
     throw new HCSException("Invalid well label: " + wellLabel);
   }
   
   public double getWellYUm(String wellLabel) throws HCSException {
      if (wellMap_.containsKey(wellLabel))
         return wellMap_.get(wellLabel).y;
      
     throw new HCSException("Invalid well label: " + wellLabel);
   }
   
   public String getColumnLabel(int col) throws HCSException {
      
      if(col < 1 || col > numColumns_)
         throw new HCSException("Invalid column number: " + col);

      return Integer.toString(col);
   }
   
   public String getRowLabel(int row) throws HCSException {
      // limit row index to valid range
      if (row < 1 || row > numRows_)
         throw new HCSException("Invalid row number: " + row);
      
      // build the row label
      int tempRow = row;
      String label = new String();
      while( tempRow > 0 )
      {
         int letterIndex = (tempRow - 1) % rowAlphabet.length;
         label += rowAlphabet[letterIndex];
         tempRow = ( tempRow - 1 ) / rowAlphabet.length;
      }
      return label;
   }
   
   public String getWellLabel(int row, int col) throws HCSException {
      return getRowLabel(row) + getColumnLabel(col);
   }
   
   private void generateWells() throws HCSException {
      wellMap_.clear();
      for (int i=0; i<numRows_; i++)
         for (int j=0; j<numColumns_; j++) {
            Well w = new Well();
            w.x = firstWellX_ + wellSpacingX_ * j;
            w.y = firstWellY_ + wellSpacingY_ * i;
            w.row = i+1;
            w.col = j+1;
            w.label = getWellLabel(w.row, w.col);
            wellMap_.put(w.label, w);
         }
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
   
   public int getNumberOfRows() {
      return numRows_;
   }

   public int getNumberOfColumns() {
      return numColumns_;
   }
   
   public double getXSize() {
      return sizeXUm_;
   }
   
   public double getYSize() {
      return sizeYUm_;
   }
   
   public double getTopLeftX() {
      return firstWellX_ - wellSpacingX_ / 2.0;
   }
   
   public double getTopLeftY() {
      return firstWellY_ - wellSpacingY_ / 2.0;
   }

}
   


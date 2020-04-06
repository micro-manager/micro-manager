///////////////////////////////////////////////////////////////////////////////
//FILE:           SBSPlate.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      high content screening
//-----------------------------------------------------------------------------

//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, 2008, 2013

//COPYRIGHT:      UCSF, 100X Imaging Inc

//LICENSE:        This file is distributed under the LGPL license.
//                License text is included with the source distribution.

//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.hcs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ReportingUtils;

public class SBSPlate {

   private int numColumns_;
   private int numRows_;
   private double wellSpacingX_;
   private double wellSpacingY_;
   private double sizeXUm_;
   private double sizeYUm_;
   private double firstWellX_;
   private double firstWellY_;
   private double wellSizeX_;
   private double wellSizeY_;
   private boolean circular_ = true;

   private String id_;
   private String description_;
   private final HashMap<String, Well> wellMap_;

   private static final String ROWS = "rows";
   private static final String COLS = "cols";
   private static final String WELL_SPACING_X = "well_spacing_X";
   private static final String WELL_SPACING_Y = "well_spacing_Y";
   private static final String PLATE_SIZE_X = "plate_size_X";
   private static final String PLATE_SIZE_Y = "plate_size_Y";
   private static final String ID = "id";
   private static final String DESCRIPTION = "description";
   private static final String FIRST_WELL_X = "first_well_x";
   private static final String FIRST_WELL_Y = "first_well_y";
   private static final String CIRCULAR = "circular";

   public static final String SBS_6_WELL= "6-Well";
   public static final String SBS_12_WELL= "12-Well";
   public static final String SBS_24_WELL= "24-Well";
   public static final String SBS_48_WELL= "48-Well";
   public static final String SBS_96_WELL= "96-Well";
   public static final String SBS_384_WELL= "384-Well";
   public static final String SLIDE_HOLDER ="4 Slides";
   public static final String DEFAULT_XYSTAGE_NAME = "XYStage"; 
   public static final String LOAD_CUSTOM = "Load custom...";
   public static final String CUSTOM = "Custom";
   private static final String METADATA_SITE_PREFIX = "Site";

   public static final FileDialogs.FileType PLATE_FILE = new FileDialogs.FileType(
         "PLATE_FILE", "High-Content Screening plate definition files",
         System.getProperty("user.home") + "/MyPlate.txt", true, "txt");

   private static final char[] ROW_ALPHABET = { 'A','B','C','D','E',
      'F','G','H','I','J',
      'K','L','M','N','O',
      'P','Q','R','S','T',
      'U','V','W','X','Y','Z' };

   private String prevMode_ = "";
   private boolean wasLastModeCustom_ = false;

   public SBSPlate() {
      // initialize as 96-well plate
      wellMap_ = new HashMap<String, Well>();
      initialize(SBS_96_WELL);
   }
   
   @Override
   public String toString() {
      return id_;
   }

   public final void initialize(String id) {
      // Skip it if we've already loaded it, or if we're being asked to load
      // a custom plate when we've already loaded a custom plate. This last is
      // a hack to get around the fact that we often call
      // initialize(LOAD_CUSTOM) while reseting the GUI.
      if (id.contentEquals(prevMode_) ||
            (id.contentEquals(LOAD_CUSTOM) && wasLastModeCustom_)) {
         // Already in the desired mode; just (re)generate wells.
         // TODO: do we even need to do this much, or is it safe to just
         // return immediately?
         try {
            generateWells();
         } catch (HCSException e) {
            ReportingUtils.logError(e);
         }
         return;
      }
      prevMode_ = id;
      wasLastModeCustom_ = false;
      /* // SDS definition, does not seem to be adhered to
         // replaced with definition below
        if (id.equals(SBS_24_WELL)){
         id_ = SBS_24_WELL;
         numColumns_ = 6;
         numRows_ = 4;
         sizeXUm_ = 127760.0;
         sizeYUm_ = 85480.0;
         wellSpacingX_ = 18000.0;
         wellSpacingY_ = 18000.0;
         firstWellX_ = 18880.0;
         firstWellY_ = 15734.5;
         wellSizeX_ = 14000.0;
         wellSizeY_ = 14000.0;
         circular_ = true;
*/
      if (id.equals(SBS_6_WELL)) {
         // Corning plates
         id_ = SBS_6_WELL;
         numColumns_ = 3;
         numRows_ = 2;
         sizeXUm_ = 127760.0;
         sizeYUm_ = 85470.0;
         wellSpacingX_ = 39120.0;
         wellSpacingY_ = 39120.0;
         firstWellX_ = 24760.0;
         firstWellY_ = 23160.0;
         wellSizeX_ = 34800.0;
         wellSizeY_ = 34800.0;
         circular_ = true;
      }
      else if (id.equals(SBS_12_WELL)) {
         // Corning plates
         id_ = SBS_12_WELL;
         numColumns_ = 4;
         numRows_ = 3;
         sizeXUm_ = 127890.0;
         sizeYUm_ = 85600.0;
         wellSpacingX_ = 26010.0;
         wellSpacingY_ = 26010.0;
         firstWellX_ = 24940.0;
         firstWellY_ = 16790.0;
         wellSizeX_ = 22110.0;
         wellSizeY_ = 22110.0;
         circular_ = true;
      }
      else if (id.equals(SBS_24_WELL)){
         id_ = SBS_24_WELL;
         numColumns_ = 6;
         numRows_ = 4;
         sizeXUm_ = 127500.0;
         sizeYUm_ = 85250.0;
         wellSpacingX_ = 19300.0;
         wellSpacingY_ = 19300.0;
         firstWellX_ = 17050.0;
         firstWellY_ = 13670.0;        
         wellSizeX_ = 15540.0;
         wellSizeY_ = 15540.0;
         circular_ = true;
      } else if (id.equals(SBS_48_WELL)){
         id_ = SBS_48_WELL;
         numColumns_ = 8;
         numRows_ = 6;
         sizeXUm_ = 127760.0;
         sizeYUm_ = 85480.0;
         wellSpacingX_ = 13000.0;
         wellSpacingY_ = 13000.0;
         firstWellX_ = 18380.0;
         firstWellY_ = 10240.0;        
         wellSizeX_ = 11370.0;
         wellSizeY_ = 11370.0;
         circular_ = true;
      } else if (id.equals(SBS_96_WELL)){
         id_ = SBS_96_WELL;
         numColumns_ = 12;
         numRows_ = 8;
         sizeXUm_ = 127760.0;
         sizeYUm_ = 85480.0;
         wellSpacingX_ = 9000.0;
         wellSpacingY_ = 9000.0;
         firstWellX_ = 14380.0;
         firstWellY_ = 11240.0;
         wellSizeX_ = 8000.0;
         wellSizeY_ = 8000.0;
         circular_ = true;
      } else if (id.equals(SBS_384_WELL)){
         id_ = SBS_384_WELL;
         numColumns_ = 24;
         numRows_ = 16;
         sizeXUm_ = 127760.0;
         sizeYUm_ = 85480.0;
         wellSpacingX_ = 4500.0;
         wellSpacingY_ = 4500.0;
         firstWellX_ = 12130.0;
         firstWellY_ = 8990.0;        
         wellSizeX_ = 4000.0;
         wellSizeY_ = 4000.0;
         circular_ = false;
      } else if (id.equals(SLIDE_HOLDER)) {
         id_ = SLIDE_HOLDER;
         numColumns_ = 4;
         numRows_ = 1;
         sizeXUm_ = 127760.0;
         sizeYUm_ = 85480.0;
         wellSpacingX_ = 27600.0;
         wellSpacingY_ = 80000.0;
         firstWellX_ = 20000.0;
         firstWellY_ = 50000.0;        
         wellSizeX_ = 25600.0;
         wellSizeY_ = 75000.0;
         circular_ = false;
      } else if (id.equals(LOAD_CUSTOM)) {
         wasLastModeCustom_ = true;
         File file = FileDialogs.openFile(null,
               "Please select the file to load from", PLATE_FILE);
         if (file != null) {
            try {
               load(file.getAbsolutePath());
            }
            catch (HCSException e) {
               ReportingUtils.showError(e, "There was an error loading the file");
            }
         }
      }
      else if ((new File(id)).exists()) {
         // Assume it's a custom file and load it.
         wasLastModeCustom_ = true;
         try {
            load(id);
         }
         catch (HCSException e) {
            ReportingUtils.showError(e, "There was an error loading the file");
         }
      }
      else {
         ReportingUtils.logError("Unrecognized plate mode " + id);
      }

      try {
         generateWells();
      } catch (HCSException e) {
         ReportingUtils.logError(e);
      }

   }

   public void load(String path) throws HCSException {
      StringBuilder contents = new StringBuilder();
      try {
         // read metadata from file            
         BufferedReader input = new BufferedReader(new FileReader(path));
         String line;
         while (( line = input.readLine()) != null){
            contents.append(line);
            contents.append(System.getProperty("line.separator"));
         }
         input.close();
         restore(contents.toString());
      } catch (IOException e) {
         throw new HCSException(e);
      }
      wasLastModeCustom_ = true;
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
      return serialize(numRows_, numColumns_, wellSpacingX_, wellSpacingY_,
            sizeXUm_, sizeYUm_, id_, description_, firstWellX_, firstWellY_,
            circular_);
   }

   public static String serialize(int numRows, int numColumns,
         double wellSpacingX, double wellSpacingY, double sizeXUm,
         double sizeYUm, String id, String description, double firstWellX,
         double firstWellY, boolean circular) throws HCSException {
      JSONObject plate = new JSONObject();
      try {
         plate.put(ROWS, numRows);
         plate.put(COLS, numColumns);
         plate.put(WELL_SPACING_X, wellSpacingX);
         plate.put(WELL_SPACING_Y, wellSpacingY);
         plate.put(PLATE_SIZE_X, sizeXUm);
         plate.put(PLATE_SIZE_Y, sizeYUm);
         plate.put(ID, id);
         plate.put(DESCRIPTION, description);
         plate.put(FIRST_WELL_X, firstWellX);
         plate.put(FIRST_WELL_Y, firstWellY);
         plate.put(CIRCULAR, circular);
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
         circular_ = plate.getBoolean(CIRCULAR);
      } catch (JSONException e) {
         throw new HCSException(e);
      }
   }

   /**
    * Generate a list of well positions using 'snake' pattern.
    * Takes a list of sites and merges them into the well list.
    * Site XY coordinates are assumed to be relative to the well center.
    * @param xyStageName
    * @param sites
    * @return - an array of well positions
    */
   public WellPositionList[] generatePositions(final String xyStageName, 
           final PositionList sites) {
      WellPositionList posListArray[] = new WellPositionList[numRows_ * numColumns_];
      boolean direction = true;
      int wellCount = 0;

      for (int i=0; i<numRows_; i++) {
         for (int j=0; j<numColumns_; j++) {
            WellPositionList wpl = new WellPositionList();
            String wellLabel;
            int colIndex;
            if (direction)
               colIndex = j+1; // forward
            else
               colIndex = numColumns_ - j; // reverse
            wellLabel = getWellLabel(i+1, colIndex);

            try {
               double wellX = getWellXUm(wellLabel);
               double wellY = getWellYUm(wellLabel);
               PositionList absSites = new PositionList();
               for (int k=0; k<sites.getNumberOfPositions(); k++) {
                  MultiStagePosition mps = sites.getPosition(k);
                  MultiStagePosition absMps = new MultiStagePosition();
                  absMps.setLabel(METADATA_SITE_PREFIX + "_" + k);
                  wpl.setGridCoordinates(i, colIndex-1);
                  absMps.setDefaultXYStage(xyStageName);
                  // TODO: make sure we get the right XY stage not just the first one
                  StagePosition sp = mps.get(0);
                  StagePosition absSp = StagePosition.create2D(xyStageName, 
                          wellX + sp.x, wellY + sp.y);
                  absMps.add(absSp);
                  absSites.addPosition(absMps);
               }
               wpl.setSitePositions(absSites);
               wpl.setLabel(wellLabel);
               posListArray[wellCount++] = wpl;
            } catch (HCSException e) {
               // TODO Auto-generated catch block
               ReportingUtils.logError(e);
            }
         }
         direction = !direction; // reverse direction
      }
      return posListArray;
   }

   public WellPositionList[] generatePositions(String xyStageName) {
      
      // generate default site in the center of the well
      PositionList sites = new PositionList();
      MultiStagePosition mps = new MultiStagePosition();
      StagePosition sp = StagePosition.create2D(xyStageName, 0.0, 0.0);
      mps.add(sp);
      sites.addPosition(mps);
      
      return generatePositions(xyStageName, sites);
   }
   
   public String getID() {
      return id_;
   }

   public String getDescription() {
      return description_;
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

   public String getColumnLabel(int col) {

      if(col < 1 || col > numColumns_)
         //throw new HCSException("Invalid column number: " + col);
         return "";

      return Integer.toString(col);
   }

   public String getRowLabel(int row) {
      // limit row index to valid range
      if (row < 1 || row > numRows_)
         //throw new HCSException("Invalid row number: " + row);
         return "";

      // build the row label
      int tempRow = row;
      String label = new String();
      while( tempRow > 0 )
      {
         int letterIndex = (tempRow - 1) % ROW_ALPHABET.length;
         label += ROW_ALPHABET[letterIndex];
         tempRow = ( tempRow - 1 ) / ROW_ALPHABET.length;
      }
      return label;
   }

   public String getWellLabel(int row, int col){
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
         label = "Undefined";
      }
   }

   public int getNumRows() {
      return numRows_;
   }

   public int getNumColumns() {
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

   public double getBottomRightX() {
      return firstWellX_ + wellSpacingX_ * (numColumns_ - 1) + wellSpacingX_ / 2.0;
   }

   public double getBottomRightY() {
      return firstWellY_ + wellSpacingY_ * (numRows_ - 1) + wellSpacingY_ / 2.0;
   }

   public void setNumColumns_(int numColumns_) {
      id_ = CUSTOM;
      this.numColumns_ = numColumns_;
   }

   public void setNumRows(int numRows) {
      id_ = CUSTOM;
      this.numRows_ = numRows;
   }

   public double getWellSpacingX() {
      return wellSpacingX_;
   }

   public void setWellSpacingX(double wellSpacingX) {
      id_ = CUSTOM;
      this.wellSpacingX_ = wellSpacingX;
   }

   public double getWellSpacingY() {
      return wellSpacingY_;
   }

   public void setWellSpacingY(double wellSpacingY) {
      id_ = CUSTOM;
      this.wellSpacingY_ = wellSpacingY;
   }

   public double getWellSizeX() {
      return wellSizeX_;
   }

   public double getWellSizeY() {
      return wellSizeY_;
   }

   public void setSizeX(double sizeXUm) {
      id_ = CUSTOM;
      this.sizeXUm_ = sizeXUm;
   }

   public void setSizeY(double sizeYUm) {
      id_ = CUSTOM;
      this.sizeYUm_ = sizeYUm;
   }

   public double getFirstWellX() {
      return firstWellX_;
   }

   public void setFirstWellX(double firstWellX) {
      id_ = CUSTOM;
      this.firstWellX_ = firstWellX;
   }

   public double getFirstWellY() {
      return firstWellY_;
   }

   public void setFirstWellY(double firstWellY) {
      id_ = CUSTOM;
      this.firstWellY_ = firstWellY;
   }

   public boolean isWellCircular() {
      return circular_;
   }

   public String getWellLabel(double x, double y) {
      int col = getWellColumn(x);
      int row = getWellRow(y);
      return getWellLabel(row+1, col+1);
   }
   
   int getWellRow(double y) {
      return (int)((y - getTopLeftY())/wellSpacingY_);
   }
   
   int getWellColumn(double x) {
      return (int)((x - getTopLeftX())/wellSpacingX_);
   }
   
   boolean isPointWithin(double x, double y) {
      return x >= 0.0 && x < sizeXUm_ && y >= 0.0 && y < sizeYUm_;
            
   }

}



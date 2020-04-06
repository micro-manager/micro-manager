package org.micromanager.acquiremultipleregions;

import java.io.File;
import java.nio.file.Path;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author kthorn
 */
class Region {

   public PositionList positions;
   public String directory;
   public String filename;

   public Region(PositionList PL, String directory, String filename) {
      this.positions = PositionList.newInstance(PL);
      this.directory = directory;
      this.filename = filename;
   }

   /*
    Return a name for the region, by concatenating Directory and Filename
    */
   public String name() {
      File loc = new File(directory, filename);
      String fullfile = loc.getPath();
      return fullfile;
   }

   public MultiStagePosition center() {
      double centerX;
      double centerY;
      MultiStagePosition centerPos;
      PositionList PL = boundingBox();
      MultiStagePosition minCoords = PL.getPosition(0);
      MultiStagePosition maxCoords = PL.getPosition(1);
      centerX = (minCoords.getX() + maxCoords.getX()) / 2;
      centerY = (minCoords.getY() + maxCoords.getY()) / 2;
      centerPos = new MultiStagePosition(minCoords.getDefaultXYStage(),
              centerX, centerY, minCoords.getDefaultZStage(), minCoords.getZ());
      return centerPos;
   }

   public int getNumXTiles(double xStepSize) {
      PositionList bBox;
      double minX, maxX;
      int numXImages;

      bBox = boundingBox();
      minX = bBox.getPosition(0).getX();
      maxX = bBox.getPosition(1).getX();
      numXImages = (int) Math.ceil(Math.abs(maxX - minX) / xStepSize) + 1; // +1 for fencepost problem
      return numXImages;
   }

   public int getNumYTiles(double yStepSize) {
      PositionList bBox;
      double minY, maxY;
      int numYImages;

      bBox = boundingBox();
      minY = bBox.getPosition(0).getY();
      maxY = bBox.getPosition(1).getY();
      numYImages = (int) Math.ceil(Math.abs(maxY - minY) / yStepSize) + 1; // +1 for fencepost problem
      return numYImages;
   }
       
    public PositionList boundingBox() {
      //returns the bounding box of the region as a MicroManager PositionList
      //the first index is the min coordinates, the second is the max coordinates
      MultiStagePosition minCoords;
      MultiStagePosition maxCoords;
      PositionList bBox = new PositionList();
      MultiStagePosition startCoords = positions.getPosition(0);
      String XYStage = startCoords.getDefaultXYStage();
      String ZStage = startCoords.getDefaultZStage();
      double minX = startCoords.getX();
      double minY = startCoords.getY();
      double Z = startCoords.getZ(); //don't worry about min and max of Z
      double maxX = minX;
      double maxY = minY;
      for (int i = 1; i < positions.getNumberOfPositions(); i++) {
         MultiStagePosition p = positions.getPosition(i);
         minX = Math.min(p.getX(), minX);
         minY = Math.min(p.getY(), minY);
         maxX = Math.max(p.getX(), maxX);
         maxY = Math.max(p.getY(), maxY);
      }
      minCoords = new MultiStagePosition(XYStage, minX, minY, ZStage, Z);
      maxCoords = new MultiStagePosition(XYStage, maxX, maxY, ZStage, Z);
      bBox.addPosition(minCoords);
      bBox.addPosition(maxCoords);
      return bBox;
   }
   
   public void save(Path path) {
       try{
           positions.save(path.resolve(filename + ".pos").toFile());
       }
       catch (Exception ex){
           ReportingUtils.showError(ex);
       }
   }
   
   static public Region loadFromFile(File f, File newDir) {
       PositionList positions = new PositionList();
       try{
           positions.load(f);
       }
       catch (Exception ex){
           ReportingUtils.showError(ex);
       }
       String fname = f.getName();
       fname = fname.substring(0,fname.length()-4);
       return new Region(positions, newDir.toString() , fname);
    }
}

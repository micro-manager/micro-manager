package org.micromanager.acquiremultipleregions;

import java.io.File;
import java.nio.file.Path;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * @author kthorn
 */
class Region {

   public PositionList positions;
   public String directory;
   public String filename;

   public Region(PositionList positionList, String directory, String filename) {
      this.positions = PositionList.newInstance(positionList);
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
      PositionList positionList = boundingBox();
      MultiStagePosition minCoords = positionList.getPosition(0);
      MultiStagePosition maxCoords = positionList.getPosition(1);
      centerX = (minCoords.getX() + maxCoords.getX()) / 2;
      centerY = (minCoords.getY() + maxCoords.getY()) / 2;
      centerPos = new MultiStagePosition(minCoords.getDefaultXYStage(),
            centerX, centerY, minCoords.getDefaultZStage(), minCoords.getZ());
      return centerPos;
   }

   public int getNumXTiles(double xStepSize) {
      PositionList bBox = boundingBox();
      double minX = bBox.getPosition(0).getX();
      double maxX = bBox.getPosition(1).getX();
      return (int) Math.ceil(Math.abs(maxX - minX) / xStepSize) + 1; // +1 for fencepost problem
   }

   public int getNumYTiles(double yStepSize) {
      PositionList bBox = boundingBox();
      double minY = bBox.getPosition(0).getY();
      double maxY = bBox.getPosition(1).getY();
      int numYImages =
            (int) Math.ceil(Math.abs(maxY - minY) / yStepSize) + 1; // +1 for fencepost problem
      return numYImages;
   }

   public PositionList boundingBox() {
      //returns the bounding box of the region as a MicroManager PositionList
      //the first index is the min coordinates, the second is the max coordinates
      MultiStagePosition minCoords;
      MultiStagePosition maxCoords;
      final PositionList bBox = new PositionList();
      MultiStagePosition startCoords = positions.getPosition(0);
      String xyStage = startCoords.getDefaultXYStage();
      String zStage = startCoords.getDefaultZStage();
      double minX = startCoords.getX();
      double minY = startCoords.getY();
      double z = startCoords.getZ(); //don't worry about min and max of Z
      double maxX = minX;
      double maxY = minY;
      for (int i = 1; i < positions.getNumberOfPositions(); i++) {
         MultiStagePosition p = positions.getPosition(i);
         minX = Math.min(p.getX(), minX);
         minY = Math.min(p.getY(), minY);
         maxX = Math.max(p.getX(), maxX);
         maxY = Math.max(p.getY(), maxY);
      }
      minCoords = new MultiStagePosition(xyStage, minX, minY, zStage, z);
      maxCoords = new MultiStagePosition(xyStage, maxX, maxY, zStage, z);
      bBox.addPosition(minCoords);
      bBox.addPosition(maxCoords);
      return bBox;
   }

   public void save(Path path) {
      try {
         positions.save(path.resolve(filename + ".pos").toFile());
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public static Region loadFromFile(File f, File newDir) {
      PositionList positions = new PositionList();
      try {
         positions.load(f);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
      String fname = f.getName();
      fname = fname.substring(0, fname.length() - 4);
      return new Region(positions, newDir.toString(), fname);
   }
}

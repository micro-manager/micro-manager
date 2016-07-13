///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
//
package org.micromanager.plugins.magellan.surfacesandregions;

import java.awt.FileDialog;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import org.micromanager.plugins.magellan.imagedisplay.DisplayPlus;
import java.util.ArrayList;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.micromanager.plugins.magellan.gui.GUI;
import org.micromanager.plugins.magellan.misc.JavaUtils;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.misc.NumberUtils;
import org.micromanager.plugins.magellan.propsandcovariants.CovariantPairingsManager;

/**
 *
 * class to keep track of the surfaces/regions
 */
public class RegionManager {

   private ArrayList<MultiPosRegion> regions_ = new ArrayList<MultiPosRegion>();
   private ArrayList<SurfaceRegionComboBoxModel> comboBoxModels_ = new ArrayList<SurfaceRegionComboBoxModel>();
   private RegionTableModel tableModel_;
   private static RegionManager singletonInstance_;

   public RegionManager() {
      singletonInstance_ = this;
   }

   public static RegionManager getInstance() {
      return singletonInstance_;
   }

   public int getIndex(SurfaceInterpolator surface) {
      return regions_.indexOf(surface);
   }

   public MultiPosRegion getRegion(int index) {
      if (index < 0 || index >= regions_.size()) {
         return null;
      }
      return regions_.get(index);
   }

   public RegionTableModel createGridTableModel() {
      tableModel_ = new RegionTableModel(this);
      return tableModel_;
   }

   public void addToModelList(SurfaceRegionComboBoxModel model) {
      comboBoxModels_.add(model);
   }

   public void removeFromModelList(SurfaceRegionComboBoxModel model) {
      comboBoxModels_.remove(model);
   }

   public void deleteAll() {
      regions_.clear();
      for (SurfaceRegionComboBoxModel combo : comboBoxModels_) {
         combo.setSelectedIndex(-1);
      }
      updateRegionTableAndCombos();
   }

   public void delete(int index) {
      regions_.remove(index);
      for (SurfaceRegionComboBoxModel combo : comboBoxModels_) {
         if (index == 0 && regions_.isEmpty()) {
            combo.setSelectedIndex(-1); //set selectionto null cause no surfaces left
         } else if (combo.getSelectedIndex() == 0) {
            //do noting, so selection stays at top of list
         } else if (index <= combo.getSelectedIndex()) {
            combo.setSelectedIndex(combo.getSelectedIndex() - 1); //decrment selection so combo stays on same object
         }
      }
      updateRegionTableAndCombos();
   }

   public void addNewRegion(MultiPosRegion region) {
      regions_.add(region);
      updateRegionTableAndCombos();
   }

   public int getNumberOfRegions() {
      return regions_.size();
   }

   public String getNewName() {
      String base = "New Region";
      int index = 1;
      String potentialName = base + " " + index;
      while (true) {
         boolean uniqueName = true;
         for (MultiPosRegion region : regions_) {
            if (region.getName().equals(potentialName)) {
               index++;
               potentialName = base + " " + index;
               uniqueName = false;
            }
         }
         if (uniqueName) {
            break;
         }
      }
      return potentialName;
   }

   /**
    * redraw overlay for all displays showing this surface
    *
    * @param region
    */
   public void drawRegionOverlay(MultiPosRegion region) {
      DisplayPlus.redrawRegionOverlay(region);
   }

   public void updateRegionTableAndCombos() {
      for (SurfaceRegionComboBoxModel m : comboBoxModels_) {
         m.update();
      }
      tableModel_.fireTableDataChanged();
      CovariantPairingsManager.getInstance().surfaceorRegionNameChanged();
   }

   public void saveRegions(GUI gui) {
      File selectedFile = null;
      if (JavaUtils.isMac()) {
         FileDialog fd = new FileDialog(gui, "Save all grids", FileDialog.SAVE);
         fd.setVisible(true);
         if (fd.getFile() != null) {
            selectedFile = new File(fd.getDirectory() + File.separator + fd.getFile());
            selectedFile = new File(selectedFile.getAbsolutePath() + ".txt");
         }
         fd.dispose();
      } else {
         JFileChooser fc = new JFileChooser();
         fc.setDialogTitle("Save all grids");
         int returnVal = fc.showSaveDialog(gui);
         if (returnVal == JFileChooser.APPROVE_OPTION) {
            selectedFile = fc.getSelectedFile();
         }
      }

      if (selectedFile == null) {
         return; //canceled
      }
      String name = selectedFile.getName();
      if (!name.endsWith(".txt")) {
         name += ".txt";
      }
      selectedFile = new File(new File(selectedFile.getParent()).getPath() + File.separator + name);
      if (selectedFile.exists()) {
         int reply = JOptionPane.showConfirmDialog(null, "Overwrite exisitng file?", "Confirm overwrite", JOptionPane.YES_NO_OPTION);
         if (reply == JOptionPane.NO_OPTION) {
            return;
         }
         selectedFile.delete();
      }

      try {
         selectedFile.createNewFile();
         FileWriter writer = new FileWriter(selectedFile);
         for (MultiPosRegion region : regions_) {
            //name, stage name, numRows, numCols, centerPositionX, centerPositionY
            writer.write(region.getName() + "\t" + region.getXYDevice() +  "\t" + region.numRows() + "\t" +
                     region.numCols() + "\t" +region.center().x + "\t" +region.center().y + "\n");
         }
         writer.flush();
         writer.close();
      } catch (IOException ex) {
         Log.log("Couldn't write file");
         return;
      }

   }

   public void loadRegions(GUI gui) {
      File selectedFile = null;
      if (JavaUtils.isMac()) {
         FileDialog fd = new FileDialog(gui, "Save grids", FileDialog.LOAD);
         fd.setFilenameFilter(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
               return name.endsWith(".txt") || name.endsWith(".TXT");
            }
         });
         fd.setVisible(true);
         if (fd.getFile() != null) {
            selectedFile = new File(fd.getDirectory() + File.separator + fd.getFile());
            selectedFile = new File(selectedFile.getAbsolutePath());
         }
         fd.dispose();
      } else {
         JFileChooser fc = new JFileChooser();
         fc.setFileFilter(new FileNameExtensionFilter("Text file", "txt", "TXT"));
         fc.setDialogTitle("Save regions");
         int returnVal = fc.showSaveDialog(gui);
         if (returnVal == JFileChooser.APPROVE_OPTION) {
            selectedFile = fc.getSelectedFile();
         }
      }
      if (selectedFile == null) {
         return; //canceled
      }

      String fileContents = "";
      FileReader reader;
      try {
         reader = new FileReader(selectedFile);
      } catch (IOException ex) {
         Log.log("Problem opening file");
         return;
      }
      BufferedReader br = new BufferedReader(reader);
      try {
         StringBuilder sb = new StringBuilder();
         String line = br.readLine();
         while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = br.readLine();
         }
         fileContents = sb.toString();
         br.close();
      } catch (IOException e) {
         Log.log("Problem reading file", true);
      }
      //Read file and reconstruct surfaces
      for (String regionString : fileContents.split("\n")) { //for each region
         String[] fields = regionString.split("\t");
         String name = fields[0];
         String xy = fields[1];
         int rows = Integer.parseInt(fields[2]);
         int cols = Integer.parseInt(fields[3]);
         Point2D.Double center = new Point2D.Double(NumberUtils.parseDouble(fields[4]), NumberUtils.parseDouble(fields[5]));
         
         //if there's already one with this name, replace its points
         //so that other parts of the software with references to it stay working
         MultiPosRegion region = null;
         for (MultiPosRegion r : regions_) {
            if (r.getName().equals(name) && r.getXYDevice().equals(xy)) {
               region = r;
            }
         }
         if (region != null) {
            //add new info
            region.updateParams(rows, cols);
            region.updateCenter(center);
         } else {
            region = new MultiPosRegion(this, xy, rows, cols, center);
            region.rename(name);
            regions_.add(region);
         }
   
      }
      updateRegionTableAndCombos();
   }

}

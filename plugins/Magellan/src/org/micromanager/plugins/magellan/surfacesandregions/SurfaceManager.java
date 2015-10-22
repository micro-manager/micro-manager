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

import org.micromanager.plugins.magellan.gui.GUI;
import org.micromanager.plugins.magellan.imagedisplay.DisplayPlus;
import java.awt.FileDialog;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.JavaUtils;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.propsandcovariants.CovariantPairingsManager;
import org.micromanager.plugins.magellan.propsandcovariants.SurfaceData;

/**
 *
 * @author Henry
 */
public class SurfaceManager {
  
   private ArrayList<SurfaceInterpolator> surfaces_ = new ArrayList<SurfaceInterpolator>();
   private ArrayList<SurfaceRegionComboBoxModel> comboBoxModels_ = new ArrayList<SurfaceRegionComboBoxModel>();
   private SurfaceTableModel tableModel_;
   private static SurfaceManager singletonInstance_;
   
   public SurfaceManager() {
      singletonInstance_ = this;
   }
   
   public static SurfaceManager getInstance() {
      return singletonInstance_;
   }
   
   public SurfaceInterpolator getSurfaceNamed(String name) {
      for (SurfaceInterpolator s : surfaces_) {
         if (s.getName().equals(name)) {
            return s;
         }
      }
      return null;
   }
   
   public int getIndex(SurfaceInterpolator surface) {
      return surfaces_.indexOf(surface);
   }
   
   public SurfaceInterpolator getSurface(int index) {
      if (index < 0 || index>= surfaces_.size() ) {
         return null;
      } 
      return surfaces_.get(index);
   }
   
   public SurfaceTableModel createSurfaceTableModel() {
      tableModel_ = new SurfaceTableModel(this);
      return tableModel_;
   }
   
   public void addToModelList(SurfaceRegionComboBoxModel model) {
      comboBoxModels_.add(model);
   }

   public void removeFromModelList(SurfaceRegionComboBoxModel model) {
      comboBoxModels_.remove(model);
   }

   public void deleteAll() {
      for (SurfaceInterpolator s: surfaces_) {
         s.delete();
      }
      surfaces_.clear();
      for (SurfaceRegionComboBoxModel combo : comboBoxModels_) {
         combo.setSelectedIndex(-1);
      }
      updateSurfaceTableAndCombos();
   }
   
   public void delete(int index) {
      SurfaceInterpolator s = surfaces_.remove(index);
      s.delete();
      for (SurfaceRegionComboBoxModel combo : comboBoxModels_) {
         if (index == 0 && surfaces_.isEmpty()) {
            combo.setSelectedIndex(-1); //set selectionto null cause no surfaces left
         } else if (combo.getSelectedIndex() == 0) {
            //do noting, so selection stays at top of list
         } else if (index <= combo.getSelectedIndex()) {
            combo.setSelectedIndex(combo.getSelectedIndex() - 1); //decrment selection so combo stays on same object
         }
      }
      updateSurfaceTableAndCombos();
   }
   
   public void addNewSurface() {
      surfaces_.add(new SurfaceInterpolatorSimple(Magellan.getCore().getXYStageDevice(), Magellan.getCore().getFocusDevice()));
      updateSurfaceTableAndCombos();
   }
   
   public int getNumberOfSurfaces() {
      return surfaces_.size();
   }
  
   public String getNewName() {
      String base = "New Surface";
      int index = 1;
      String potentialName = base + " " + index;
      while (true) {
         boolean uniqueName = true;
         for (SurfaceInterpolator surface : surfaces_) {
            if (surface.getName().equals(potentialName)) {
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

   public void drawSurfaceOverlay(SurfaceInterpolator surface) {
      DisplayPlus.redrawSurfaceOverlay(surface); //redraw overlay for all displays showing this surface
   }
   
   public void updateSurfaceTableAndCombos() {
      for (SurfaceRegionComboBoxModel m : comboBoxModels_) {
         m.update();
      }
      tableModel_.fireTableDataChanged();
      CovariantPairingsManager.getInstance().surfaceorRegionNameChanged();
   }

   /**
    * Generate surface data for all available surfaces
    * @return 
    */
   public ArrayList<SurfaceData> getSurfaceData() {
      ArrayList<SurfaceData> stats = new ArrayList<SurfaceData>();
      for (SurfaceInterpolator surface : surfaces_) {
         stats.addAll(surface.getData());
      }
      return stats;
   }
   
   void renameSurface(int row, String newName) throws Exception {
      for (int i = 0; i < surfaces_.size(); i++) {
         if (i == row) {
            continue;
         }
         if (surfaces_.get(i).getName().equals(newName)) {
            throw new Exception();
         }
      }
      surfaces_.get(row).rename(newName);
      updateSurfaceTableAndCombos();
      //update covariants that use this surface
      CovariantPairingsManager.getInstance().updatePairingNames();
   }
  
   public void saveSurfaces(GUI gui) {
      File selectedFile = null;
      if (JavaUtils.isMac()) {
         FileDialog fd = new FileDialog(gui, "Save all surfaces", FileDialog.SAVE);
         fd.setVisible(true);
         if (fd.getFile() != null) {
            selectedFile = new File(fd.getDirectory() + File.separator + fd.getFile());
            selectedFile = new File(selectedFile.getAbsolutePath() + ".txt");
         }
         fd.dispose();
      } else {
         JFileChooser fc = new JFileChooser();
         fc.setDialogTitle("Save all surfaces");
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
         int reply = JOptionPane.showConfirmDialog(null, "OVerwrite exisitng file?", "Confirm overwrite", JOptionPane.YES_NO_OPTION);
         if (reply == JOptionPane.NO_OPTION) {
            return;
         }
         selectedFile.delete();
      }

      try {
         selectedFile.createNewFile();
         FileWriter writer = new FileWriter(selectedFile);
         for (SurfaceInterpolator surface : surfaces_) {
            writer.write(surface.getName() + "\t" + surface.getXYDevice() + "\t" + surface.getZDevice() + "\n");
            for (Point3d p : surface.getPoints()) {
               writer.write( p.x + "\t" + p.y + "\t" + p.z + "\n");
            }
            writer.write("\n");
         }
         writer.flush();
         writer.close();
      } catch (IOException ex) {
         Log.log("Couldn't write file");
         return;
      }
   
   }

   public void loadSurfaces(GUI gui) {
        File selectedFile = null;
      if (JavaUtils.isMac()) {
         FileDialog fd = new FileDialog(gui, "Save covariant pairing values", FileDialog.LOAD);
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
         fc.setDialogTitle("Save covariant pairing values");
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
         Log.log("Problem reading file",true);
      }
      //Read file and reconstruct surfaces
      for (String surfaceString : fileContents.split("\n\n")) { //for each surface
         String[] lines = surfaceString.split("\n");
         String name = lines[0].split("\t")[0];
         String xy = lines[0].split("\t")[1];
         String z = lines[0].split("\t")[2];
         //if there's already one with this name, replace its points
         //so that other parts of the software with references to it stay working
         SurfaceInterpolator surface = null;
         for (SurfaceInterpolator s : surfaces_) {
            if (s.getName().equals(name)) {
               surface = s;
            }
         }   
         if (surface != null) {
            //remove all points and add these ones
            surface.deleteAllPoints();
         } else {
            surface = new SurfaceInterpolatorSimple(xy, z);
            surface.rename(name);
            surfaces_.add(surface);
         }
         for (int i = 1; i < lines.length; i++) {
            String[] xyz = lines[i].split("\t");
            surface.addPoint(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]));
         }
      }
      updateSurfaceTableAndCombos();
   }

}

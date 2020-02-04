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
package org.micromanager.magellan.internal.surfacesandregions;

import org.micromanager.magellan.internal.gui.GUI;
import java.awt.FileDialog;
import java.awt.geom.Point2D;
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
import org.micromanager.magellan.internal.coordinates.AffineUndefinedException;
import org.micromanager.magellan.internal.coordinates.MagellanAffineUtils;
//import org.micromanager.magellan.imagedisplaynew.DisplayWindowSurfaceGridTableModel;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.JavaUtils;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.misc.NumberUtils;

/**
 *
 * @author Henry
 */
public class SurfaceGridManager {
  
   private ArrayList<SurfaceInterpolator> surfaces_ = new ArrayList<SurfaceInterpolator>();
   private ArrayList<MultiPosGrid> grids_ = new ArrayList<MultiPosGrid>();
   private ArrayList<SurfaceGridListener> listeners_ = new ArrayList<SurfaceGridListener>();
   private static SurfaceGridManager singletonInstance_;
   
   public SurfaceGridManager() {
      singletonInstance_ = this;
   }
   
   public void registerSurfaceGridListener(SurfaceGridListener l ) {
      listeners_.add(l);
   }
   
   public void unregisterSurfaceGridListener(SurfaceGridListener l ) {
      listeners_.remove(l);
   }
   
   public static SurfaceGridManager getInstance() {
      return singletonInstance_;
   }
   
      
   public MultiPosGrid getGridNamed(String name) {
      for (MultiPosGrid s : grids_) {
         if (s.getName().equals(name)) {
            return s;
         }
      }
      return null;
   }
   
   public SurfaceInterpolator getSurfaceNamed(String name) {
      for (SurfaceInterpolator s : surfaces_) {
         if (s.getName().equals(name)) {
            return s;
         }
      }
      return null;
   }
   
   public int getIndex(XYFootprint surfaceOrGrid) {
      if (surfaceOrGrid instanceof SurfaceInterpolator) {
         return surfaces_.indexOf(surfaceOrGrid) + grids_.size();
      }
      return grids_.indexOf(surfaceOrGrid);
   }
   
   //For calling from surface onyl combo box
   public SurfaceInterpolator getSurface(int index) {
      if (index < 0 || index >= surfaces_.size()) {
         return null;
      } 
      return surfaces_.get(index);
   }
   
   //For calling from grid onyl combo box
   public MultiPosGrid getGrid(int index) {
      if (index < 0 || index >= grids_.size()) {
         return null;
      } 
      return grids_.get(index);
   }
   
   public XYFootprint getSurfaceOrGrid(int index) {
      if (index < 0 || index >= surfaces_.size() + grids_.size()) {
         return null;
      } else if (index < grids_.size()) {
         return grids_.get(index);
      }
      return surfaces_.get(index - grids_.size());
   }
   
   public void deleteAll() {
      for (SurfaceInterpolator s: surfaces_) {
         s.delete();
         for (SurfaceGridListener l : listeners_) {
            l.SurfaceOrGridDeleted(s);
         }
      }
      for (MultiPosGrid g : grids_) {
         for (SurfaceGridListener l : listeners_) {
            l.SurfaceOrGridDeleted(g);
         }
      }
      surfaces_.clear();
      grids_.clear();
   }
   
   public void delete(int index) {
      XYFootprint removed;
      if (index < grids_.size()) {
         removed = grids_.remove(index);
         for (SurfaceGridListener l : listeners_) {
            l.SurfaceOrGridDeleted(removed);
         }
      } else {
         removed = surfaces_.remove(index - grids_.size());
         for (SurfaceGridListener l : listeners_) {
            l.SurfaceOrGridDeleted(removed);
         }
         ((SurfaceInterpolator) removed).delete();
      }
   }
   
   public SurfaceInterpolator addNewSurface() {
      if (!MagellanAffineUtils.isAffineTransformDefined()) {
          throw new AffineUndefinedException();
      }
       SurfaceInterpolator s = new SurfaceInterpolatorSimple(Magellan.getCore().getXYStageDevice(), Magellan.getCore().getFocusDevice());
      surfaces_.add(s);
      for (SurfaceGridListener l : listeners_) {
            l.SurfaceOrGridCreated(s);
      }
      return s;
   }
   
   public MultiPosGrid addNewGrid(int rows, int cols, Point2D.Double center) {
      if (!MagellanAffineUtils.isAffineTransformDefined()) {
          throw new AffineUndefinedException();
      }     
       MultiPosGrid grid = new MultiPosGrid(this, Magellan.getCore().getXYStageDevice(), rows, cols, center);
      grids_.add(grid);
      for (SurfaceGridListener l : listeners_) {
            l.SurfaceOrGridCreated(grid);
      }  
      return grid;
   }
   
   public int getNumberOfSurfaces() {
      return surfaces_.size();
   }
   
   public int getNumberOfGrids() {
      return grids_.size();
   }
  
   public String getNewSurfaceName() {
      String base = "New Surface";
      int index = 1;
      String potentialName = base + " " + index;
      while (true) {
         boolean uniqueName = true;
         for (SurfaceInterpolator surface : surfaces_) {
            if (surface.getName() != null && surface.getName().equals(potentialName)) {
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
   
   public String getNewGridName() {
      String base = "New Grid";
      int index = 1;
      String potentialName = base + " " + index;
      while (true) {
         boolean uniqueName = true;
         for (MultiPosGrid region : grids_) {
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

   public void surfaceOrGridUpdated(XYFootprint s) {
      for (SurfaceGridListener l : listeners_) {
         l.SurfaceOrGridChanged(s);
      }
   }
   
   //This is called by tile overlap changing but this should propogate throught o displays to update them
   public void updateAll() {
      for (SurfaceGridListener l : listeners_) {
         for (SurfaceInterpolator s : surfaces_) {
            l.SurfaceOrGridChanged(s);
         }
         for (MultiPosGrid g : grids_) {
            l.SurfaceOrGridChanged(g);
         }
      }
   }
   
   public void rename(XYFootprint xy, String newName) {
      for (SurfaceInterpolator s : surfaces_) {
         if (s == xy) {
            s.rename(newName);
            return;
         }
      }
      for (MultiPosGrid s : grids_) {
         if (s == xy) {
            s.rename(newName);
            return;
         }
      }
      throw new RuntimeException("Couldn't find surface or grid...what?");
   }
   
   public void rename(int row, String newName) throws Exception {
      //Make sure name isnt taken
      for (int i = 0; i < surfaces_.size(); i++) {
         if (i == row - grids_.size()) {
            continue;
         }
         if (surfaces_.get(i).getName().equals(newName)) {
            throw new Exception();
         }
      }
      for (int i = 0; i < grids_.size(); i++) {
         if (i == row) {
            continue;
         }
         if (grids_.get(i).getName().equals(newName) ) {
            throw new Exception();
         }
      } 
      if (row < grids_.size()) {
         grids_.get(row).rename(newName);
         for (SurfaceGridListener l : listeners_) {
            l.SurfaceOrGridRenamed(grids_.get(row));
         }
      } else {
         surfaces_.get(row).rename(newName);
         for (SurfaceGridListener l : listeners_) {
            l.SurfaceOrGridRenamed(surfaces_.get(row));
         }
      }
   }
  
   public void save(GUI gui) {
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

   public void load(GUI gui) {
        File selectedFile = null;
      if (JavaUtils.isMac()) {
         FileDialog fd = new FileDialog(gui, "Load surfaces", FileDialog.LOAD);
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
         fc.setDialogTitle("Save surfaces");
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
            surface.addPoint(NumberUtils.parseDouble(xyz[0]), NumberUtils.parseDouble(xyz[1]), NumberUtils.parseDouble(xyz[2]));
         }
         for (SurfaceGridListener l : listeners_) {
            l.SurfaceOrGridCreated(surface);
         }
      }
   }

   void surfaceOrGridRenamed(XYFootprint gs) {
      for (SurfaceGridListener l : listeners_) {
         l.SurfaceOrGridRenamed(gs);
      }
   }
   
   public void SurfaceInterpolationUpdated(SurfaceInterpolator s) {
      for (SurfaceGridListener l : listeners_) {
           l.SurfaceInterpolationUpdated(s);
      }
   }
}

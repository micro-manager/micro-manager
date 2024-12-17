package org.micromanager.pairedstagecontrol;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.micromanager.Studio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.WindowPositioning;


/**
 * UI part of the LightSheetControlFrame.  ALso does the fitting,
 * and sets the slope and intercept of stage 2 based on data.
 */
public class PairedStageControlFrame extends JFrame {

   private final Studio studio_;
   private String stage1_ = "";
   private String stage2_ = "";
   private String multiStageName_ = "";
   private final RPModel rpm_;
   private JList<ReferencePoint> rpList_;
   private XYChartPlotter plotter_;

   /**
    * Creates new form LightSheetControlForm.
    *
    * @param studio - what do we do without it?
    */
   public PairedStageControlFrame(Studio studio, String multiStageName) {
      studio_ = studio;
      rpm_ = new RPModel();
      multiStageName_ = multiStageName;

      try {
         stage1_ = studio_.core().getProperty(multiStageName_, "PhysicalStage-1");
      } catch (Exception ex) {
         studio_.logs().logError(ex, "Error when requesting Stage1 name");
      }
      try {
         stage2_ = studio_.core().getProperty(multiStageName_, "PhysicalStage-2");
      } catch (Exception ex) {
         studio_.logs().logError(ex, "Error when requesting Stage2 name");
      }

      plotter_ = new XYChartPlotter(stage1_ + "/" + stage2_ + " positions", stage1_, stage2_);
      super.setBounds(100, 100, 362, 595);
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);
      this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      this.setLayout(new MigLayout());

      rpList_ = new JList<>();
      rpList_.setModel(rpm_);
      rpList_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

      final JPanel buttonPanel = new JPanel();
      buttonPanel.setLayout(new MigLayout());

      final String buttonConstraints = "growx 1, wrap";

      final JButton addRPButton = new JButton("Add Reference Point");
      addRPButton.addActionListener(this::addRPButtonActionPerformed);
      buttonPanel.add(addRPButton, buttonConstraints);

      final JButton delRPButton = new JButton("Delete Reference Point");
      delRPButton.addActionListener(this::delRPButtonActionPerformed);
      buttonPanel.add(delRPButton, buttonConstraints);

      final JButton updRPButton = new JButton("Update Reference Point");
      updRPButton.addActionListener(this::updRPButtonActionPerformed);
      buttonPanel.add(updRPButton, buttonConstraints);

      final JButton delAllButton = new JButton("Delete All");
      delAllButton.addActionListener(this::delAllButtonActionPerformed);
      buttonPanel.add(delAllButton, buttonConstraints);

      final JButton goToPosButton = new JButton("Go to Position");
      goToPosButton.addActionListener(this::goToPosButtonActionPerformed);
      buttonPanel.add(goToPosButton, buttonConstraints);

      final JButton loadButton = new JButton("Load from File");
      loadButton.addActionListener(this::loadButtonActionPerformed);
      buttonPanel.add(loadButton, buttonConstraints);

      final JButton saveButton = new JButton("Save to File");
      saveButton.addActionListener(this::saveButtonActionPerformed);
      buttonPanel.add(saveButton, buttonConstraints);

      final JButton helpButton = new JButton("Help");
      helpButton.addActionListener(this::helpButtonActionPerformed);
      buttonPanel.add(helpButton, buttonConstraints);

      final JPanel pointsPanel = new JPanel();
      pointsPanel.setLayout(new MigLayout());
      final JLabel refPointLabel = new JLabel("Reference Points (" + stage1_ + " / " + stage2_
            + ")");
      pointsPanel.add(refPointLabel, "wrap");
      final JScrollPane jScrollPane1 = new JScrollPane();
      jScrollPane1.setViewportView(rpList_);
      pointsPanel.add(jScrollPane1, "grow, pushy 1, wrap");

      this.add(buttonPanel);
      this.add(pointsPanel, "pushy 1, growy, growx 100, wrap");
      pack();
      this.add(plotter_.getChartPanel(), "width " + this.getWidth()
            + ", height " + this.getHeight() + ", push 100, grow, span 2");

      pack();
      this.setTitle("Light Sheet Control");
   }


   private static class RPModel extends AbstractListModel<ReferencePoint> {

      public ReferencePointList rpList;

      private RPModel() {
         this.rpList = new ReferencePointList();
      }

      @Override
      public int getSize() {
         return rpList.getNumberOfPoints();
      }

      @Override
      public ReferencePoint getElementAt(int index) {
         return rpList.getPoint(index);
      }

      public void addPoint(ReferencePoint r) {
         rpList.addPoint(r);
         fireIntervalAdded(this, rpList.getNumberOfPoints(), rpList.getNumberOfPoints());
      }

      public void deletePoint(int index) {
         rpList.removePoint(index);
         fireIntervalRemoved(this, index, index);
      }

      public ReferencePoint getReferencePoint(int index) {
         ReferencePoint r = rpList.getPoint(index);
         return r;
      }

      public void updatePoint(int index, ReferencePoint r) {
         rpList.replacePoint(index, r);
         fireContentsChanged(this, index, index);
      }

      public void clearRegions() {
         this.rpList = new ReferencePointList();
         fireIntervalAdded(this, 0, 0);
      }

      public void updateDisplay() {
         fireContentsChanged(this, 0, rpList.getNumberOfPoints());
      }
   }
    
   private void updateStageRelation() {
      plotter_.addSeries("Data", rpm_.rpList);
      //updates slope and offset parameters for the two stages
      if (rpm_.getSize() > 1) { // need at least two points for linear fit
         // Least Squares regression
         SimpleRegression sr = new SimpleRegression();
         int np = rpm_.getSize();
         for (int n = 0; n < np; n++) {
            ReferencePoint rp = rpm_.getReferencePoint(n);
            sr.addData(rp.getStagePosition1(), rp.getStagePosition2());
         }
         try {
            studio_.core().setProperty(multiStageName_, "Scaling-2", sr.getSlope());
            studio_.core().setProperty(multiStageName_, "TranslationUm-2", sr.getIntercept());
         } catch (Exception ex) {
            studio_.logs().logError(ex,
                  "LightSheetControl: Error when setting scaling and translation");
         }
         plotter_.addFittedLine(sr.getIntercept(), sr.getSlope());
      }
   }

   private void addRPButtonActionPerformed(java.awt.event.ActionEvent evt) {
      ReferencePoint rp = null;
      try {
         rp = new ReferencePoint(studio_.core().getPosition(stage1_),
               studio_.core().getPosition(stage2_));
      } catch (Exception ex) {
         studio_.logs().logError(ex,
               "Error when requesting stage positionsStage 1 / 2 = " + stage1_ + stage2_);
      }
      rpm_.addPoint(rp);
      updateStageRelation();
   }

   private void delRPButtonActionPerformed(ActionEvent evt) {
      rpm_.deletePoint(rpList_.getSelectedIndex());
      updateStageRelation();
   }

   private void updRPButtonActionPerformed(ActionEvent evt) {
      ReferencePoint rp = null;
      try {
         rp = new ReferencePoint(studio_.core().getPosition(stage1_),
               studio_.core().getPosition(stage2_));
      } catch (Exception ex) {
         studio_.logs().logError(ex,
               "Error when requesting stage positions. Stage 1 / 2 = " + stage1_ + stage2_);
      }
      rpm_.updatePoint(rpList_.getSelectedIndex(), rp);
      updateStageRelation();
   }

   private void delAllButtonActionPerformed(ActionEvent evt) {
      rpm_.clearRegions();
      updateStageRelation();
   }

   private void goToPosButtonActionPerformed(ActionEvent evt) {
      ReferencePoint rp = rpm_.getReferencePoint(rpList_.getSelectedIndex());
      try {
         studio_.core().setPosition(stage1_, rp.getStagePosition1());
         studio_.core().setPosition(stage2_, rp.getStagePosition2());
      } catch (Exception ex) {
         studio_.logs().logError(ex, "Error when setting stage positions");
      }
   }

   public static final FileDialogs.FileType LIGHT_SHEET_CONTROL_FILE = new FileDialogs.FileType(
         "LIGHT_SHEET_CONTROL_FILE",
         "LightSheetControl points",
         System.getProperty("user.home") + "/LightSheetControl.txt",
         true, "txt");

   private void loadButtonActionPerformed(ActionEvent evt) {
      File f = FileDialogs.openFile(this, "Load LightSheetControl file", LIGHT_SHEET_CONTROL_FILE);
      if (f != null) {
         String fileName = f.getPath();
         try (FileInputStream fis = new FileInputStream(fileName);
               ObjectInputStream ois = new ObjectInputStream(fis);) {
            rpm_.rpList = (ReferencePointList) ois.readObject();
         } catch (IOException ioe) {
            studio_.logs().showError(ioe, "Error while reading data from: " + fileName, this);
            return;
         } catch (ClassNotFoundException c) {
            studio_.logs().showError(c, "Data do not match expectation", this);
            return;
         }
         rpm_.updateDisplay();
         updateStageRelation();
      }
   }

   private void saveButtonActionPerformed(ActionEvent evt) {
      File f = FileDialogs.openFile(this, "Load LightSheetControl file", LIGHT_SHEET_CONTROL_FILE);
      if (f != null) {
         if (f.exists()) {
            int response = JOptionPane.showConfirmDialog(this,
                  "File already exists. Overwrite?",
                  "Confirm Overwrite",
                  JOptionPane.YES_NO_OPTION);
            if (response == JOptionPane.NO_OPTION) {
               return;
            }
         }
         String fileName = f.getPath();
         try (FileOutputStream fos = new FileOutputStream(fileName);
               ObjectOutputStream oos = new ObjectOutputStream(fos);) {
            oos.writeObject(rpm_.rpList);
         } catch (FileNotFoundException e) {
            studio_.logs().showError(e, "File " + fileName + " not found : ", this);
         } catch (IOException ioe) {
            studio_.logs().showError(ioe, "Error while writing data to: " + fileName, this);
         }
      }
   }

   private void helpButtonActionPerformed(ActionEvent evt) {

      Helper h = new Helper();
      h.start();
   }

   /**
    * There may be easier ways to start a non-blocking thread.
    */
   private class Helper extends Thread {
      Helper() {
         super("Helper");
      }

      @Override
      public void run() {
         try {
            ij.plugin.BrowserLauncher.openURL(
                  "http://micro-manager.org/wiki/PairedStageControl");
         } catch (IOException e1) {
            studio_.logs().showError(e1);
         }
      }

   }

}
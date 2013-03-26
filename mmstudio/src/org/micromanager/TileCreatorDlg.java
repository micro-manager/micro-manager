///////////////////////////////////////////////////////////////////////////////
//FILE:          TileCreatorDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, January 10, 2008

//COPYRIGHT:    University of California, San Francisco, 2008

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package org.micromanager;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.JComboBox;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;

import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

public class TileCreatorDlg extends MMDialog {
   private static final long serialVersionUID = 1L;
   private CMMCore core_;
   private MultiStagePosition[] endPosition_;
   private boolean[] endPositionSet_;
   private PositionListDlg positionListDlg_;

   private JTextField overlapField_;
   private JComboBox overlapUnitsCombo_;
   private enum OverlapUnitEnum {UM, PX, PERCENT};
   private OverlapUnitEnum overlapUnit_ = OverlapUnitEnum.UM;
   private JTextField pixelSizeField_;
   private final JLabel labelLeft_ = new JLabel();
   private final JLabel labelTop_ = new JLabel();
   private final JLabel labelRight_ = new JLabel();
   private final JLabel labelBottom_ = new JLabel();
   private int prefix_ = 0;

   private static final DecimalFormat FMT_POS = new DecimalFormat("000");

   /**
    * Create the dialog
    */
   public TileCreatorDlg(CMMCore core, MMOptions opts, PositionListDlg positionListDlg) {
      super();
      setResizable(false);
      setName("tileDialog");
      getContentPane().setLayout(null);
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            savePosition();
         }
      });
      core_ = core;
      positionListDlg_ = positionListDlg;
      endPosition_ = new MultiStagePosition[4];
      endPositionSet_ = new boolean[4];

      setTitle("Tile Creator");
      setBounds(300, 300, 344, 280);

      Preferences root = Preferences.userNodeForPackage(this.getClass());
      setPrefsNode(root.node(root.absolutePath() + "/TileCreatorDlg"));

      Rectangle r = getBounds();
      //loadPosition(r.x, r.y, r.width, r.height);
      loadPosition(r.x, r.y);

      final JButton goToLeftButton = new JButton();
      goToLeftButton.setFont(new Font("", Font.PLAIN, 10));
      goToLeftButton.setText("Go To");
      goToLeftButton.setBounds(20, 89, 93, 23);
      getContentPane().add(goToLeftButton);
      goToLeftButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if (endPositionSet_[3])
               goToPosition(endPosition_[3]);
         }
      });

      labelLeft_.setFont(new Font("", Font.PLAIN, 8));
      labelLeft_.setHorizontalAlignment(JLabel.CENTER);
      labelLeft_.setText("");
      labelLeft_.setBounds(0, 112, 130, 14);
      getContentPane().add(labelLeft_);

      final JButton setLeftButton = new JButton();
      setLeftButton.setBounds(20, 66, 93, 23);
      setLeftButton.setFont(new Font("", Font.PLAIN, 10));
      setLeftButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            markPosition(3);
            labelLeft_.setText(thisPosition());
         }
      });
      setLeftButton.setText("Set");
      getContentPane().add(setLeftButton);


      labelTop_.setFont(new Font("", Font.PLAIN, 8));
      labelTop_.setHorizontalAlignment(JLabel.CENTER);
      labelTop_.setText("");
      labelTop_.setBounds(115, 51, 130, 14);
      getContentPane().add(labelTop_);

      final JButton goToTopButton = new JButton();
      goToTopButton.setFont(new Font("", Font.PLAIN, 10));
      goToTopButton.setText("Go To");
      goToTopButton.setBounds(133, 28, 93, 23);
      getContentPane().add(goToTopButton);
      goToTopButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if (endPositionSet_[0])
            goToPosition(endPosition_[0]);
         }
      });

      final JButton setTopButton = new JButton();
      setTopButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            markPosition(0);
            labelTop_.setText(thisPosition());
         }
      });
      setTopButton.setBounds(133, 5, 93, 23);
      setTopButton.setFont(new Font("", Font.PLAIN, 10));
      setTopButton.setText("Set");
      getContentPane().add(setTopButton);

      labelRight_.setFont(new Font("", Font.PLAIN, 8));
      labelRight_.setHorizontalAlignment(JLabel.CENTER);
      labelRight_.setText("");
      labelRight_.setBounds(214, 112, 130, 14);
      getContentPane().add(labelRight_);

      final JButton setRightButton = new JButton();
      setRightButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            markPosition(1);
            labelRight_.setText(thisPosition());
         }
      });
      setRightButton.setBounds(234, 66, 93, 23);
      setRightButton.setFont(new Font("", Font.PLAIN, 10));
      setRightButton.setText("Set");
      getContentPane().add(setRightButton);

      labelBottom_.setFont(new Font("", Font.PLAIN, 8));
      labelBottom_.setHorizontalAlignment(JLabel.CENTER);
      labelBottom_.setText("");
      labelBottom_.setBounds(115, 172, 130, 14);
      getContentPane().add(labelBottom_);

      final JButton setBottomButton = new JButton();
      setBottomButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            markPosition(2);
            labelBottom_.setText(thisPosition());
         }
      });
      setBottomButton.setFont(new Font("", Font.PLAIN, 10)); 
      setBottomButton.setText("Set");
      setBottomButton.setBounds(133, 126, 93, 23);
      getContentPane().add(setBottomButton);

      final JButton goToRightButton = new JButton();
      goToRightButton.setFont(new Font("", Font.PLAIN, 10)); 
      goToRightButton.setText("Go To");
      goToRightButton.setBounds(234, 89, 93, 23);
      getContentPane().add(goToRightButton);
      goToRightButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if (endPositionSet_[1])
               goToPosition(endPosition_[1]);
         }
      });

      final JButton goToBottomButton = new JButton();
      goToBottomButton.setFont(new Font("", Font.PLAIN, 10));
      goToBottomButton.setText("Go To");
      goToBottomButton.setBounds(133, 149, 93, 23);
      getContentPane().add(goToBottomButton);
      goToBottomButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if (endPositionSet_[2])
               goToPosition(endPosition_[2]);
         }
      });

      final JLabel overlapLabel = new JLabel();
      overlapLabel.setFont(new Font("", Font.PLAIN, 10)); 
      overlapLabel.setText("Overlap");
      overlapLabel.setBounds(20, 189, 80, 14);
      getContentPane().add(overlapLabel);

      overlapField_ = new JTextField();
      overlapField_.setBounds(70, 186, 50, 20);
      overlapField_.setFont(new Font("", Font.PLAIN, 10));
      overlapField_.setText("0");
      getContentPane().add(overlapField_);

      String[] unitStrings = { "um", "px", "%" };
      overlapUnitsCombo_ = new JComboBox(unitStrings);
      overlapUnitsCombo_.setSelectedIndex(0);
      overlapUnitsCombo_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
             JComboBox cb = (JComboBox)arg0.getSource();
             overlapUnit_ = OverlapUnitEnum.values()[cb.getSelectedIndex()];
         }
      });
      overlapUnitsCombo_.setBounds(130, 186, 60, 20);
      getContentPane().add(overlapUnitsCombo_);

      final JLabel pixelSizeLabel = new JLabel();
      pixelSizeLabel.setFont(new Font("", Font.PLAIN, 10));
      pixelSizeLabel.setText("Pixel Size [um]");
      pixelSizeLabel.setBounds(205, 189, 80, 14);
      getContentPane().add(pixelSizeLabel);

      pixelSizeField_ = new JTextField();
      pixelSizeField_.setFont(new Font("", Font.PLAIN, 10));
      pixelSizeField_.setBounds(280, 186, 50, 20);
      pixelSizeField_.setText(NumberUtils.doubleToDisplayString(core_.getPixelSizeUm()));
      getContentPane().add(pixelSizeField_);

      final JButton okButton = new JButton();
      okButton.setFont(new Font("", Font.PLAIN, 10));
      okButton.setText("OK");
      okButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            savePosition();
            addToPositionList();
         }
      });
      okButton.setBounds(20, 216, 93, 23);
      getContentPane().add(okButton);

      final JButton cancelButton = new JButton();
      cancelButton.setBounds(133, 216, 93, 23);
      cancelButton.setFont(new Font("", Font.PLAIN, 10));
      cancelButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            savePosition();
            dispose();
         }
      });
      cancelButton.setText("Cancel");
      getContentPane().add(cancelButton);

      final JButton resetButton = new JButton();
      resetButton.setBounds(234, 216, 93, 23);
      resetButton.setFont(new Font("", Font.PLAIN, 10));
      resetButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            reset();
         }
      });
      resetButton.setText("Reset");
      getContentPane().add(resetButton);

   }


   /**
    * Store current xyPosition.
    */
   private void markPosition(int location) {
      MultiStagePosition msp = new MultiStagePosition();
      msp.setDefaultXYStage(core_.getXYStageDevice());
      msp.setDefaultZStage(core_.getFocusDevice());

      // read 1-axis stages
      try {
         StrVector stages = core_.getLoadedDevicesOfType(DeviceType.StageDevice);
         for (int i=0; i<stages.size(); i++) {
            StagePosition sp = new StagePosition();
            sp.stageName = stages.get(i);
            sp.numAxes = 1;
            sp.x = core_.getPosition(stages.get(i));
            msp.add(sp);
         }

         // read 2-axis stages
         StrVector stages2D = core_.getLoadedDevicesOfType(DeviceType.XYStageDevice);
         for (int i=0; i<stages2D.size(); i++) {
            StagePosition sp = new StagePosition();
            sp.stageName = stages2D.get(i);
            sp.numAxes = 2;
            sp.x = core_.getXPosition(stages2D.get(i));
            sp.y = core_.getYPosition(stages2D.get(i));
            msp.add(sp);
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }

      endPosition_[location] = msp;
      endPositionSet_[location] = true;

   }

   /**
    * Update display of the current xy position.
    */
   private String thisPosition() {
      StringBuffer sb = new StringBuffer();

      // read 1-axis stages
      try {
         StrVector stages = core_.getLoadedDevicesOfType(DeviceType.StageDevice);
         for (int i=0; i<stages.size(); i++) {
            StagePosition sp = new StagePosition();
            sp.stageName = stages.get(i);
            sp.numAxes = 1;
            sp.x = core_.getPosition(stages.get(i));
            sb.append(sp.getVerbose() + "\n");
         }

         // read 2-axis stages
         StrVector stages2D = core_.getLoadedDevicesOfType(DeviceType.XYStageDevice);
         for (int i=0; i<stages2D.size(); i++) {
            StagePosition sp = new StagePosition();
            sp.stageName = stages2D.get(i);
            sp.numAxes = 2;
            sp.x = core_.getXPosition(stages2D.get(i));
            sp.y = core_.getYPosition(stages2D.get(i));
            sb.append(sp.getVerbose() + "\n");
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }

      return sb.toString();
   }

   /*
    * Create the tile list based on user input, pixelsize, and imagesize
    */
   private void addToPositionList() {
      // check if we are calibrated, TODO: allow input of image size
      double pixSizeUm = 0.0;
      try {
         pixSizeUm = NumberUtils.displayStringToDouble(pixelSizeField_.getText());
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
      if (pixSizeUm <= 0.0) {
         JOptionPane.showMessageDialog(this, "Pixel Size should be a value > 0 (usually 0.1 -1 um).  It should be experimentally determined. ");
         return;
      }

      double overlap = 0.0;
      try {
         overlap = NumberUtils.displayStringToDouble(overlapField_.getText());
      } catch (Exception e) {
         //handleError(e.getMessage());
      }
      boolean correction, transposeXY, mirrorX, mirrorY;
      String camera = core_.getCameraDevice();
      if (camera == null) {
         JOptionPane.showMessageDialog(null, "This function does not work without a camera");
         return;
      }

      try{
      String tmp = core_.getProperty(camera, "TransposeCorrection");
      if (tmp.equals("0"))
         correction = false;
      else
         correction = true;
      tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX());
      if (tmp.equals("0"))
         mirrorX = false;
      else
         mirrorX = true;
      tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY());
      if (tmp.equals("0"))
         mirrorY = false;
      else
         mirrorY = true;
      tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY());
      if (tmp.equals("0"))
         transposeXY = false;
      else
         transposeXY = true;
      } catch(Exception exc) {
            ReportingUtils.showError(exc);
            return;
         }

      double overlapUmX;
      double overlapUmY;

      if(overlapUnit_ == OverlapUnitEnum.UM)
          overlapUmX = overlapUmY = overlap;
      else if(overlapUnit_ == OverlapUnitEnum.PERCENT) {
          overlapUmX = pixSizeUm * (overlap / 100) * core_.getImageWidth();
          overlapUmY = pixSizeUm * (overlap / 100) * core_.getImageHeight();
      } else { // overlapUnit_ == OverlapUnit.PX
          overlapUmX = overlap * pixSizeUm;
          overlapUmY = overlap * pixSizeUm;
      }

      double tmpXUm = pixSizeUm * core_.getImageWidth() - overlapUmX;
      double tmpYUm = pixSizeUm * core_.getImageHeight() - overlapUmY;

      double tileSizeXUm = tmpXUm;
      double tileSizeYUm = tmpYUm ;
         // if camera does not correct image orientation, we'll correct for it here:
      if (!correction) {
            // Order: swapxy, then mirror axis
            if (transposeXY) {tileSizeXUm = tmpYUm; tileSizeYUm = tmpXUm;}
         }

      // Make sure at least two corners were set
      int nrSet = 0;
      for (int i=0; i<4; i++) {
         if (endPositionSet_[i])
            nrSet++;
      }
      if (nrSet < 2) {
         JOptionPane.showMessageDialog(this, "At least two corners should be set");
         return;
      }

      // Calculate a bounding rectangle around the defaultXYStage positions
      // TODO: develop method to deal with multiple axis
      double minX = 0.0, minY = 0.0, maxX = 0.0, maxY = 0.0, meanZ = 0.0;
      boolean firstSet = false;
      StagePosition sp = new StagePosition();
      for (int i=0; i<4; i++) {
         if (endPositionSet_[i]) {
            if (!firstSet) {
               sp = endPosition_[i].get(endPosition_[i].getDefaultXYStage());
               minX = maxX = sp.x;
               minY = maxY = sp.y;
               sp = endPosition_[i].get(endPosition_[i].getDefaultZStage());
               meanZ = sp.x;
               firstSet = true;
            } else {
               sp = endPosition_[i].get(endPosition_[i].getDefaultXYStage());
               if (sp.x < minX)
                  minX = sp.x;
               if (sp.x > maxX)
                  maxX = sp.x;
               if (sp.y < minY)
                  minY = sp.y;
               if (sp.y > maxY)
                  maxY = sp.y;
               sp = endPosition_[i].get(endPosition_[i].getDefaultZStage());
               meanZ += sp.x;
            }
         }
      }
      meanZ = meanZ/nrSet;

      // if there are at least three set points, use them to define a 
      // focus plane: a, b, c such that z = f(x, y) = a*x + b*y + c.

      double zPlaneA = 0.0, zPlaneB = 0.0, zPlaneC = 0.0;
      boolean hasZPlane = false;

      if (nrSet >= 3)
      {
         hasZPlane = true;

         double x1 = 0.0, y1 = 0.0, z1 = 0.0;
         double x2 = 0.0, y2 = 0.0, z2 = 0.0;
         double x3 = 0.0, y3 = 0.0, z3 = 0.0;

         boolean sp1Set = false;
         boolean sp2Set = false;
         boolean sp3Set = false;

         // if there are four points set, we should either (a) choose the
         // three that are least co-linear, or (b) use a linear regression to
         // fit a focus plane that minimizes the errors at the four selected
         // positions.  this code does neither - it just uses the first three
         // positions it finds.

         for (int i=0; i<4; i++) {
            if (endPositionSet_[i] && !sp1Set) {
               x1 = endPosition_[i].get(endPosition_[i].getDefaultXYStage()).x;
               y1 = endPosition_[i].get(endPosition_[i].getDefaultXYStage()).y;
               z1 = endPosition_[i].get(endPosition_[i].getDefaultZStage()).x;
               sp1Set = true;
            } else if (endPositionSet_[i] && !sp2Set) {
               x2 = endPosition_[i].get(endPosition_[i].getDefaultXYStage()).x;
               y2 = endPosition_[i].get(endPosition_[i].getDefaultXYStage()).y;
               z2 = endPosition_[i].get(endPosition_[i].getDefaultZStage()).x;
               sp2Set = true;
            } else if (endPositionSet_[i] && !sp3Set) {
               x3 = endPosition_[i].get(endPosition_[i].getDefaultXYStage()).x;
               y3 = endPosition_[i].get(endPosition_[i].getDefaultXYStage()).y;
               z3 = endPosition_[i].get(endPosition_[i].getDefaultZStage()).x;
               sp3Set = true;
            }
         }
         
         // define vectors 1-->2, 1-->3

         double x12 = x2 - x1;
         double y12 = y2 - y1;
         double z12 = z2 - z1;

         double x13 = x3 - x1;
         double y13 = y3 - y1;
         double z13 = z3 - z1;

         // first, make sure the points aren't co-linear: the angle between
         // vectors 1-->2 and 1-->3 must be "sufficiently" large
 
         double dot_prod = x12 * x13 + y12 * y13 + z12 * z13;
         double magnitude12 = x12 * x12 + y12 * y12 + z12 * z12;
         magnitude12 = Math.sqrt(magnitude12);
         double magnitude13 = x13 * x13 + y13 * y13 + z13 * z13;
         magnitude13 = Math.sqrt(magnitude13);

         double cosTheta = dot_prod / (magnitude12 * magnitude13);
         double theta = Math.acos(cosTheta);  // in RADIANS

         // "sufficiently" large here is 0.5 radians, or about 30 degrees
         if(theta < 0.5 
                 || theta > (2 * Math.PI - 0.5) 
                 || (theta > (Math.PI - 0.5) && theta < (Math.PI + 0.5)))
             hasZPlane = false;

         // intermediates: ax + by + cz + d = 0

         double a = y12 * z13 - y13 * z12;
         double b = z12 * x13 - z13 * x12;
         double c = x12 * y13 - x13 * y12;
         double d = -1 * (a * x1 + b * y1 + c * z1);

         // shuffle to z = f(x, y) = zPlaneA * x + zPlaneB * y + zPlaneC

         zPlaneA = a / (-1 * c);
         zPlaneB = b / (-1 * c);
         zPlaneC = d / (-1 * c);
      }

      // make sure the user wants to continue if they've asked for Z positions
      // but haven't defined enough (non-colinear!) points for a Z plane

      if(positionListDlg_.useDrive(core_.getFocusDevice()) && !hasZPlane)
      {
          int choice = JOptionPane.showConfirmDialog(this, 
                  "You are creating a position list that includes\n" +
                  "a Z (focus) position, but the grid locations\n" +
                  "you've chosen are co-linear!  If you continue,\n" +
                  "the focus for the new positions will be\n" +
                  "the average focus for the grid locations you set.\n\n" +
                  "If you want to calculate the focus for the new\n" +
                  "positions, you must select at least three grid corners\n" +
                  "that aren't co-linear!\n\nContinue?", 
                  "Continue with co-linear focus?",
                  JOptionPane.YES_NO_OPTION);
          if(choice == JOptionPane.NO_OPTION || choice == JOptionPane.CLOSED_OPTION)
              return;

      }

      // calculate number of images in X and Y
      int nrImagesX = (int) Math.floor ( (maxX - minX) / tileSizeXUm ) + 2;
      int nrImagesY = (int) Math.floor ( (maxY - minY) / tileSizeYUm ) + 2;

      // Increment prefix for these positions
      prefix_ += 1;

      // todo handle mirrorX mirrorY
      for (int y=0; y< nrImagesY; y++) {
         for (int x=0; x<nrImagesX; x++) {
            // on even rows go left to right, on odd rows right to left
            int tmpX = x;
            if ( (y & 1) == 1)
               tmpX = nrImagesX - x - 1;
            MultiStagePosition msp = new MultiStagePosition();

            // Add XY position
            msp.setDefaultXYStage(core_.getXYStageDevice());
            msp.setDefaultZStage(core_.getFocusDevice());
            StagePosition spXY = new StagePosition();
            spXY.stageName = core_.getXYStageDevice();
            spXY.numAxes = 2;
            spXY.x = minX + (tmpX * tileSizeXUm);
            spXY.y = minY + (y * tileSizeYUm);
            msp.add(spXY);

            // Add Z position
            StagePosition spZ = new StagePosition();
            spZ.stageName = core_.getFocusDevice();
            spZ.numAxes = 1;

            if(hasZPlane) {
                double z = zPlaneA * spXY.x + zPlaneB * spXY.y + zPlaneC;
                spZ.x = z;
            } else 
                spZ.x = meanZ;

            if (positionListDlg_.useDrive(spZ.stageName))
               msp.add(spZ);
 
            // Add 'metadata'
            msp.setGridCoordinates(y, tmpX);

            if(overlapUnit_ == OverlapUnitEnum.UM || overlapUnit_ == OverlapUnitEnum.PX)
            {
                msp.setProperty("OverlapUm", NumberUtils.doubleToCoreString(overlapUmX));
                int overlapPix = (int) Math.floor(overlapUmX/pixSizeUm);

                msp.setProperty("OverlapPixels", NumberUtils.intToCoreString(overlapPix));
            } else { // overlapUnit_ == OverlapUnit.PERCENT
                // overlapUmX != overlapUmY; store both
                msp.setProperty("OverlapUmX", NumberUtils.doubleToCoreString(overlapUmX));
                msp.setProperty("OverlapUmY", NumberUtils.doubleToCoreString(overlapUmY));
                int overlapPixX = (int) Math.floor(overlapUmX/pixSizeUm);
                int overlapPixY = (int) Math.floor(overlapUmY/pixSizeUm);
                msp.setProperty("OverlapPixelsX", NumberUtils.intToCoreString(overlapPixX));
                msp.setProperty("OverlapPixelsY", NumberUtils.intToCoreString(overlapPixX));
            }

            // Add to position list
            positionListDlg_.addPosition(msp, generatePosLabel(prefix_ + "-Pos", tmpX, y));
         }
      }

      dispose();
   }

   /*
    * Delete all positions from the dialog and update labels.  Re-read pixel calibration - when available - from the core 
    */
   private void reset() {
      for (int i=0; i<4; i++) 
         endPositionSet_[i] = false;
      labelTop_.setText("");
      labelRight_.setText("");
      labelBottom_.setText("");
      labelLeft_.setText("");
      double pxsz = core_.getPixelSizeUm();
      pixelSizeField_.setText(NumberUtils.doubleToDisplayString(pxsz));
   }
   
   /*
    * Move stage to position
    */
   private void goToPosition(MultiStagePosition position) {
      try { 
         MultiStagePosition.goToPosition(position, core_);
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   private void handleError(String txt) {
      JOptionPane.showMessageDialog(this, txt);      
   }

   public static String generatePosLabel(String prefix, int x, int y) {
      String name = prefix + "_" + FMT_POS.format(x) + "_" + FMT_POS.format(y);
      return name;
   }

}

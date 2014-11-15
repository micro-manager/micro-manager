///////////////////////////////////////////////////////////////////////////////
//FILE:          TileCreatorDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, January 10, 2008

//COPYRIGHT:    University of California, San Francisco, 2008 - 2014

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package org.micromanager.positionlist;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.ParseException;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.JComboBox;

import mmcorej.CMMCore;
import mmcorej.MMCoreJ;

import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.StagePosition;
import org.micromanager.MMOptions;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

public class TileCreatorDlg extends MMDialog implements MMListenerInterface {
   private static final long serialVersionUID = 1L;
   private CMMCore core_;
   private MultiStagePosition[] endPosition_;
   private boolean[] endPositionSet_;
   private PositionListDlg positionListDlg_;

   private JTextField overlapField_;
   private JComboBox overlapUnitsCombo_;
   
   private enum OverlapUnitEnum {UM, PX, PERCENT};
   private OverlapUnitEnum overlapUnit_ = OverlapUnitEnum.UM;
   private int centeredFrames_ = 0;
   private JTextField pixelSizeField_;
   private final JLabel labelLeft_ = new JLabel();
   private final JLabel labelTop_ = new JLabel();
   private final JLabel labelRight_ = new JLabel();
   private final JLabel labelBottom_ = new JLabel();
   private final JLabel labelWidth_ = new JLabel();
   private final JLabel labelWidthUmPx_ = new JLabel();
   private static int prefix_ = 0;

   private static final DecimalFormat FMT_POS = new DecimalFormat("000");

   /**
    * Create the dialog
    * @param core - Micro-Manager Core object
    * @param opts - App wide settings stored in singleton MMOptions object
    * @param positionListDlg - The position list dialog
    */
   public TileCreatorDlg(CMMCore core, MMOptions opts, 
           PositionListDlg positionListDlg) {
      super();
      setResizable(false);
      setName("tileDialog");
      getContentPane().setLayout(null);
      
      core_ = core;
      positionListDlg_ = positionListDlg;   
      positionListDlg_.activateAxisTable(false);
      endPosition_ = new MultiStagePosition[4];
      endPositionSet_ = new boolean[4];

      setTitle("Tile Creator");

      loadAndRestorePosition(300, 300);
      this.setSize(344, 280);

      final JButton goToLeftButton = new JButton();
      goToLeftButton.setFont(new Font("", Font.PLAIN, 10));
      goToLeftButton.setText("Go To");
      goToLeftButton.setBounds(20, 89, 93, 23);
      getContentPane().add(goToLeftButton);
      goToLeftButton.addActionListener(new ActionListener() {
         @Override
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
         @Override
         public void actionPerformed(ActionEvent arg0) {
            labelLeft_.setText(thisPosition(markPosition(3)));
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
      goToTopButton.setBounds(129, 28, 93, 23);
      getContentPane().add(goToTopButton);
      goToTopButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            if (endPositionSet_[0])
            goToPosition(endPosition_[0]);
         }
      });

      final JButton setTopButton = new JButton();
      setTopButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            labelTop_.setText(thisPosition(markPosition(0)));
         }
      });
      setTopButton.setBounds(129, 5, 93, 23);
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
         @Override
         public void actionPerformed(ActionEvent arg0) {
            labelRight_.setText(thisPosition(markPosition(1)));
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
         @Override
         public void actionPerformed(ActionEvent arg0) {
            labelBottom_.setText(thisPosition(markPosition(2)));
         }
      });
      setBottomButton.setFont(new Font("", Font.PLAIN, 10)); 
      setBottomButton.setText("Set");
      setBottomButton.setBounds(129, 126, 93, 23);
      getContentPane().add(setBottomButton);

      final JButton goToRightButton = new JButton();
      goToRightButton.setFont(new Font("", Font.PLAIN, 10)); 
      goToRightButton.setText("Go To");
      goToRightButton.setBounds(234, 89, 93, 23);
      getContentPane().add(goToRightButton);
      goToRightButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            if (endPositionSet_[1])
               goToPosition(endPosition_[1]);
         }
      });

      final JButton goToBottomButton = new JButton();
      goToBottomButton.setFont(new Font("", Font.PLAIN, 10));
      goToBottomButton.setText("Go To");
      goToBottomButton.setBounds(129, 149, 93, 23);
      getContentPane().add(goToBottomButton);
      goToBottomButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            if (endPositionSet_[2])
               goToPosition(endPosition_[2]);
         }
      });

      final JButton gridCenteredHereButton = new JButton();
      gridCenteredHereButton.setFont(new Font("", Font.PLAIN, 10));
      gridCenteredHereButton.setText("Center Here");
      gridCenteredHereButton.setBounds(129, 66, 93, 23);
      gridCenteredHereButton.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
             try {
              centerGridHere();
             } catch (TileCreatorException tex) {
                // zero pixel size exception. User was already told 
             }
          }
      });
      getContentPane().add(gridCenteredHereButton);
 
      final JButton centeredPlusButton = new JButton();
      centeredPlusButton.setFont(new Font("", Font.PLAIN, 10));
      centeredPlusButton.setText("+");
      centeredPlusButton.setBounds(184, 89, 38, 19);
      centeredPlusButton.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
              ++centeredFrames_;
              labelWidth_.setText(String.format("%dx%d", centeredFrames_, centeredFrames_));
              updateCenteredSizeLabel();
  
          }
      });
      getContentPane().add(centeredPlusButton);
 
      labelWidth_.setFont(new Font("", Font.PLAIN, 10));
      labelWidth_.setHorizontalAlignment(JLabel.CENTER);
      labelWidth_.setText("");
      labelWidth_.setBounds(157, 89, 37, 19);
      getContentPane().add(labelWidth_);
 
      final JButton centeredMinusButton = new JButton();
      centeredMinusButton.setFont(new Font("", Font.PLAIN, 10));
      centeredMinusButton.setText("-");
      centeredMinusButton.setBounds(129, 89, 38, 19);
      centeredMinusButton.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
              --centeredFrames_;
              if(centeredFrames_ < 1)
                 centeredFrames_ = 1;
              labelWidth_.setText(String.format("%dx%d", centeredFrames_, centeredFrames_));
              updateCenteredSizeLabel();
          }
      });
      getContentPane().add(centeredMinusButton);
 
      labelWidthUmPx_.setFont(new Font("", Font.PLAIN, 8));
      labelWidthUmPx_.setHorizontalAlignment(JLabel.CENTER);
      labelWidthUmPx_.setText("");
      labelWidthUmPx_.setBounds(129, 108, 93, 14);
      getContentPane().add(labelWidthUmPx_);
 
      final JLabel overlapLabel = new JLabel();
      overlapLabel.setFont(new Font("", Font.PLAIN, 10)); 
      overlapLabel.setText("Overlap");
      overlapLabel.setBounds(20, 189, 80, 14);
      getContentPane().add(overlapLabel);

      overlapField_ = new JTextField();
      overlapField_.setBounds(70, 186, 50, 20);
      overlapField_.setFont(new Font("", Font.PLAIN, 10));
      overlapField_.setText("0");
      overlapField_.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
              updateCenteredSizeLabel();
          }
      });

      getContentPane().add(overlapField_);

      String[] unitStrings = { "um", "px", "%" };
      overlapUnitsCombo_ = new JComboBox(unitStrings);
      overlapUnitsCombo_.setSelectedIndex(0);
      overlapUnitsCombo_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
             JComboBox cb = (JComboBox)arg0.getSource();
             overlapUnit_ = OverlapUnitEnum.values()[cb.getSelectedIndex()];
             updateCenteredSizeLabel();
         }
      });
      overlapUnitsCombo_.setBounds(125, 186, 75, 20);
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
      pixelSizeField_.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
              updateCenteredSizeLabel();
          }
      });
      getContentPane().add(pixelSizeField_);

      final JButton okButton = new JButton();
      okButton.setFont(new Font("", Font.PLAIN, 10));
      okButton.setText("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            addToPositionList();
         }
      });
      okButton.setBounds(20, 216, 93, 23);
      getContentPane().add(okButton);

      final JButton cancelButton = new JButton();
      cancelButton.setBounds(129, 216, 93, 23);
      cancelButton.setFont(new Font("", Font.PLAIN, 10));
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) { 
            positionListDlg_.activateAxisTable(true);
            dispose();
         }
      });
      cancelButton.setText("Cancel");
      getContentPane().add(cancelButton);

      final JButton resetButton = new JButton();
      resetButton.setBounds(234, 216, 93, 23);
      resetButton.setFont(new Font("", Font.PLAIN, 10));
      resetButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            reset();
         }
      });
      resetButton.setText("Reset");
      getContentPane().add(resetButton); 


   }


   /**
    * Store current xyPosition.
    * Only store positions of drives selected in PositionList
    */
   private MultiStagePosition markPosition(int location) {
      MultiStagePosition msp = new MultiStagePosition();
      
      try {
         // read 1-axis stages
         final String zStage = positionListDlg_.get1DAxis();
         if (zStage != null) {
            msp.setDefaultZStage(zStage);
            StagePosition sp = new StagePosition();
            sp.stageName = zStage;
            sp.numAxes = 1;
            sp.x = core_.getPosition(zStage);
            msp.add(sp);
         }

         // and 2 axis default stage
         final String xyStage = positionListDlg_.get2DAxis();
         if (xyStage != null) {
            msp.setDefaultXYStage(xyStage);
            StagePosition sp = new StagePosition();
            sp.stageName = xyStage;
            sp.numAxes = 2;
            sp.x = core_.getXPosition(xyStage);
            sp.y = core_.getYPosition(xyStage);
            msp.add(sp);
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }

      endPosition_[location] = msp;
      endPositionSet_[location] = true;
      
      return msp;

   }

   /**
    * Update display of the current xy position.
    */
   private String thisPosition(MultiStagePosition msp) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < msp.size(); i++) {
         StagePosition sp = msp.get(i);
         sb.append(sp.getVerbose()).append("\n");
      }
      
      return sb.toString();
   }

   /**
    * Updates the labelWidthUmPx_ field when the number of frames in
    * a centered grid is changed.
    */

   private void updateCenteredSizeLabel()  {
      try {
       double[] centeredSize = getCenteredSize();
       if(centeredSize[0] == 0.0)
           labelWidthUmPx_.setText("");
       else
           labelWidthUmPx_.setText(
                   Integer.toString((int)centeredSize[0]) + "x" + 
                   Integer.toString((int)centeredSize[1]) + "um");
      } catch (TileCreatorException tex) {
         // most likely zero pixel size no need to update
      }
   }

   /**
    * Compute the um size of an nxn grid.
    */

   private double[] getCenteredSize() throws TileCreatorException {
      double imageSizeXUm = getImageSize()[0];
      double imageSizeYUm = getImageSize()[1];

      double tileSizeXUm = getTileSize()[0];
      double tileSizeYUm = getTileSize()[1];

      double overlapXUm = imageSizeXUm - tileSizeXUm;
      double overlapYUm = imageSizeYUm - tileSizeYUm;

      double totalXUm = tileSizeXUm * centeredFrames_ + overlapXUm;
      double totalYUm = tileSizeYUm * centeredFrames_ + overlapYUm;

      return new double[] {totalXUm, totalYUm};
   }

   /**
    * Updates all four positions to create a grid that is centered at the
    * current location and has a total diameter with the specified number
    * of frames.
    */
   private void centerGridHere()  throws TileCreatorException {
      double imageSizeXUm = getImageSize()[0];
      double imageSizeYUm = getImageSize()[1];

      double tileSizeXUm = getTileSize()[0];
      double tileSizeYUm = getTileSize()[1];

      double overlapXUm = imageSizeXUm - tileSizeXUm;
      double overlapYUm = imageSizeYUm - tileSizeYUm;

      double [] centeredSize = getCenteredSize();
      if(centeredSize[0] == 0.0)
          return;

      double offsetXUm = centeredSize[0] / 2.0 - imageSizeXUm / 2.0 - 1;
      double offsetYUm = centeredSize[1] / 2.0 - imageSizeYUm / 2.0 - 1;

      for (int location = 0; location < 4; ++location) {
         // get the current position
         MultiStagePosition msp = new MultiStagePosition();
         StringBuilder sb = new StringBuilder();

         // read 1-axis stages
         try {
            final String zStage = positionListDlg_.get1DAxis();
            if (zStage != null) {
               msp.setDefaultZStage(zStage);
               StagePosition sp = new StagePosition();
               sp.stageName = zStage;
               sp.numAxes = 1;
               sp.x = core_.getPosition(zStage);
               msp.add(sp);
               sb.append(sp.getVerbose()).append("\n");
            }

            // read 2-axis stages
            final String xyStage = positionListDlg_.get2DAxis();
            if (xyStage != null) {
               msp.setDefaultXYStage(xyStage);
               StagePosition sp = new StagePosition();
               sp.stageName = xyStage;
               sp.numAxes = 2;
               sp.x = core_.getXPosition(xyStage);
               sp.y = core_.getYPosition(xyStage);

               switch (location) {
                  case 0: // top
                     sp.y += offsetYUm;
                     break;
                  case 1: // right
                     sp.x += offsetXUm;
                     break;
                  case 2: // bottom
                     sp.y -= offsetYUm;
                     break;
                  case 3: // left
                     sp.x -= offsetXUm;
                     break;
               }
               msp.add(sp);
               sb.append(sp.getVerbose()).append("\n");
            }
         } catch (Exception e) {
            ReportingUtils.showError(e);
         }

         endPosition_[location] = msp;
         endPositionSet_[location] = true;

         switch (location) {
            case 0: // top
               labelTop_.setText(sb.toString());
               break;
            case 1: // right
               labelRight_.setText(sb.toString());
               break;
            case 2: // bottom
               labelBottom_.setText(sb.toString());
               break;
            case 3: // left
               labelLeft_.setText(sb.toString());
               break;
         }
      }
   }

   private boolean isSwappedXY() {
      boolean correction, transposeXY, mirrorX, mirrorY;
      String camera = core_.getCameraDevice();
      if (camera == null) {
         JOptionPane.showMessageDialog(null, "This function does not work without a camera");
         return false;
      }

      try {
         String tmp = core_.getProperty(camera, "TransposeCorrection");
         if (tmp.equals("0")) {
            correction = false;
         } else {
            correction = true;
         }
         tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX());
         if (tmp.equals("0")) {
            mirrorX = false;
         } else {
            mirrorX = true;
         }
         tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY());
         if (tmp.equals("0")) {
            mirrorY = false;
         } else {
            mirrorY = true;
         }

         tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY());
         if (tmp.equals("0")) {
            transposeXY = false;
         } else {
            transposeXY = true;
         }
      } catch (Exception exc) {
         ReportingUtils.showError(exc);
         return false;
      }

      return !correction && transposeXY;
   }

   private double getPixelSizeUm() throws TileCreatorException {
      // check if we are calibrated, TODO: allow input of image size
      double pixSizeUm = 0.0;
      try {
         pixSizeUm = NumberUtils.displayStringToDouble(pixelSizeField_.getText());
      } catch (ParseException e) {
         ReportingUtils.logError(e);
      }
      if (pixSizeUm <= 0.0) {
         JOptionPane.showMessageDialog(this, "Pixel Size should be a value > 0 (usually 0.1 -1 um).  It should be experimentally determined. ");
         throw new TileCreatorException("Zero pixel size");
      }

      return pixSizeUm;
   }
   

   private double[] getTileSize() throws TileCreatorException {
      double pixSizeUm = getPixelSizeUm();
      double overlap = 0.0;
      try {
         overlap = NumberUtils.displayStringToDouble(overlapField_.getText());
      } catch (ParseException e) {
         ReportingUtils.logError(e, "Number Parse error in Tile Creator Dialog");
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

      // if camera does not correct image orientation, we'll correct for it here:
      boolean swapXY = isSwappedXY();

      double tileSizeXUm = swapXY ? 
                           pixSizeUm * core_.getImageHeight() - overlapUmY :
                           pixSizeUm * core_.getImageWidth() - overlapUmX;

      double tileSizeYUm = swapXY ? 
                           pixSizeUm * core_.getImageWidth() - overlapUmX :
                           pixSizeUm * core_.getImageHeight() - overlapUmY;

      return new double[] {tileSizeXUm, tileSizeYUm};
   }

   private double[] getImageSize() throws TileCreatorException {     
      double pixSizeUm = getPixelSizeUm();
      boolean swapXY = isSwappedXY();
      double imageSizeXUm = swapXY ? pixSizeUm * core_.getImageHeight() : 
                                     pixSizeUm * core_.getImageWidth();
      double imageSizeYUm = swapXY ? pixSizeUm * core_.getImageWidth() :
                                     pixSizeUm * core_.getImageHeight();

      return new double[] {imageSizeXUm, imageSizeYUm};
   }


   /*
    * Create the tile list based on user input, pixelsize, and imagesize
    */
   private void addToPositionList() {
      // Sanity check: don't create any positions if there is no XY stage to
      // use.
      if (positionListDlg_.get2DAxis() == null) {
         return;
      }
      try {
         // Make sure at least two corners were set
         int nrSet = 0;
         for (int i = 0; i < 4; i++) {
            if (endPositionSet_[i]) {
               nrSet++;
            }
         }
         if (nrSet < 2) {
            JOptionPane.showMessageDialog(this, "At least two corners should be set");
            return;
         }

         boolean hasZPlane = (nrSet >= 3) && (positionListDlg_.get1DAxis() != null);

         // Calculate a bounding rectangle around the defaultXYStage positions
         // TODO: develop method to deal with multiple axis
         double minX = Double.POSITIVE_INFINITY;
         double minY = Double.POSITIVE_INFINITY;
         double maxX = Double.NEGATIVE_INFINITY;
         double maxY = Double.NEGATIVE_INFINITY;
         double meanZ = 0.0;
         StagePosition sp;
         for (int i = 0; i < 4; i++) {
            if (endPositionSet_[i]) {
               sp = endPosition_[i].get(endPosition_[i].getDefaultXYStage());
               if (sp.x < minX) {
                  minX = sp.x;
               }
               if (sp.x > maxX) {
                  maxX = sp.x;
               }
               if (sp.y < minY) {
                  minY = sp.y;
               }
               if (sp.y > maxY) {
                  maxY = sp.y;
               }
               if (hasZPlane) {
                  sp = endPosition_[i].get(endPosition_[i].getDefaultZStage());
                  meanZ += sp.x;
               }
            }
         }

         meanZ = meanZ / nrSet;

         // if there are at least three set points, use them to define a 
         // focus plane: a, b, c such that z = f(x, y) = a*x + b*y + c.

         double zPlaneA = 0.0, zPlaneB = 0.0, zPlaneC = 0.0;

         if (hasZPlane) {
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

            for (int i = 0; i < 4; i++) {
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
            if (theta < 0.5
                    || theta > (2 * Math.PI - 0.5)
                    || (theta > (Math.PI - 0.5) && theta < (Math.PI + 0.5))) {
               hasZPlane = false;
            }
            if (Double.isNaN(theta)) {
               hasZPlane = false;
            }

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

         double pixSizeUm = getPixelSizeUm();

         double imageSizeXUm = getImageSize()[0];
         double imageSizeYUm = getImageSize()[1];

         double tileSizeXUm = getTileSize()[0];
         double tileSizeYUm = getTileSize()[1];

         double overlapXUm = imageSizeXUm - tileSizeXUm;
         double overlapYUm = imageSizeYUm - tileSizeYUm;

         // bounding box size
         double boundingXUm = maxX - minX + imageSizeXUm;
         double boundingYUm = maxY - minY + imageSizeYUm;

         // calculate number of images in X and Y
         int nrImagesX = (int) Math.ceil((boundingXUm - overlapXUm) / tileSizeXUm);
         int nrImagesY = (int) Math.ceil((boundingYUm - overlapYUm) / tileSizeYUm);

         double totalSizeXUm = nrImagesX * tileSizeXUm + overlapXUm;
         double totalSizeYUm = nrImagesY * tileSizeYUm + overlapYUm;

         double offsetXUm = (totalSizeXUm - boundingXUm) / 2;
         double offsetYUm = (totalSizeYUm - boundingYUm) / 2;

         // Increment prefix for these positions
         prefix_ += 1;

         // todo handle mirrorX mirrorY
         for (int y = 0; y < nrImagesY; y++) {
            for (int x = 0; x < nrImagesX; x++) {
               // on even rows go left to right, on odd rows right to left
               int tmpX = x;
               if ((y & 1) == 1) {
                  tmpX = nrImagesX - x - 1;
               }
               MultiStagePosition msp = new MultiStagePosition();

               // Add XY position
               final String xyStage = positionListDlg_.get2DAxis();
               // xyStage is not null; we've checked above.
               msp.setDefaultXYStage(xyStage);
               StagePosition spXY = new StagePosition();
               spXY.stageName = xyStage;
               spXY.numAxes = 2;
               spXY.x = minX - offsetXUm + (tmpX * tileSizeXUm);
               spXY.y = minY - offsetYUm + (y * tileSizeYUm);
               msp.add(spXY);

               // Add Z position
               final String zStage = positionListDlg_.get1DAxis();
               if (zStage != null) {
                  msp.setDefaultZStage(zStage);
                  StagePosition spZ = new StagePosition();
                  spZ.stageName = zStage;
                  spZ.numAxes = 1;

                  if (hasZPlane) {
                     double z = zPlaneA * spXY.x + zPlaneB * spXY.y + zPlaneC;
                     spZ.x = z;
                  } else {
                     spZ.x = meanZ;
                  }

                  msp.add(spZ);
               }

               // Add 'metadata'
               msp.setGridCoordinates(y, tmpX);

               if (overlapUnit_ == OverlapUnitEnum.UM || overlapUnit_ == OverlapUnitEnum.PX) {
                  msp.setProperty("OverlapUm", NumberUtils.doubleToCoreString(overlapXUm));
                  int overlapPix = (int) Math.floor(overlapXUm / pixSizeUm);

                  msp.setProperty("OverlapPixels", NumberUtils.intToCoreString(overlapPix));
               } else { // overlapUnit_ == OverlapUnit.PERCENT
                  // overlapUmX != overlapUmY; store both
                  msp.setProperty("OverlapUmX", NumberUtils.doubleToCoreString(overlapXUm));
                  msp.setProperty("OverlapUmY", NumberUtils.doubleToCoreString(overlapYUm));
                  int overlapPixX = (int) Math.floor(overlapXUm / pixSizeUm);
                  int overlapPixY = (int) Math.floor(overlapYUm / pixSizeUm);
                  msp.setProperty("OverlapPixelsX", NumberUtils.intToCoreString(overlapPixX));
                  msp.setProperty("OverlapPixelsY", NumberUtils.intToCoreString(overlapPixY));
               }

               // Add to position list
               positionListDlg_.addPosition(msp, generatePosLabel(prefix_ + "-Pos", tmpX, y));
            }
         }

         positionListDlg_.activateAxisTable(true);
         dispose();
      } catch (TileCreatorException tex) {
         // user was already warned
      }
   }

   /**
    * Delete all positions from the dialog and update labels. Re-read pixel
    * calibration - when available - from the core
    */
   private void reset() {
      for (int i = 0; i < 4; i++) {
         endPositionSet_[i] = false;
      }
      labelTop_.setText("");
      labelRight_.setText("");
      labelBottom_.setText("");
      labelLeft_.setText("");
      labelWidth_.setText("");
      labelWidthUmPx_.setText("");

      double pxsz = core_.getPixelSizeUm();
      pixelSizeField_.setText(NumberUtils.doubleToDisplayString(pxsz));
      centeredFrames_ = 0;
   }

   /**
    * Move stage to position
    */
   private void goToPosition(MultiStagePosition position) {
      try {
         MultiStagePosition.goToPosition(position, core_);
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   public static String generatePosLabel(String prefix, int x, int y) {
      String name = prefix + "_" + FMT_POS.format(x) + "_" + FMT_POS.format(y);
      return name;
   }

   // Implementation of MMListenerInterface
   @Override
   public void propertiesChangedAlert() {
   }

   @Override
   public void propertyChangedAlert(String device, String property, String value) {
   }

   @Override
   public void configGroupChangedAlert(String groupName, String newConfig) {
   }

   @Override
   public void systemConfigurationLoaded() {
   }

   @Override
   public void pixelSizeChangedAlert(double newPixelSizeUm) {
      pixelSizeField_.setText(NumberUtils.doubleToDisplayString(newPixelSizeUm));
      updateCenteredSizeLabel();
   }

   @Override
   public void stagePositionChangedAlert(String deviceName, double pos) {
   }

   @Override
   public void xyStagePositionChanged(String deviceName, double xPos, double yPos) {
   }

   @Override
   public void exposureChanged(String cameraName, double newExposureTime) {
   }

   @Override
   public void slmExposureChanged(String cameraName, double newExposureTime) {
   }

   private class TileCreatorException extends Exception {

      private static final long serialVersionUID = -84723856111238971L;
      private Throwable cause;
      private static final String MSG_PREFIX = "MMScript error: ";

      public TileCreatorException(String message) {
         super(MSG_PREFIX + message);
      }

      public TileCreatorException(Throwable t) {
         super(MSG_PREFIX + t.getMessage());
         this.cause = t;
      }

      @Override
      public Throwable getCause() {
         return this.cause;
      }
   }
}

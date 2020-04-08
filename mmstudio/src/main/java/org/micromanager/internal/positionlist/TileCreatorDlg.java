///////////////////////////////////////////////////////////////////////////////
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


package org.micromanager.internal.positionlist;

import com.google.common.eventbus.Subscribe;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.ParseException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.Studio;
import org.micromanager.events.PixelSizeChangedEvent;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.internal.utils.MMDialog;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.positionlist.utils.TileCreator;
import org.micromanager.internal.positionlist.utils.ZGenerator;
import org.micromanager.propertymap.MutablePropertyMapView;

public final class TileCreatorDlg extends MMDialog {
   private static final long serialVersionUID = 1L;
   private final CMMCore core_;
   private final Studio studio_;
   private final TileCreator tileCreator_;
   private MultiStagePosition[] endPosition_;
   private boolean[] endPositionSet_;
   private PositionListDlg positionListDlg_;

   private JTextField overlapField_;
   private JComboBox overlapUnitsCombo_;
   
   
   private TileCreator.OverlapUnitEnum overlapUnit_ = TileCreator.OverlapUnitEnum.UM;
   private int centeredFrames_ = 0;
   private JTextField pixelSizeField_;
   private final JLabel labelLeft_ = new JLabel();
   private final JLabel labelTop_ = new JLabel();
   private final JLabel labelRight_ = new JLabel();
   private final JLabel labelBottom_ = new JLabel();
   private final JLabel labelWidth_ = new JLabel();
   private final JLabel labelWidthUmPx_ = new JLabel();
   private static int prefix_ = 0;

   private static final String OVERLAP_PREF = "overlap";

   /**
    * Create the dialog
    * @param core - Micro-Manager Core object
    * @param studio - the Micro-Manager UI 
    * @param positionListDlg - The position list dialog
    */
   public TileCreatorDlg(final CMMCore core, final Studio studio,
           final PositionListDlg positionListDlg) {
      super("grid tile creator");
      super.setResizable(false);
      super.setName("tileDialog");
      super.getContentPane().setLayout(null);
      
      core_ = core;
      studio_ = studio;
      tileCreator_ = new TileCreator(core_);
      positionListDlg_ = positionListDlg;   
      positionListDlg_.activateAxisTable(false);
      endPosition_ = new MultiStagePosition[4];
      endPositionSet_ = new boolean[4];

      MutablePropertyMapView settings = studio.profile().getSettings(
              TileCreatorDlg.class);

      super.setTitle("Tile Creator");

      super.loadAndRestorePosition(300, 300);
      super.setSize(344, 280);

      final JButton goToLeftButton = new JButton();
      goToLeftButton.setFont(new Font("", Font.PLAIN, 10));
      goToLeftButton.setText("Go To");
      goToLeftButton.setBounds(20, 89, 93, 23);
      super.getContentPane().add(goToLeftButton);
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
      super.getContentPane().add(labelLeft_);

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
      super.getContentPane().add(setLeftButton);


      labelTop_.setFont(new Font("", Font.PLAIN, 8));
      labelTop_.setHorizontalAlignment(JLabel.CENTER);
      labelTop_.setText("");
      labelTop_.setBounds(115, 51, 130, 14);
      super.getContentPane().add(labelTop_);

      final JButton goToTopButton = new JButton();
      goToTopButton.setFont(new Font("", Font.PLAIN, 10));
      goToTopButton.setText("Go To");
      goToTopButton.setBounds(129, 28, 93, 23);
      super.getContentPane().add(goToTopButton);
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
      super.getContentPane().add(setTopButton);

      labelRight_.setFont(new Font("", Font.PLAIN, 8));
      labelRight_.setHorizontalAlignment(JLabel.CENTER);
      labelRight_.setText("");
      labelRight_.setBounds(214, 112, 130, 14);
      super.getContentPane().add(labelRight_);

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
      super.getContentPane().add(setRightButton);

      labelBottom_.setFont(new Font("", Font.PLAIN, 8));
      labelBottom_.setHorizontalAlignment(JLabel.CENTER);
      labelBottom_.setText("");
      labelBottom_.setBounds(115, 172, 130, 14);
      super.getContentPane().add(labelBottom_);

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
      super.getContentPane().add(setBottomButton);

      final JButton goToRightButton = new JButton();
      goToRightButton.setFont(new Font("", Font.PLAIN, 10)); 
      goToRightButton.setText("Go To");
      goToRightButton.setBounds(234, 89, 93, 23);
      super.getContentPane().add(goToRightButton);
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
      super.getContentPane().add(goToBottomButton);
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
      super.getContentPane().add(gridCenteredHereButton);
 
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
      super.getContentPane().add(centeredPlusButton);
 
      labelWidth_.setFont(new Font("", Font.PLAIN, 10));
      labelWidth_.setHorizontalAlignment(JLabel.CENTER);
      labelWidth_.setText("");
      labelWidth_.setBounds(157, 89, 37, 19);
      super.getContentPane().add(labelWidth_);
 
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
      super.getContentPane().add(centeredMinusButton);
 
      labelWidthUmPx_.setFont(new Font("", Font.PLAIN, 8));
      labelWidthUmPx_.setHorizontalAlignment(JLabel.CENTER);
      labelWidthUmPx_.setText("");
      labelWidthUmPx_.setBounds(129, 108, 93, 14);
      super.getContentPane().add(labelWidthUmPx_);
 
      final JLabel overlapLabel = new JLabel();
      overlapLabel.setFont(new Font("", Font.PLAIN, 10)); 
      overlapLabel.setText("Overlap");
      overlapLabel.setBounds(20, 189, 80, 14);
      super.getContentPane().add(overlapLabel);

      overlapField_ = new JTextField();
      overlapField_.setBounds(70, 186, 50, 20);
      overlapField_.setFont(new Font("", Font.PLAIN, 10));
      overlapField_.setText(settings.getString(OVERLAP_PREF, "0"));
      overlapField_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            settings.putString(OVERLAP_PREF, overlapField_.getText() );
            updateCenteredSizeLabel();
         }
      });

      super.getContentPane().add(overlapField_);

      String[] unitStrings = { "um", "px", "%" };
      overlapUnitsCombo_ = new JComboBox(unitStrings);
      overlapUnitsCombo_.setSelectedIndex(0);
      overlapUnitsCombo_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
             JComboBox cb = (JComboBox)arg0.getSource();
             overlapUnit_ = TileCreator.OverlapUnitEnum.values()[cb.getSelectedIndex()];
             updateCenteredSizeLabel();
         }
      });
      overlapUnitsCombo_.setBounds(125, 186, 75, 20);
      super.getContentPane().add(overlapUnitsCombo_);

      final JLabel pixelSizeLabel = new JLabel();
      pixelSizeLabel.setFont(new Font("", Font.PLAIN, 10));
      pixelSizeLabel.setText("Pixel Size [um]");
      pixelSizeLabel.setBounds(205, 189, 80, 14);
      super.getContentPane().add(pixelSizeLabel);

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
      super.getContentPane().add(pixelSizeField_);

      final JButton okButton = new JButton();
      okButton.setFont(new Font("", Font.PLAIN, 10));
      okButton.setText("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            settings.putString(OVERLAP_PREF, overlapField_.getText() );
            addToPositionList();
         }
      });
      okButton.setBounds(20, 216, 93, 23);
      super.getContentPane().add(okButton);

      final JButton cancelButton = new JButton();
      cancelButton.setBounds(129, 216, 93, 23);
      cancelButton.setFont(new Font("", Font.PLAIN, 10));
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            dispose();
         }
      });
      cancelButton.setText("Cancel");
      super.setDefaultCloseOperation(MMDialog.DISPOSE_ON_CLOSE);
      super.getContentPane().add(cancelButton);

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
      super.getContentPane().add(resetButton); 

      studio.events().registerForEvents(this);
   }

   @Override
   public void dispose(){
        studio_.profile().getSettings(TileCreatorDlg.class).putString(
                  OVERLAP_PREF,overlapField_.getText() );
        positionListDlg_.activateAxisTable(true);
        super.dispose();
   }
   
   @Subscribe
   public void shuttingDown(ShutdownCommencingEvent se) {
      studio_.profile().getSettings(TileCreatorDlg.class).putString(
                 OVERLAP_PREF, overlapField_.getText());
      dispose();
   }

   /**
    * Store current xyPosition.
    * Only store positions of drives selected in PositionList
    */
   private MultiStagePosition markPosition(int location) {
      MultiStagePosition msp = new MultiStagePosition();
      
      try {
         // read 1-axis stages
         final StrVector zStages = positionListDlg_.get1DAxes();
         if (zStages.size()>0) {
            msp.setDefaultZStage(zStages.get(0));
            for (int i=0; i<zStages.size(); i++){
                StagePosition sp = StagePosition.create1D(zStages.get(i), core_.getPosition(zStages.get(i)));
                msp.add(sp);
            }
         }

         // and 2 axis default stage
         final String xyStage = positionListDlg_.get2DAxis();
         if (xyStage != null) {
            msp.setDefaultXYStage(xyStage);
            StagePosition sp = StagePosition.create2D(xyStage, core_.getXPosition(xyStage), core_.getYPosition(xyStage));
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
       double pixelSizeUm = getPixelSizeUm();
      double imageSizeXUm = tileCreator_.getImageSize(pixelSizeUm)[0];
      double imageSizeYUm = tileCreator_.getImageSize(pixelSizeUm)[1];

      double overlap = getOverlap();
      double tileSizeXUm = tileCreator_.getTileSize(overlap, overlapUnit_, pixelSizeUm)[0];
      double tileSizeYUm = tileCreator_.getTileSize(overlap, overlapUnit_, pixelSizeUm)[1];

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
       double pixelSizeUm = getPixelSizeUm();
      double imageSizeXUm = tileCreator_.getImageSize(pixelSizeUm)[0];
      double imageSizeYUm = tileCreator_.getImageSize(pixelSizeUm)[1];

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
            final StrVector zStages = positionListDlg_.get1DAxes();
            if (zStages.size()>0){
                msp.setDefaultZStage(zStages.get(0));
                for (int i=0; i<zStages.size(); i++){
                    StagePosition sp = StagePosition.create1D(zStages.get(i), core_.getPosition(zStages.get(i)));
                    msp.add(sp);
                    sb.append(sp.getVerbose()).append("\n");
                }
            }

            // read 2-axis stages
            final String xyStage = positionListDlg_.get2DAxis();
            if (xyStage != null) {
               msp.setDefaultXYStage(xyStage);
               StagePosition sp = StagePosition.create2D(xyStage, core_.getXPosition(xyStage), core_.getYPosition(xyStage));

               switch (location) {
                  case 0: // top
                     sp.set2DPosition(xyStage,
                             core_.getXPosition(xyStage),
                             core_.getYPosition(xyStage) + offsetYUm);
                     break;
                  case 1: // right
                     sp.set2DPosition(xyStage,
                             core_.getXPosition(xyStage) + offsetXUm,
                             core_.getYPosition(xyStage));
                     break;
                  case 2: // bottom
                     sp.set2DPosition(xyStage,
                             core_.getXPosition(xyStage),
                             core_.getYPosition(xyStage) - offsetYUm);
                     break;
                  case 3: // left
                     sp.set2DPosition(xyStage,
                             core_.getXPosition(xyStage) - offsetXUm,
                             core_.getYPosition(xyStage));
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

    private double getPixelSizeUm() throws TileCreatorDlg.TileCreatorException {
        // check if we are calibrated, TODO: allow input of image size
        double pixSizeUm = 0.0;
        try {
           pixSizeUm = NumberUtils.displayStringToDouble(pixelSizeField_.getText());
        } catch (ParseException e) {
           ReportingUtils.logError(e);
        }
        if (pixSizeUm <= 0.0) {
           JOptionPane.showMessageDialog(this, "Pixel Size should be a value > 0 (usually 0.1 -1 um).  It should be experimentally determined. ");
           throw new TileCreatorDlg.TileCreatorException("Zero pixel size");
        }

        return pixSizeUm;
    }
    
    private double getOverlap(){
        try {
           double overlap = NumberUtils.displayStringToDouble(overlapField_.getText());
           return overlap;
        } catch (ParseException e) {
           ReportingUtils.logError(e, "Number Parse error in Tile Creator Dialog");
           return 0;
        }
    }

   /*
    * Create the tile list based on user input, pixelsize, and imagesize
    */
    private void addToPositionList() {
        // Sanity check: don't create any positions if there is no XY stage to
        // use.
        String xyStage = positionListDlg_.get2DAxis();
        if (xyStage == null) {
           return;
        }
        prefix_ += 1;
        double overlap = getOverlap();
        double pixelSizeUm;
        try{
            pixelSizeUm = getPixelSizeUm();
        } catch (TileCreatorDlg.TileCreatorException ex){
            ReportingUtils.showError(ex);
            return;
        }
        StrVector zStages = positionListDlg_.get1DAxes();
        PositionList endPoints =  new PositionList();
        for (int i=0; i<endPosition_.length; i++) {
            if (endPosition_[i] != null){ //We don't want to send null positions to the tile creator.
                endPoints.addPosition(endPosition_[i]);
            }
        }
        PositionList posList = tileCreator_.createTiles(overlap, overlapUnit_, endPoints.getPositions(), pixelSizeUm, Integer.toString(prefix_), xyStage, zStages, ZGenerator.Type.SHEPINTERPOLATE);
        // Add to position list
        // Increment prefix for these positions
        MultiStagePosition[] msps = posList.getPositions();
        for (int i=0; i<msps.length; i++) {
            positionListDlg_.addPosition(msps[i], msps[i].getLabel());
        }
        positionListDlg_.activateAxisTable(true);
        dispose();
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

   @Subscribe
   public void onPixelSizeChanged(PixelSizeChangedEvent event) {
      pixelSizeField_.setText(NumberUtils.doubleToDisplayString(
               event.getNewPixelSizeUm()));
      updateCenteredSizeLabel();
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

///////////////////////////////////////////////////////////////////////////////
//FILE:          Setup.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim.Data;

import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JSlider;
import javax.swing.event.ChangeListener;

import org.micromanager.asidispim.ASIdiSPIMFrame;
import org.micromanager.asidispim.Data.Properties.PropTypes;
import org.micromanager.asidispim.Utils.UpdateFromPropertyListenerInterface;



/**
 * Class containing data pertaining to SPIM setup/alignment
 * @author Jon
 */
public class Setup {
   
   // TODO figure out how to have 2 seperate instances of this class, or else move imagingStartPos_ etc. to SetupPanel where we have 2
   
   public double imagingStartPos_;
   public double imagingEndPos_;
   public double sheetStartPos_;
   public double sheetEndPos_;
   
   // list of strings used as keys in the Property class
   // initialized with corresponding property name in the constructor
   public static final String SHEET_ENABLED_A = "SheetEnabledA";
   public static final String SHEET_ENABLED_B = "SheetEnabledB";
   public static final String SHEET_AMPLITUDE = "SheetAmplitude";
   public static final String SHEET_AMPLITUDE_A = "SheetAmplitudeA";
   public static final String SHEET_AMPLITUDE_B = "SheetAmplitudeB";
   public static final String SHEET_OFFSET      = "SheetOffset";
   public static final String SHEET_OFFSET_A = "SheetOffsetA";
   public static final String SHEET_OFFSET_B = "SheetOffsetB";
   public static final String MAX_SHEET_VAL   = "MaxSheetVal";
   public static final String MAX_SHEET_VAL_A = "MaxSheetValA";
   public static final String MAX_SHEET_VAL_B = "MaxSheetValB";
   public static final String MIN_SHEET_VAL   = "MinSheetVal";
   public static final String MIN_SHEET_VAL_A = "MinSheetValA";
   public static final String MIN_SHEET_VAL_B = "MinSheetValB";
   public static final String SHEET_MOVE_AMPLITUDE = "SheetMoveAmplitude";
   public static final String SHEET_MOVE_AMPLITUDE_A = "SheetMoveAmplitudeA";
   public static final String SHEET_MOVE_AMPLITUDE_B = "SheetMoveAmplitudeB";
   public static final String SHEET_MOVE_OFFSET = "SheetMoveOffset";
   public static final String SHEET_MOVE_OFFSET_A = "SheetMoveOffsetA";
   public static final String SHEET_MOVE_OFFSET_B = "SheetMoveOffsetB";
   public static final String IMAGE_MOVE_AMPLITUDE = "ImageMoveAmplitude";
   public static final String IMAGE_MOVE_AMPLITUDE_A = "ImageMoveAmplitudeA";
   public static final String IMAGE_MOVE_AMPLITUDE_B = "ImageMoveAmplitudeB";
   public static final String IMAGE_MOVE_OFFSET = "ImageMoveOffset";
   public static final String IMAGE_MOVE_OFFSET_A = "ImageMoveOffsetA";
   public static final String IMAGE_MOVE_OFFSET_B = "ImageMoveOffsetB";
   
   private final List<UpdateFromPropertyListenerInterface> listeners_;
   
   public Setup() {
      listeners_ = new ArrayList<UpdateFromPropertyListenerInterface>();
      
      // initialize any property values
      ASIdiSPIMFrame.props_.addPropertyData(SHEET_ENABLED_A, "SingleAxisXMode", Devices.GALVOA, PropTypes.STRING);
      ASIdiSPIMFrame.props_.addPropertyData(SHEET_ENABLED_B, "SingleAxisXMode", Devices.GALVOB, PropTypes.STRING);
      ASIdiSPIMFrame.props_.addPropertyData(SHEET_AMPLITUDE_A, "SingleAxisXAmplitude(deg)", Devices.GALVOA, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(SHEET_AMPLITUDE_B, "SingleAxisXAmplitude(deg)", Devices.GALVOB, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(SHEET_OFFSET_A, "SingleAxisXOffset(deg)", Devices.GALVOA, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(SHEET_OFFSET_B, "SingleAxisXOffset(deg)", Devices.GALVOB, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(MAX_SHEET_VAL_A, "MaxDeflectionX(deg)", Devices.GALVOA, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(MAX_SHEET_VAL_B, "MaxDeflectionX(deg)", Devices.GALVOB, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(MIN_SHEET_VAL_A, "MinDeflectionX(deg)", Devices.GALVOA, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(MIN_SHEET_VAL_B, "MinDeflectionX(deg)", Devices.GALVOB, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(SHEET_MOVE_AMPLITUDE_A, "SingleAxisYAmplitude(deg)", Devices.GALVOA, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(SHEET_MOVE_AMPLITUDE_B, "SingleAxisYAmplitude(deg)", Devices.GALVOB, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(SHEET_MOVE_OFFSET_A, "SingleAxisYOffset(deg)", Devices.GALVOA, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(SHEET_MOVE_OFFSET_B, "SingleAxisYOffset(deg)", Devices.GALVOB, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(IMAGE_MOVE_AMPLITUDE_A, "SingleAxisAmplitude(um)", Devices.PIEZOA, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(IMAGE_MOVE_AMPLITUDE_B, "SingleAxisAmplitude(um)", Devices.PIEZOB, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(IMAGE_MOVE_OFFSET_A, "SingleAxisOffset(um)", Devices.PIEZOA, PropTypes.FLOAT);
      ASIdiSPIMFrame.props_.addPropertyData(IMAGE_MOVE_OFFSET_B, "SingleAxisOffset(um)", Devices.PIEZOB, PropTypes.FLOAT);
      
      // init the local variables we use for setting ROI extents
      // ideally we would get from controller, but we have to know which side we are on
      imagingStartPos_ = 0;
      imagingEndPos_ = 0;
      sheetStartPos_ = 0;
      sheetEndPos_ = 0;
   }
   
   /**
    * updates single-axis parameters for stepped light sheet (slow axis)
    * according to sheetStartPos_ and sheetEndPos_
    */
   public void updateSheetSAParams(String side) {
      ASIdiSPIMFrame.props_.setPropValue(SHEET_MOVE_AMPLITUDE+side, (float)(sheetEndPos_-sheetStartPos_));
      ASIdiSPIMFrame.props_.setPropValue(SHEET_MOVE_OFFSET+side, (float)(sheetEndPos_+sheetStartPos_)/2);
   }
   
   /**
    * updates single-axis parameters for stepped piezos
    * according to sheetStartPos_ and sheetEndPos_
    */
   public void updateImagingSAParams(String side) {
      ASIdiSPIMFrame.props_.setPropValue(IMAGE_MOVE_AMPLITUDE+side, (float)(imagingEndPos_-imagingStartPos_));
      ASIdiSPIMFrame.props_.setPropValue(IMAGE_MOVE_OFFSET+side, (float)(imagingEndPos_+imagingStartPos_)/2);
   }
   
   public void addListener(UpdateFromPropertyListenerInterface listener) {
      listeners_.add(listener);
   }
   
   public void addListener(JCheckBox jsp) {
      ItemListener[] cl = jsp.getItemListeners();
      for (int i=0; i<cl.length; i++) {
         try {
            // this will only work some of the time, for the listener we added
            // the rest of the calls will throw exceptions, which we catch and do nothing with
            listeners_.add((UpdateFromPropertyListenerInterface)cl[i]);
         } catch (Exception ex) {
            // do nothing here
         }
      }
   }
   
   public void addListener(JSlider jsp) {
      ChangeListener[] cl = jsp.getChangeListeners();
      for (int i=0; i<cl.length; i++) {
         try {
            // this will only work some of the time, for the listener we added
            // the rest of the calls will throw exceptions, which we catch and do nothing with
            listeners_.add((UpdateFromPropertyListenerInterface)cl[i]);
         } catch (Exception ex) {
            // do nothing here
         }
      }
   }

   public void removeListener(UpdateFromPropertyListenerInterface listener) {
      listeners_.remove(listener);
   }

   public void callListeners() {
      for (UpdateFromPropertyListenerInterface listener: listeners_) {
         listener.updateFromProperty();
      }
   }
   
}

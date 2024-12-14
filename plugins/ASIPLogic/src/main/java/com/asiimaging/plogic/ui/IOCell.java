/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui;

import com.asiimaging.plogic.PLogicControlModel;
import com.asiimaging.plogic.model.devices.ASIPLogic;
import com.asiimaging.plogic.ui.asigui.CheckBox;
import com.asiimaging.plogic.ui.asigui.ComboBox;
import com.asiimaging.plogic.ui.asigui.Panel;
import com.asiimaging.plogic.ui.asigui.Spinner;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JLabel;

public class IOCell extends Panel {

   public static boolean UPDATE;

   private static final String[] ioTypes = ASIPLogic.IOType.toArray();

   // source address
   private ComboBox cmbIOType_;
   private Spinner spnSourceAddr_;
   private CheckBox cbxInvert_;
   private CheckBox cbxEdge_;
   private JLabel lblValue_;

   private final int cellAddr_;
   private final String title_;

   private final PLogicControlModel model_;

   public IOCell(final PLogicControlModel model, final int cellNum) {
      model_ = Objects.requireNonNull(model);
      // I/O cell data
      cellAddr_ = cellNum + 32; // BNC1 starts at address 33
      if (cellNum <= 8) {
         title_ = "BNC" + cellNum;
      } else {
         title_ = "TTL" + (cellNum - 9);
      }
      createUserInterface();
      createEventHandlers();
   }

   private void createUserInterface() {
      setMigLayout(
            "",
            "[center]5[center]",
            "[]5[]"
      );

      // top left and right labels
      final JLabel lblCell = new JLabel(title_);
      final JLabel lblCellAddr = new JLabel("Addr " + cellAddr_);
      final Font font = new Font(Font.SANS_SERIF, Font.BOLD, 16);
      lblCell.setFont(font);
      lblCellAddr.setFont(font);

      cmbIOType_ = new ComboBox(ioTypes, ioTypes[0], 138, 22);

      // for outputs
      final JLabel lblInvert = new JLabel("Invert?");
      final JLabel lblEdge = new JLabel("Edge?");
      final JLabel lblValue = new JLabel("Value");

      spnSourceAddr_ = Spinner.createIntegerSpinner(0, 0, 63, 1);
      cbxInvert_ = new CheckBox(false);
      cbxEdge_ = new CheckBox(false);
      lblValue_ = new JLabel("255");

      setBorder(BorderFactory.createLineBorder(Color.GRAY));

      add(lblCell, "align left");
      add(lblCellAddr, "span 3, align right, wrap");
      add(new JLabel("I/O:"), "split 2");
      add(cmbIOType_, "span 2");
      add(lblInvert, "");
      add(lblEdge, "");
      add(lblValue, "wrap");
      add(new JLabel("Source Address:"), "align left, split 2");
      add(spnSourceAddr_, "gapleft 20");
      add(cbxInvert_, "");
      add(cbxEdge_, "");
      add(new JLabel("255"), "wrap");
   }

   private void createEventHandlers() {
      // change I/O type
      cmbIOType_.registerListener(e -> {
         final ASIPLogic.IOType ioType = ASIPLogic.IOType.fromString(cmbIOType_.getSelected());
         refreshUserInterface(ioType); // needed during update
         if (UPDATE) {
            model_.plc().pointerPosition(cellAddr_);
            model_.plc().ioType(ioType);
         }
      });

      // set source address
      spnSourceAddr_.registerListener(e -> {
         if (UPDATE) {
            setSourceAddress();
         }
      });
      cbxInvert_.registerListener(e -> {
         if (UPDATE) {
            setSourceAddress();
         }
      });
      cbxEdge_.registerListener(e -> {
         if (UPDATE) {
            setSourceAddress();
         }
      });
   }

   /**
    * Refresh the I/O cell based on the type of cell.
    *
    * @param ioType the type of cell
    */
   private void refreshUserInterface(final ASIPLogic.IOType ioType) {
      removeAll();

      final JLabel lblCell = new JLabel(title_);
      final JLabel lblCellAddr = new JLabel("Addr " + cellAddr_);
      final Font font = new Font(Font.SANS_SERIF, Font.BOLD, 16);
      lblCell.setFont(font);
      lblCellAddr.setFont(font);

      final JLabel lblInvert = new JLabel("Invert?");
      final JLabel lblEdge = new JLabel("Edge?");
      final JLabel lblValue = new JLabel("Value");

      if (ioType == ASIPLogic.IOType.INPUT) {
         add(lblCell, "align left");
         add(lblCellAddr, "gapleft 41, align right, wrap");
         add(new JLabel("I/O:"), "split 2");
         add(cmbIOType_, "");
      } else {
         add(lblCell, "align left");
         add(lblCellAddr, "span 3, align right, wrap");
         add(new JLabel("I/O:"), "split 2");
         add(cmbIOType_, "span 2");
         add(lblInvert, "");
         add(lblEdge, "");
         add(lblValue, "wrap");
         add(new JLabel("Source Address:"), "align left, split 2");
         add(spnSourceAddr_, "gapleft 20");
         add(cbxInvert_, "");
         add(cbxEdge_, "");
         add(lblValue_, "");
      }
      setMinimumSize(new Dimension(283, 95));
      updateUI();
   }

   /**
    * Send the new source address to the controller.
    */
   private void setSourceAddress() {
      final boolean isInverted = cbxInvert_.isSelected();
      final boolean isEdge = cbxEdge_.isSelected();
      final int value = spnSourceAddr_.getInt() + (isInverted ? 64 : 0) + (isEdge ? 128 : 0);
      model_.plc().pointerPosition(cellAddr_);
      model_.plc().sourceAddress(value);
      lblValue_.setText(String.valueOf(value));
   }

   /**
    * Remove all components from the cell.
    */
   public void clearCell() {
      removeAll();
      setMinimumSize(new Dimension(283, 95));
      updateUI();
   }

   /**
    * Set pointer position to cell; set UI to configuration and I/O type.
    */
   public void initCell() {
      //plc_.pointerPosition(cellAddr_);
      // I/O type
      //final ASIPLogic.IOType ioType = plc_.ioType();
      final ASIPLogic.IOType ioType = model_.plc().state().io(cellAddr_).type();
      cmbIOType_.setSelected(ioType.toString());
      // source address
      //final int sourceAddr = plc_.cellConfig();
      final int sourceAddr = model_.plc().state().io(cellAddr_).sourceAddress();
      int sourceAddrTemp = sourceAddr;
      if (sourceAddrTemp >= 128) {
         sourceAddrTemp -= 128;
         cbxEdge_.setSelected(true);
      } else {
         cbxEdge_.setSelected(false);
      }
      if (sourceAddrTemp >= 64) {
         sourceAddrTemp -= 64;
         cbxInvert_.setSelected(true);
      } else {
         cbxInvert_.setSelected(false);
      }
      spnSourceAddr_.setInt(sourceAddrTemp);
      lblValue_.setText(String.valueOf(sourceAddr));
   }
}

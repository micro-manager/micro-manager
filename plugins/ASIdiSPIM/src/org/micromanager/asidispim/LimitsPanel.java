///////////////////////////////////////////////////////////////////////////////
//FILE:          LimitsPanel.java
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

package org.micromanager.asidispim;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Joystick.Directions;
import org.micromanager.asidispim.Data.LimitsSpec;
import org.micromanager.asidispim.Data.LimitsTableModel;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;

import net.miginfocom.swing.MigLayout;

/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public class LimitsPanel extends ListeningJPanel {
   
   private final Prefs prefs_;
   private final Positions positions_;
   private final LimitsTableModel limitsTableModel_;
   
   
   /**
    * if table is disabled then cell will be set to disabled too
    */
   class MyTableCellRenderer extends DefaultTableCellRenderer {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value,
            boolean selected, boolean focused, int rowIndex, int columnIndex)
      {
         // https://stackoverflow.com/a/3055930
         if (value == null) {
            return null;
         }
         
         return super.getTableCellRendererComponent(table, value, selected, 
                 focused, rowIndex, columnIndex);
      }
   };
   
   class MyCheckboxCellRenderer extends JCheckBox implements TableCellRenderer {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int rowIndex, int columnIndex) {
         // https://stackoverflow.com/a/3055930
         if (value == null) {
            return null;
         }

         if (!(value instanceof Boolean)) {
            return null;
         }
         JCheckBox check = new JCheckBox("", (Boolean) value);
         check.setOpaque(true);
         if (isSelected) {
            check.setBackground(table.getSelectionBackground());
            check.setOpaque(true);
         } else {
            check.setOpaque(false);
            check.setBackground(table.getBackground());
         }
         check.setHorizontalAlignment(SwingConstants.CENTER);
         return check;
      }
   }
   
   
   /**
    * 
    * @param gui Micro-Manager api
    * @param devices the (single) instance of the Devices class
    * @param props Plugin-wide properties
    * @param prefs Plugin-wide preferences
    * @param stagePosUpdater Can query the controller for stage positions
    */
   public LimitsPanel(Prefs prefs, Positions positions) {
      super (MyStrings.PanelNames.LIMITS.toString(),
            new MigLayout(
              "", 
              "[right]10[center]",
              "[]0[]"));
     
      prefs_ = prefs;
      positions_ = positions;
      
      final JTable limitsTable;
      final JScrollPane limitsTablePane;
      
      limitsTableModel_ = new LimitsTableModel(prefs_, panelName_);
      limitsTable = new JTable(limitsTableModel_);

      // put table and buttons in its own miglayout
      final JPanel tablePanel = new JPanel();
      tablePanel.setLayout(new MigLayout (
            "flowy, ins 0",
            "",
            "") );
      limitsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      TableColumn column_use = limitsTable.getColumnModel().getColumn(LimitsTableModel.columnIndex_UseLimit);
      TableColumn column_xCoeff = limitsTable.getColumnModel().getColumn(LimitsTableModel.columnIndex_xCoeff);
      TableColumn column_yCoeff = limitsTable.getColumnModel().getColumn(LimitsTableModel.columnIndex_yCoeff);
      TableColumn column_zCoeff = limitsTable.getColumnModel().getColumn(LimitsTableModel.columnIndex_zCoeff);
      TableColumn column_Sum = limitsTable.getColumnModel().getColumn(LimitsTableModel.columnIndex_Sum);
      TableColumn column_Invert = limitsTable.getColumnModel().getColumn(LimitsTableModel.columnIndex_Invert);
      column_use.setPreferredWidth(35);
      column_xCoeff.setPreferredWidth(50);
      column_yCoeff.setPreferredWidth(50);
      column_zCoeff.setPreferredWidth(50);
      column_Sum.setPreferredWidth(80);
      column_Invert.setPreferredWidth(45);
      MyTableCellRenderer centerRenderer = new MyTableCellRenderer();
      MyCheckboxCellRenderer checkboxRenderer = new MyCheckboxCellRenderer();
      centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
      column_use.setCellRenderer(checkboxRenderer);
      column_xCoeff.setCellRenderer(centerRenderer);
      column_yCoeff.setCellRenderer(centerRenderer);
      column_zCoeff.setCellRenderer(centerRenderer);
      column_Sum.setCellRenderer(centerRenderer);
      column_Invert.setCellRenderer(checkboxRenderer);

      limitsTablePane = new JScrollPane(limitsTable);
      limitsTablePane.setPreferredSize(new Dimension(350,100));
      limitsTablePane.setViewportView(limitsTable);
      tablePanel.add(limitsTablePane, "wrap");

      Dimension buttonSize = new Dimension(30, 20);
      JButton plusButton = new JButton("+");
      plusButton.setMargin(new Insets(0,0,0,0));
      plusButton.setPreferredSize(buttonSize);
      plusButton.setMaximumSize(buttonSize);
      plusButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            limitsTableModel_.addNewEmptyLimit();
            limitsTableModel_.fireTableDataChanged();
         }
      }
            );
      tablePanel.add(plusButton, "growx, aligny top, split 2");

      JButton minusButton = new JButton("-");
      minusButton.setMargin(new Insets(0,0,0,0));
      minusButton.setPreferredSize(buttonSize);
      minusButton.setMaximumSize(buttonSize);
      minusButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int selectedRows[] = limitsTable.getSelectedRows();
            for (int row : selectedRows) {
               limitsTableModel_.removeLimit(row);
            }
            if (selectedRows.length > 0) {
               limitsTableModel_.fireTableDataChanged();
            }
         }
      }
            );
      tablePanel.add(minusButton, "wrap");

      this.add(tablePanel, "span 2, align left, wrap");

   }

   
   @Override
   public void windowClosing() {
   }
   
   @Override
   public final void updateStagePositions() {
      double xPos = positions_.getCachedPosition(Devices.Keys.XYSTAGE, Directions.X);
      double yPos = positions_.getCachedPosition(Devices.Keys.XYSTAGE, Directions.Y);
      double zPos = positions_.getCachedPosition(Devices.Keys.UPPERZDRIVE, Directions.NONE);
      for (LimitsSpec l: limitsTableModel_.getUsedLimits()) {
         double sum = xPos*l.xCoeff_ + yPos*l.yCoeff_ + zPos*l.zCoeff_;
         if ((sum > l.sum_) ^ l.invert_) {
            ASIdiSPIM.getFrame().getNavigationPanel().haltAllMotion();
            MyDialogUtils.showError("XYZ limit exceeded and motion halted.  \n Violated rule with xCoeff=" + l.xCoeff_ +
                  ", yCoeff=" + l.yCoeff_ + ", zCoeff=" + l.zCoeff_ + ", sum=" + l.sum_ + ", invert=" + l.invert_);
         }
      }
   }
   
   
}

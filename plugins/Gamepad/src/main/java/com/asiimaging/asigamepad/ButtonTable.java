///////////////////////////////////////////////////////////////////////////////
//FILE:          ButtonTable.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     asi gamepad plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Vikram Kopuri
//
// COPYRIGHT:    Applied Scientific Instrumentation (ASI), 2018
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


package com.asiimaging.asigamepad;

import com.ivan.xinput.enums.XInputButton;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import org.micromanager.Studio;
import org.micromanager.propertymap.MutablePropertyMapView;

//A good table tutorial can be found at https://docs.oracle.com/javase/tutorial/uiswing/components/table.html


/**
 * Button assignment table class
 *
 * @author Vikram Kopuri for ASI
 */

public class ButtonTable extends JPanel implements TableModelListener {

   private static final long serialVersionUID = 5334839209473245807L;
   private Studio studio_;
   private MutablePropertyMapView settings_;
   private DefaultTableModel model_;
   public JTable table;

   public ButtonTable(Studio studio) {
      super(new GridLayout(1, 0));
      studio_ = studio;

      String[] columnNames = {"Button",
            "Action",
            "Beanshell Script"};

      Object[][] data = new Object[XInputButton.values().length][columnNames.length];

      int idx = 0;

      settings_ = studio_.profile().getSettings(this.getClass());

      for (XInputButton button : XInputButton.values()) {
         data[idx][0] = button;
         ButtonActions.ActionItems actionItem = settings_.getStringAsEnum(
               button.name() + "enum",
               ButtonActions.ActionItems.class, ButtonActions.ActionItems.Undefined);
         data[idx][1] = actionItem;
         String scriptPath = settings_.getString(button.name(), "");
         data[idx][2] = scriptPath;
         idx++;
      }

      model_ = new DefaultTableModel(data, columnNames) {
         private static final long serialVersionUID = 1L;

         @Override
         public boolean isCellEditable(int row, int column) {
            //First column is readonly
            return column != 0;
         }
      };

      table = new JTable(model_);

      table.setPreferredScrollableViewportSize(
            new Dimension(300, XInputButton.values().length * table.getRowHeight()));
      table.setFillsViewportHeight(true);

      //Create the scroll pane and add the table to it.
      JScrollPane scrollPane = new JScrollPane(table);

      setupCellEditors();
      model_.addTableModelListener(this);

      // Add the scroll pane to this panel.
      add(scrollPane);

   } // end of constructor

   public void setupCellEditors() {
      // create and assign celleditor for action column.
      setupActionColumn(table.getColumnModel().getColumn(1));

      setupScriptCol(table.getColumnModel().getColumn(2));
   }

   /**
    * setup a combobox with Button actions enum as 2nd column's cell editor
    *
    * @param deviceColumn column that will get the new custom editor
    */
   private void setupActionColumn(TableColumn deviceColumn) {
      JComboBox<ButtonActions.ActionItems> comboBox = new JComboBox<>();

      for (ButtonActions.ActionItems ai : ButtonActions.ActionItems.values()) {
         comboBox.addItem(ai);
      }
      deviceColumn.setCellEditor(new DefaultCellEditor(comboBox));

      // Set up tool tips for the sport cells.
      DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
      renderer.setToolTipText("Assign actions to buttons");
      deviceColumn.setCellRenderer(renderer);
   }

   /**
    * Set up the editor for the Beanshell script column.
    *
    * @param deviceColumn column that will get the new custom editor
    */
   private void setupScriptCol(TableColumn deviceColumn) {
      deviceColumn.setCellEditor(new FileCellEditor());
   }


   /**
    * This fine grain notification tells listeners the exact range
    * of cells, rows, or columns that changed.
    *
    * @param e
    */
   @Override
   public void tableChanged(TableModelEvent e) {
      int col = e.getColumn();
      int row = e.getFirstRow();
      if (row > -1 && row < model_.getRowCount()) {
         String key = ((XInputButton) model_.getValueAt(row, 0)).name();

         if (col == 1) {
            settings_.putEnumAsString(key + "enum",
                  (ButtonActions.ActionItems) model_.getValueAt(row, 1));
            if (!model_.getValueAt(row, 1).equals(ButtonActions.ActionItems.Run_Beanshell_script)) {
               model_.removeTableModelListener(this);
               model_.setValueAt("", row, 2);
               model_.addTableModelListener(this);
            }
         } else if (col == 2) {
            settings_.putString(key, (String) model_.getValueAt(row, 2));
         }
      } else if (row == -1 && col == -1) {
         // save the complete table
         for (int r = 0; r < model_.getRowCount(); r++) {
            String key = ((XInputButton) model_.getValueAt(r, 0)).name();
            settings_.putEnumAsString(key + "enum",
                  (ButtonActions.ActionItems) model_.getValueAt(r, 1));
            settings_.putString(key, (String) model_.getValueAt(r, 2));
         }
      }

   }
} //end of class

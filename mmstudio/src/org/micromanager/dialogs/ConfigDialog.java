///////////////////////////////////////////////////////////////////////////////
//FILE:          ConfigDialog.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 20, 2006
//               Modified June 12, 2009 by Arthur Edelstein
//
// COPYRIGHT:    University of California, San Francisco, 2006-2009
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
// CVS:          $Id: PresetEditor.java 2693 2009-07-02 17:22:39Z arthur $

package org.micromanager.dialogs;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.table.TableColumn;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.*;

/*
 * A base class from which GroupEditor and PresetEditor are derived.
 */
public class ConfigDialog extends MMDialog {

   private static final long serialVersionUID = 5819669941239786807L;

   protected CMMCore core_;
   protected ScriptInterface gui_;

   private JTable table_;
   protected PropertyTableData data_;
   private JScrollPane scrollPane_;

   private ShowFlags flags_;
   private ShowFlagsPanel showFlagsPanel_;

   private final SpringLayout springLayout_;
   private JTextArea textArea_;
   private JCheckBox showReadonlyCheckBox_;
   protected boolean showShowReadonlyCheckBox_ = false;
   protected JTextField nameField_;
   private JLabel nameFieldLabel_;

   private JButton okButton_;
   private JButton cancelButton_;

   protected String TITLE = "";
   protected String instructionsText_ = "Instructions go here.";
   protected String nameFieldLabelText_ = "GroupOrPreset Name";
   protected String initName_ = "";

   protected String groupName_;
   protected String presetName_;

   protected Boolean showUnused_ = true;
   protected int numColumns_ = 3;

   protected boolean newItem_ = true;
   protected boolean showFlagsPanelVisible = true;

   protected int scrollPaneTop_;

   public ConfigDialog(String groupName, String presetName, ScriptInterface gui, CMMCore core, boolean newItem) {
      super();
      groupName_ = groupName;
      presetName_ = presetName;
      newItem_ = newItem;
      gui_ = gui;
      core_ = core;
      springLayout_ = new SpringLayout();
      getContentPane().setLayout(springLayout_);
      loadAndRestorePosition(100, 100, 550, 600);
         
      setResizable(false);
   }

   public void initialize() {
      initializePropertyTable();
      initializeWidgets();
      initializeFlags();
      setupKeys();
      setVisible(true);
      this.setTitle(TITLE);
      nameField_.requestFocus();
      setFocusable(true);
      update();
   }
   
   
   /*
    * Assign ENTER and ESCAPE keystrokes to be equivalent to OK and Cancel buttons, respectively.
    */
   @SuppressWarnings("serial")
   protected void setupKeys() {
      // Get the InputMap and ActionMap of the RootPane of this JDialog.
      JRootPane rootPane = this.getRootPane();
      InputMap inputMap = rootPane.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW);
      ActionMap actionMap = rootPane.getActionMap();
      
      // Setup ENTER key.
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter");
      actionMap.put("enter", new AbstractAction()  {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            if (table_.isEditing() && table_.getCellEditor() != null)
               table_.getCellEditor().stopCellEditing();
            okChosen();
         }
      });

      // Setup ESCAPE key.
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");
      actionMap.put("escape", new AbstractAction() {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            cancelChosen();
         }
      });

      // Stop table_ from consuming the ENTER keystroke and preventing it from being received by this (JDialog).
      table_.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
   }

   public void initializeData() {
      data_.setGUI(gui_);
      data_.setShowUnused(showUnused_);
   }

   protected void initializeWidgets() {
      textArea_ = new JTextArea();
      textArea_.setFont(new Font("Arial", Font.PLAIN, 12));
      textArea_.setWrapStyleWord(true);
      textArea_.setText(instructionsText_);
      textArea_.setEditable(false);
      textArea_.setOpaque(false);
      getContentPane().add(textArea_);
      springLayout_.putConstraint(SpringLayout.EAST, textArea_, 250, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, textArea_, 5, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, textArea_, 37, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, textArea_, 5, SpringLayout.NORTH, getContentPane());

      if (showShowReadonlyCheckBox_) {
         showReadonlyCheckBox_ = new JCheckBox();
         showReadonlyCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
         showReadonlyCheckBox_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               // show/hide read-only properties
               data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
               data_.update(false);
               data_.fireTableStructureChanged();
             }
         });
         showReadonlyCheckBox_.setText("Show read-only properties");
         getContentPane().add(showReadonlyCheckBox_);
         springLayout_.putConstraint(SpringLayout.EAST, showReadonlyCheckBox_, 250, SpringLayout.WEST, getContentPane());
         springLayout_.putConstraint(SpringLayout.WEST, showReadonlyCheckBox_, 5, SpringLayout.WEST, getContentPane());
         springLayout_.putConstraint(SpringLayout.NORTH, showReadonlyCheckBox_, 45, SpringLayout.NORTH, getContentPane());
         springLayout_.putConstraint(SpringLayout.SOUTH, showReadonlyCheckBox_, 70, SpringLayout.NORTH, getContentPane());
      }

      nameField_ = new JTextField();
      nameField_.setText(initName_);
      nameField_.setEditable(true);
      nameField_.setSelectionStart(0);
      nameField_.setSelectionEnd(nameField_.getText().length());
      getContentPane().add(nameField_);
      springLayout_.putConstraint(SpringLayout.EAST, nameField_, 280, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, nameField_, 95, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, nameField_, -3, SpringLayout.NORTH, scrollPane_);
      springLayout_.putConstraint(SpringLayout.NORTH, nameField_, -30, SpringLayout.NORTH, scrollPane_);

      nameFieldLabel_ = new JLabel();
      nameFieldLabel_.setText(nameFieldLabelText_);
      nameFieldLabel_.setFont(new Font("Arial",Font.BOLD,12));
      nameFieldLabel_.setHorizontalAlignment(SwingConstants.RIGHT);
      getContentPane().add(nameFieldLabel_);
      springLayout_.putConstraint(SpringLayout.EAST, nameFieldLabel_, 90, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, nameFieldLabel_, 5, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, nameFieldLabel_, -3, SpringLayout.NORTH, scrollPane_);
      springLayout_.putConstraint(SpringLayout.NORTH, nameFieldLabel_, -30, SpringLayout.NORTH, scrollPane_);

      okButton_ = new JButton("OK");
      getContentPane().add(okButton_);
      springLayout_.putConstraint(SpringLayout.EAST, okButton_, -5, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, okButton_, -105, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, okButton_, 30, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, okButton_, 5, SpringLayout.NORTH, getContentPane());
      okButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (table_.isEditing() && table_.getCellEditor() != null)
               table_.getCellEditor().stopCellEditing();
            okChosen();
         }
      });


      cancelButton_ = new JButton("Cancel");
      getContentPane().add(cancelButton_);
      springLayout_.putConstraint(SpringLayout.EAST, cancelButton_, -5, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, cancelButton_, -105, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, cancelButton_, 57, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, cancelButton_, 32, SpringLayout.NORTH, getContentPane());
      cancelButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            cancelChosen();
         }
      });
   }

   protected void initializeFlags() {
      flags_ = new ShowFlags();

      if (showFlagsPanelVisible ) {
         flags_.load(getPrefsNode());
         Configuration cfg;
         try {
            if (presetName_.length()==0)
               cfg = new Configuration();
            else
               cfg = core_.getConfigState(groupName_, presetName_);
            showFlagsPanel_ = new ShowFlagsPanel(data_,flags_,core_,cfg);
         } catch (Exception e) {
            ReportingUtils.showError(e);
         }
         getContentPane().add(showFlagsPanel_);
         springLayout_.putConstraint(SpringLayout.EAST, showFlagsPanel_, 440, SpringLayout.WEST, getContentPane());
         springLayout_.putConstraint(SpringLayout.WEST, showFlagsPanel_, 290, SpringLayout.WEST, getContentPane());
         springLayout_.putConstraint(SpringLayout.SOUTH, showFlagsPanel_, 135, SpringLayout.NORTH, getContentPane());
         springLayout_.putConstraint(SpringLayout.NORTH, showFlagsPanel_, 5, SpringLayout.NORTH, getContentPane());
      }

      data_.setFlags(flags_);
   }

   public void initializePropertyTable() {
        scrollPane_ = new JScrollPane();
        scrollPane_.setFont(new Font("Arial", Font.PLAIN, 10));
        scrollPane_.setBorder(new BevelBorder(BevelBorder.LOWERED));
        getContentPane().add(scrollPane_);
        springLayout_.putConstraint(SpringLayout.SOUTH, scrollPane_, -5, SpringLayout.SOUTH, getContentPane());
        springLayout_.putConstraint(SpringLayout.NORTH, scrollPane_, scrollPaneTop_, SpringLayout.NORTH, getContentPane());
        springLayout_.putConstraint(SpringLayout.EAST, scrollPane_, -5, SpringLayout.EAST, getContentPane());
        springLayout_.putConstraint(SpringLayout.WEST, scrollPane_, 5, SpringLayout.WEST, getContentPane());

        table_ = new JTable();
        table_.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table_.setAutoCreateColumnsFromModel(false);
        scrollPane_.setViewportView(table_);
        table_.setModel(data_);

        if (numColumns_ == 3) {
            table_.addColumn(new TableColumn(0, 200, new PropertyNameCellRenderer(), null));
            table_.addColumn(new TableColumn(1, 75, new PropertyUsageCellRenderer(), new PropertyUsageCellEditor()));
            table_.addColumn(new TableColumn(2, 200, new PropertyValueCellRenderer(true), new PropertyValueCellEditor(true)));
        } else if (numColumns_ == 2) {
            table_.addColumn(new TableColumn(0, 200, new PropertyNameCellRenderer(), null));
            table_.addColumn(new TableColumn(1, 200, new PropertyValueCellRenderer(false), new PropertyValueCellEditor(false)));
        }
 
   }

   public void okChosen() {
   }

   public void cancelChosen() {
      this.dispose();
   }

   @Override
   public void dispose() {
      super.dispose();
      savePosition();
      gui_.refreshGUI();
   }

   public void update() {
      data_.update(false);
   }

   
   public void showMessageDialog(String message) {
      JOptionPane.showMessageDialog(this, message);
   }

}

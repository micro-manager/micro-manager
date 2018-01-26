///////////////////////////////////////////////////////////////////////////////
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

package org.micromanager.internal.dialogs;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.border.BevelBorder;
import javax.swing.table.TableColumn;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.MMDialog;
import org.micromanager.internal.utils.PropertyNameCellRenderer;
import org.micromanager.internal.utils.PropertyTableData;
import org.micromanager.internal.utils.PropertyUsageCellEditor;
import org.micromanager.internal.utils.PropertyUsageCellRenderer;
import org.micromanager.internal.utils.PropertyValueCellEditor;
import org.micromanager.internal.utils.PropertyValueCellRenderer;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.ShowFlags;
import org.micromanager.internal.utils.ShowFlagsPanel;

/*
 * A base class from which GroupEditor and PresetEditor are derived.
 */
public abstract class ConfigDialog extends MMDialog {

   private static final long serialVersionUID = 5819669941239786807L;

   protected CMMCore core_;
   protected Studio gui_;

   private JTable table_;
   protected PropertyTableData data_;
   private JScrollPane scrollPane_;

   private ShowFlags flags_;
   private ShowFlagsPanel showFlagsPanel_;

   private JTextArea instructionsTextArea_;
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
   protected boolean showFlagsPanelVisible_ = true;

   protected int scrollPaneTop_;

   public ConfigDialog(String groupName, String presetName, Studio gui, CMMCore core, boolean newItem) {
      super("config editing for " + groupName);
      groupName_ = groupName;
      presetName_ = presetName;
      newItem_ = newItem;
      gui_ = gui;
      core_ = core;
      super.setLayout(new MigLayout("fill, insets 2, gap 2"));
      super.loadAndRestorePosition(100, 100, 550, 600);
      super.setMinimumSize(new Dimension(400, 200));
   }

   public void initialize() {
      flags_ = new ShowFlags(gui_);
      data_.setFlags(flags_);

      initializeWidgets();
      initializePropertyTable();
      setupKeys();
      setVisible(true);
      this.setTitle(TITLE);
      nameField_.requestFocus();
      setFocusable(true);
      update();
   }


   /**
    * Assign ENTER and ESCAPE keystrokes to be equivalent to OK and Cancel
    * buttons, respectively.
    */
   @SuppressWarnings("serial")
   protected void setupKeys() {
      // Get the InputMap and ActionMap of the RootPane of this JDialog.
      InputMap inputMap = getRootPane().getInputMap(
            JComponent.WHEN_IN_FOCUSED_WINDOW);
      ActionMap actionMap = getRootPane().getActionMap();

      // Setup ENTER key.
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter");
      actionMap.put("enter", new AbstractAction() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (table_.isEditing() && table_.getCellEditor() != null) {
               table_.getCellEditor().stopCellEditing();
            }
            okChosen();
         }
      });

      // Setup ESCAPE key.
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");
      actionMap.put("escape", new AbstractAction() {
         @Override
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });

      // Stop table_ from consuming the ENTER keystroke and preventing it from
      // being received by this (JDialog).
      table_.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
   }

   public void initializeData() {
      data_.setGUI(gui_);
      data_.setShowUnused(showUnused_);
   }

   protected void initializeWidgets() {
      JPanel leftPanel = new JPanel(
            new MigLayout("filly, flowy, insets 0 6 0 0, gap 2"));
      instructionsTextArea_ = new JTextArea();
      instructionsTextArea_.setFont(new Font("Arial", Font.PLAIN, 12));
      instructionsTextArea_.setWrapStyleWord(true);
      instructionsTextArea_.setText(instructionsText_);
      instructionsTextArea_.setEditable(false);
      instructionsTextArea_.setOpaque(false);
      leftPanel.add(instructionsTextArea_, "gaptop 2, gapbottom push");

      if (showShowReadonlyCheckBox_) {
         showReadonlyCheckBox_ = new JCheckBox("Show read-only properties");
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
         leftPanel.add(showReadonlyCheckBox_, "gaptop 5, gapbottom 10");
      }

      nameFieldLabel_ = new JLabel(nameFieldLabelText_);
      nameFieldLabel_.setFont(new Font("Arial", Font.BOLD, 12));
      leftPanel.add(nameFieldLabel_, "split 2, flowx, alignx right");

      nameField_ = new JTextField();
      nameField_.setText(initName_);
      nameField_.setEditable(true);
      nameField_.setSelectionStart(0);
      nameField_.setSelectionEnd(nameField_.getText().length());
      leftPanel.add(nameField_, "width 180!");

      add(leftPanel, "growy, gapright push");

      if (showFlagsPanelVisible_ ) {
         flags_.load(ConfigDialog.class);
         Configuration cfg;
         try {
            if (presetName_.length() == 0) {
               cfg = new Configuration();
            }
            else {
               cfg = core_.getConfigState(groupName_, presetName_);
            }
            showFlagsPanel_ = new ShowFlagsPanel(data_, flags_, core_, cfg);
         } catch (Exception e) {
            ReportingUtils.showError(e);
         }
         add(showFlagsPanel_, "growx");
      }

      okButton_ = new JButton("OK");
      okButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (table_.isEditing() && table_.getCellEditor() != null) {
               table_.getCellEditor().stopCellEditing();
            }
            okChosen();
         }
      });
      add(okButton_, "gapleft push, split 2, flowy, width 90!");

      cancelButton_ = new JButton("Cancel");
      cancelButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });
      add(cancelButton_, "gapleft push, gapbottom push, wrap, width 90!");
   }

   public void initializePropertyTable() {
        scrollPane_ = new JScrollPane();
        scrollPane_.setFont(new Font("Arial", Font.PLAIN, 10));
        scrollPane_.setBorder(new BevelBorder(BevelBorder.LOWERED));
        add(scrollPane_, "span, growx, growy, pushy, wrap");

        table_ = new DaytimeNighttime.Table();
        table_.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table_.setAutoCreateColumnsFromModel(false);
        scrollPane_.setViewportView(table_);
        table_.setModel(data_);

        if (numColumns_ == 3) {
            table_.addColumn(new TableColumn(0, 200, 
                    new PropertyNameCellRenderer(), null));
            table_.addColumn(new TableColumn(1, 75, 
                    new PropertyUsageCellRenderer(), new PropertyUsageCellEditor()));
            table_.addColumn(new TableColumn(2, 200, 
                    new PropertyValueCellRenderer(true), new PropertyValueCellEditor(true)));
        } else if (numColumns_ == 2) {
            table_.addColumn(new TableColumn(0, 200, new PropertyNameCellRenderer(), null));
            table_.addColumn(new TableColumn(1, 200, new PropertyValueCellRenderer(false), new PropertyValueCellEditor(false)));
        }

   }

   public abstract void okChosen();

   @Override
   public void dispose() {
      super.dispose();
      savePosition();
      gui_.app().refreshGUI();
   }

   public void update() {
      data_.update(false);
   }


   public void showMessageDialog(String message) {
      JOptionPane.showMessageDialog(this, message);
   }

}

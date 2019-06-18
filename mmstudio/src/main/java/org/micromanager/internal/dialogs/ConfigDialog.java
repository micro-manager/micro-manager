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

import com.google.common.eventbus.Subscribe;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
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
import org.micromanager.events.PropertiesChangedEvent;
import org.micromanager.events.PropertyChangedEvent;
import org.micromanager.events.ShutdownCommencingEvent;
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
   protected Studio studio_;

   protected JTable table_;
   protected PropertyTableData data_;
   private JScrollPane scrollPane_;

   protected ShowFlags flags_;
   protected ShowFlagsPanel showFlagsPanel_;

   protected JTextArea instructionsTextArea_;
   protected JTextField nameField_;
   protected JLabel nameFieldLabel_;

   protected JButton okButton_;
   protected JButton cancelButton_;

   protected String title_ = "";
   protected String instructionsText_ = "Instructions go here.";
   protected String nameFieldLabelText_ = "GroupOrPreset Name";
   protected String initName_ = "";

   // following only used when showPixelSize_ is true
   protected boolean showPixelSize_ = false;
   protected String pixelSizeFieldLabelText_ = "Pixel Size (um):";
   protected JTextField pixelSizeField_;
   protected String pixelSize_;

   protected String groupName_;
   protected String presetName_;

   protected Boolean showUnused_ = true;
   protected int numColumns_ = 3;
   protected int numRowsBeforeFilters_ = 1;

   protected boolean newItem_ = true;
   protected boolean showFlagsPanelVisible_ = true;

   protected int scrollPaneTop_;

   public ConfigDialog(String groupName, String presetName, Studio studio, 
           CMMCore core,  boolean newItem) {
      super("config editing for " + groupName);
      groupName_ = groupName;
      presetName_ = presetName;
      newItem_ = newItem;
      studio_ = studio;
      core_ = core;
      super.setLayout(new MigLayout("fill, insets 2, gap 2, flowy"));
      // call loadAndRestorePosition and setMinimumSize from concrete subclasses
   }

   public void initialize() {
      flags_ = new ShowFlags(studio_);
      data_.setFlags(flags_);

      initializeWidgets();
      initializeBetweenWidgetsAndTable();
      initializePropertyTable();
      setupKeys();
      setVisible(true);
      this.setTitle(title_);
      nameField_.requestFocus();
      setFocusable(true);
      update();
      studio_.events().registerForEvents(this);
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
      data_.setGUI(studio_);
      data_.setShowUnused(showUnused_);
   }

   /**
    * Override this method to insert any GUI elements between
    * initializeWidgets() and initializePropertyTable().
    * Used for affine transform in pixel editors.
    */
   protected void initializeBetweenWidgetsAndTable() {
      return;
   }

   protected void initializeWidgets() {
      
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
            add(showFlagsPanel_, "skip " + numRowsBeforeFilters_ + ", aligny top, span 1 3, wrap");
         } catch (Exception e) {
            ReportingUtils.showError(e);
         }
      }

      JPanel topMidPanel = new JPanel(
            new MigLayout("flowx, insets 0 6 0 0, gap 2"));

      instructionsTextArea_ = new JTextArea();
      instructionsTextArea_.setFont(new Font("Arial", Font.PLAIN, 12));
      instructionsTextArea_.setWrapStyleWord(true);
      instructionsTextArea_.setText(instructionsText_);
      instructionsTextArea_.setEditable(false);
      instructionsTextArea_.setOpaque(false);
      topMidPanel.add(instructionsTextArea_, "gaptop 2, wrap");

      nameFieldLabel_ = new JLabel(nameFieldLabelText_);
      nameFieldLabel_.setFont(new Font("Arial", Font.BOLD, 12));
      topMidPanel.add(nameFieldLabel_, "split 2, alignx right");

      nameField_ = new JTextField();
      nameField_.setFont(new Font("Arial", Font.PLAIN, 12));
      // nameField_.setFont(new Font("Arial", Font.BOLD, 16));  // should consider increasing font size for entry field for readability, but would want to do it in multiple places for consistency
      nameField_.setText(initName_);
      nameField_.setEditable(true);
      nameField_.setSelectionStart(0);
      nameField_.setSelectionEnd(nameField_.getText().length());
      int fieldWidth = showPixelSize_ ? 90 : 180;
      topMidPanel.add(nameField_, "width " + fieldWidth + "!, gaptop 2, wrap");

      if (showPixelSize_) {
         JLabel pixelSizeFieldLabel = new JLabel(pixelSizeFieldLabelText_);
         pixelSizeFieldLabel.setFont(new Font("Arial", Font.BOLD, 12));
         topMidPanel.add(pixelSizeFieldLabel, "split 2, alignx right");

         pixelSizeField_ = new JTextField();
         pixelSizeField_.setFont(new Font("Arial", Font.PLAIN, 12));
         pixelSizeField_.setText(pixelSize_);
         pixelSizeField_.setEditable(true);
         pixelSizeField_.setSelectionStart(0);
         pixelSizeField_.setSelectionEnd(pixelSizeField_.getText().length());
         topMidPanel.add(pixelSizeField_, "width " + fieldWidth + "!, gaptop 2, wrap");
      }

      JPanel topRightPanel = new JPanel(
            new MigLayout("flowy, insets 0 6 0 0, gap 2"));

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
      topRightPanel.add(okButton_, "gapleft push, split 2, width 90!");

      cancelButton_ = new JButton("Cancel");
      cancelButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });
      topRightPanel.add(cancelButton_, "width 90!");

      add(topMidPanel, "flowx, split 2");
      add(topRightPanel, "gapleft push, flowx");
   }

   public void initializePropertyTable() {
        scrollPane_ = new JScrollPane();
        scrollPane_.setFont(new Font("Arial", Font.PLAIN, 10));
        scrollPane_.setBorder(new BevelBorder(BevelBorder.LOWERED));
        int extraWidth = scrollPane_.getVerticalScrollBar().getPreferredSize().width;
        add(scrollPane_, "flowy, span, growx, growy, push, width pref+" + extraWidth + "px");

        table_ = new DaytimeNighttime.Table();
        table_.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table_.setAutoCreateColumnsFromModel(false);
        scrollPane_.setViewportView(table_);
        table_.setModel(data_);

        if (numColumns_ == 3) {
            table_.addColumn(new TableColumn(0, 200, 
                    new PropertyNameCellRenderer(studio_), null));
            table_.addColumn(new TableColumn(1, 75, 
                    new PropertyUsageCellRenderer(studio_), new PropertyUsageCellEditor()));
            table_.addColumn(new TableColumn(2, 200, 
                    new PropertyValueCellRenderer(studio_), new PropertyValueCellEditor(true)));
        } else if (numColumns_ == 2) {
            table_.addColumn(new TableColumn(0, 200, 
                    new PropertyNameCellRenderer(studio_), null));
            table_.addColumn(new TableColumn(1, 200, 
                    new PropertyValueCellRenderer(studio_), new PropertyValueCellEditor(false)));
        }

   }

   public abstract void okChosen();

   @Override
   public void dispose() {
      studio_.events().unregisterForEvents(this);
      super.dispose();
      savePosition();
      studio_.app().refreshGUI();
   }
   
      
   /**
    * @param event indicating that shutdown is happening
    */
   @Subscribe
   public void onShutdownCommencing(ShutdownCommencingEvent event) {
      this.dispose();
   }
   

   public void update() {
      data_.update(false);
   }


   public void showMessageDialog(String message) {
      JOptionPane.showMessageDialog(this, message);
   }
   
   
   @Subscribe
   public void onPropertiesChanged(PropertiesChangedEvent event) {
      // avoid re-executing a refresh because of callbacks while we are
      // updating
      if (!data_.updating()) {
         data_.update(true);
      }
   }

   @Subscribe
   public void onPropertyChanged(PropertyChangedEvent event) {
      // avoid re-executing a refresh because of callbacks while we are
      // updating
      if (!data_.updating()) {
        data_.update(true);
      }
   }

}

///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 20, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006, 2014
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

package org.micromanager.internal;

/**
 * @author Nenad Amodaj
 * PropertyEditor provides UI for manipulating sets of device properties
 */

import com.google.common.eventbus.Subscribe;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.BevelBorder;
import javax.swing.table.TableColumn;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.events.PropertiesChangedEvent;
import org.micromanager.events.PropertyChangedEvent;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.PropertyItem;
import org.micromanager.internal.utils.PropertyNameCellRenderer;
import org.micromanager.internal.utils.PropertyTableData;
import org.micromanager.internal.utils.PropertyValueCellEditor;
import org.micromanager.internal.utils.PropertyValueCellRenderer;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.ShowFlags;
import org.micromanager.internal.utils.ShowFlagsPanel;

/**
 * JFrame based component for generic manipulation of device properties.
 * Represents the entire system state as a list of triplets:
 * device - property - value
 *
 * aka the "Device/Property Browser"
 */
public final class PropertyEditor extends MMFrame {
   private static final long serialVersionUID = 1507097881635431043L;

   private JTable table_;
   private PropertyEditorTableData data_;
   private final ShowFlags flags_;

   private JScrollPane scrollPane_;
   private final Studio studio_;
   private final CMMCore core_;

   public PropertyEditor(Studio studio) {
      super("property editor");

      studio_ = studio;
      core_ = studio_.core();

      flags_ = new ShowFlags(studio_);
      flags_.load(PropertyEditor.class);

      createTable();
      createComponents();
      
      loadAndRestorePosition(100, 100, 550, 600);
      setMinimumSize(new Dimension(420, 400));
   }

   private void createTable() {
      data_ = new PropertyEditorTableData(
            studio_, "", "", 1, 2, getContentPane());
      data_.setFlags(flags_);
      data_.setShowUnused(true);
      data_.setColumnNames("Property", "Value", "");

      table_ = new DaytimeNighttime.Table();
      table_.setAutoCreateColumnsFromModel(false);
      table_.setModel(data_);

      table_.addColumn(new TableColumn(0, 200, 
              new PropertyNameCellRenderer(studio_), null));
      table_.addColumn(new TableColumn(1, 200, 
              new PropertyValueCellRenderer(studio_), new PropertyValueCellEditor(false)));
   }

   private void createComponents() {
      setIconImage(Toolkit.getDefaultToolkit().getImage(
              getClass().getResource("/org/micromanager/icons/microscope.gif") ) );

      setLayout(new MigLayout("fill, insets 2, flowy"));

      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            flags_.save(PropertyEditor.class);
         }
         @Override
         public void windowOpened(WindowEvent e) {
            // restore values from the previous session
            data_.update(false);
            data_.fireTableStructureChanged();
        }
      });
      setTitle("Device Property Browser");

      loadAndRestorePosition(100, 100, 550, 600);
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      
      final JButton refreshButton = new JButton("Refresh",
            new ImageIcon(getClass().getResource(
              "/org/micromanager/icons/arrow_refresh.png")));
      
      Font defaultFont = new Font("Arial", Font.PLAIN, 10);
      
      add(new ShowFlagsPanel(data_, flags_, core_,
               core_.getSystemStateCache()),
            "split 2, aligny top, gapbottom 10");
      
      refreshButton.setFont(defaultFont);
      refreshButton.addActionListener((ActionEvent e) -> {
         refresh(false);
      });
      add(refreshButton, "width 100!, center, wrap");

      scrollPane_ = new JScrollPane();
      scrollPane_.setViewportView(table_);
      scrollPane_.setFont(defaultFont);
      scrollPane_.setBorder(new BevelBorder(BevelBorder.LOWERED));
      add(scrollPane_, "span, grow, push, wrap");
   }

   protected void refresh(boolean fromCache) {
      data_.setFlags(flags_);
      data_.setShowUnused(true);
      data_.refresh(fromCache);
   }

   @Subscribe
   public void onPropertyChanged(PropertyChangedEvent event) {
      String device = event.getDevice();
      String property = event.getProperty();
      String value = event.getValue();
      data_.update(device, property, value);
   }

   /**
    * Manually save now; if we wait until the program actually exits, then
    * the profile will be done finalizing and our settings won't get saved.
    * @param event indicating that shutdown is happening
    */
   @Subscribe
   public void onShutdownCommencing(ShutdownCommencingEvent event) {
      if (!event.isCanceled()) {
         flags_.save(PropertyEditor.class);
      }
   }

    public final class PropertyEditorTableData extends PropertyTableData {
      public PropertyEditorTableData(Studio studio, String groupName, String presetName,
         int PropertyValueColumn, int PropertyUsedColumn, Component parentComponent) {

         super(studio, groupName, presetName, PropertyValueColumn, 
                 PropertyUsedColumn, false, true, false, false);
      }

      private static final long serialVersionUID = 1L;

      @Override
      public void setValueAt(Object value, int row, int col) {
         PropertyItem item = propListVisible_.get(row);
         studio_.logs().logMessage("Setting value " + value + " at row " + row);
         if (col == propertyValueColumn_) {
            setValueInCore(item,value);
         }
         core_.updateSystemStateCache();
         studio_.app().refreshGUIFromCache();
         fireTableCellUpdated(row, col);
      }

      public void update (String device, String propName, String newValue) {
         PropertyItem item = getItem(device, propName);
         if (item != null) {
            item.value = newValue;
            Integer row = propToRow_.get(item);
            if (row != null) {
               fireTableCellUpdated(row, propertyValueColumn_);
            }
         }
      }

      @Override
      public void update(ShowFlags flags, String groupName, String presetName, boolean fromCache) {
         try {
            StrVector devices = core_.getLoadedDevices();
            propList_.clear();

            if (!fromCache) {
               // Some properties may not be readable if we are
               // mid-acquisition.
               studio_.live().setSuspended(true);
            }
            for (int i = 0; i < devices.size(); i++) {
               if (data_.showDevice(flags, devices.get(i))) {
                  StrVector properties = core_.getDevicePropertyNames(devices.get(i));
                  for (int j = 0; j < properties.size(); j++){
                     PropertyItem item = new PropertyItem();
                     item.readFromCore(core_, devices.get(i), properties.get(j), fromCache);

                     if ((!item.readOnly || showReadOnly_) && !item.preInit) {
                        propList_.add(item);
                     }
                  }
               }
            }

            updateRowVisibility(flags);

            if (!fromCache) {
               studio_.live().setSuspended(false);
            }
         } catch (Exception e) {
            ReportingUtils.showError(e, "Error updating Device Property Browser");
         }
         this.fireTableStructureChanged();

      }
   }
}


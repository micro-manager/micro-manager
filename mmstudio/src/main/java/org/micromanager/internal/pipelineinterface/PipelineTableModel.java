///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//AUTHOR:        Mark Tsuchida, Chris Weisiger
//COPYRIGHT:     University of California, San Francisco, 2006-2015
//               100X Imaging Inc, www.100ximaging.com, 2008
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.pipelineinterface;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.NewPipelineEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.PropertyMap;

// TODO: currently we redraw the entire table any time it changes, rather than
// only redrawing the row(s) that are modified.
public class PipelineTableModel extends AbstractTableModel {
   static final int ENABLED_COLUMN = 0;
   static final int NAME_COLUMN = 1;
   static final int CONFIGURE_COLUMN = 2;
   private static final int NUM_COLUMNS = 3;
   private ArrayList<ConfiguratorWrapper> pipelineConfigs_;

   PipelineTableModel() {
      pipelineConfigs_ = new ArrayList<ConfiguratorWrapper>();
   }

   public void addConfigurator(ConfiguratorWrapper configurator) {
      pipelineConfigs_.add(configurator);
      fireTableDataChanged();
   }

   public void removeConfigurator(ConfiguratorWrapper configurator) {
      pipelineConfigs_.remove(configurator);
      configurator.getConfigurator().cleanup();
      fireTableDataChanged();
   }

   public void clearPipeline() {
      // Create a copy of the list as we'll be removing from it as we iterate
      // over it.
      for (ConfiguratorWrapper config : new ArrayList<ConfiguratorWrapper>(pipelineConfigs_)) {
         removeConfigurator(config);
      }
   }

   public void moveConfigurator(ConfiguratorWrapper configurator,
         int offset) {
      int oldIndex = pipelineConfigs_.indexOf(configurator);
      if (oldIndex < 0) {
         return;
      }

      int newIndex = oldIndex + offset;
      newIndex = Math.max(0, newIndex);
      newIndex = Math.min(newIndex, pipelineConfigs_.size() - 1);
      pipelineConfigs_.remove(configurator);
      pipelineConfigs_.add(newIndex, configurator);
      fireTableDataChanged();
   }

   @Override
   public void fireTableDataChanged() {
      super.fireTableDataChanged();
      MMStudio.getInstance().events().post(new NewPipelineEvent());
   }

   /**
    * Provide a list of factories for all enabled processors.
    */
   public List<ProcessorFactory> getPipelineFactories() {
      List<ConfiguratorWrapper> configs = getEnabledConfigurators();
      ArrayList<ProcessorFactory> result = new ArrayList<ProcessorFactory>();
      for (ConfiguratorWrapper config : configs) {
         PropertyMap settings = config.getConfigurator().getSettings();
         result.add(config.getPlugin().createFactory(settings));
      }
      return result;
   }

   public ArrayList<ConfiguratorWrapper> getEnabledConfigurators() {
      ArrayList<ConfiguratorWrapper> result = new ArrayList<ConfiguratorWrapper>();
      for (ConfiguratorWrapper config : pipelineConfigs_) {
         if (config.getIsEnabled()) {
            result.add(config);
         }
      }
      return result;
   }

   @Override
   public int getRowCount() {
      return pipelineConfigs_.size();
   }

   @Override
   public int getColumnCount() {
      return NUM_COLUMNS;
   }

   @Override
   public Class<?> getColumnClass(int column) {
      switch (column) {
         case ENABLED_COLUMN:
            return Boolean.class;
         case NAME_COLUMN:
            return String.class;
         case CONFIGURE_COLUMN:
            return ConfiguratorWrapper.class;
      }
      return Object.class;
   }

   @Override
   public String getColumnName(int column) {
      switch (column) {
         case ENABLED_COLUMN:
            return "Enabled";
         case NAME_COLUMN:
            return "Processor";
         case CONFIGURE_COLUMN:
            return "Settings";
      }
      return "";
   }

   @Override
   public boolean isCellEditable(int row, int column) {
      switch (column) {
         case ENABLED_COLUMN: return true;
         case NAME_COLUMN: return false;
         case CONFIGURE_COLUMN: return true;
      }
      return false;
   }

   @Override
   public Object getValueAt(int row, int column) {
      switch (column) {
         case ENABLED_COLUMN:
            return pipelineConfigs_.get(row).getIsEnabled();
         case NAME_COLUMN:
            return pipelineConfigs_.get(row).getName();
         case CONFIGURE_COLUMN:
            return pipelineConfigs_.get(row);
      }
      return null;
   }

   @Override
   public void setValueAt(Object value, int row, int column) {
      if (column == ENABLED_COLUMN) {
         boolean enabled = (Boolean) value;
         pipelineConfigs_.get(row).setIsEnabled(enabled);
      }
   }

   /**
    * Remove all extant configurator GUIs.
    */
   public void cleanup() {
      for (ConfiguratorWrapper config : pipelineConfigs_) {
         config.getConfigurator().cleanup();
      }
   }
}

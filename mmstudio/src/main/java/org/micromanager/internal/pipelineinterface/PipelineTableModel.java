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
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.NewPipelineEvent;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.internal.MMStudio;

// TODO: currently we redraw the entire table any time it changes, rather than
// only redrawing the row(s) that are modified.
public final class PipelineTableModel extends AbstractTableModel {
   static final int ENABLED_COLUMN = 0;
   static final int ENABLED_LIVE_COLUMN = 1;
   static final int NAME_COLUMN = 2;
   static final int CONFIGURE_COLUMN = 3;
   private static final int NUM_COLUMNS = 4;
   private static final String SAVED_PIPELINE = "saved pipeline configuration";

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
    * @param isLiveMode if true, select configurators enabled for live, if
    *        false, select generally-enabled configurators.
    */
   public List<ProcessorFactory> getPipelineFactories(boolean isLiveMode) {
      List<ConfiguratorWrapper> configs = getEnabledConfigurators(isLiveMode);
      ArrayList<ProcessorFactory> result = new ArrayList<ProcessorFactory>();
      for (ConfiguratorWrapper config : configs) {
         PropertyMap settings = config.getConfigurator().getSettings();
         result.add(config.getPlugin().createFactory(settings));
      }
      return result;
   }

   /**
    * Provide a list of configurators for all processors.
    */
   public List<ConfiguratorWrapper> getPipelineConfigurators() {
      return pipelineConfigs_;
   }

   /*
   Provide a list of all enabled configurators. If the argument is true then only configurators
   that are enabled for live mode are returned.
   */
   public List<ConfiguratorWrapper> getEnabledConfigurators(boolean isLiveMode) {
      ArrayList<ConfiguratorWrapper> result = new ArrayList<ConfiguratorWrapper>();
      for (ConfiguratorWrapper config : pipelineConfigs_) {
         if ((isLiveMode && config.getIsEnabledInLive()) ||
               (!isLiveMode && config.getIsEnabled())) {
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
         case ENABLED_LIVE_COLUMN:
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
         case ENABLED_LIVE_COLUMN:
            return "Snap/Live";
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
         case ENABLED_COLUMN:
         case ENABLED_LIVE_COLUMN:
         case CONFIGURE_COLUMN:
            return true;
         case NAME_COLUMN:
            return false;
         default:
            return false;
      }
   }

   @Override
   public Object getValueAt(int row, int column) {
      switch (column) {
         case ENABLED_COLUMN:
            return pipelineConfigs_.get(row).getIsEnabled();
         case ENABLED_LIVE_COLUMN:
            return pipelineConfigs_.get(row).getIsEnabledInLive();
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
         pipelineConfigs_.get(row).setIsEnabled((Boolean) value);
         fireTableDataChanged();
      }
      else if (column == ENABLED_LIVE_COLUMN) {
         pipelineConfigs_.get(row).setIsEnabledInLive((Boolean) value);
         fireTableDataChanged();
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

   /**
    * Record the current pipeline to the user's profile, so it can be
    * restored later.
    */
   public void savePipelineToProfile(Studio studio) {
      ArrayList<String> serializedConfigs = new ArrayList<String>();
      for (ConfiguratorWrapper config : pipelineConfigs_) {
         serializedConfigs.add(config.toJSON());
      }
      studio.profile().getSettings(PipelineTableModel.class).putStringList(
            SAVED_PIPELINE, serializedConfigs);
   }

   /**
    * Restore the pipeline from the user's profile. Return true if we actually
    * updated the model.
    */
   public boolean restorePipelineFromProfile(Studio studio) {
      List<String> serializedConfigs = studio.profile().
              getSettings(PipelineTableModel.class).
              getStringList(SAVED_PIPELINE, new String[] {});
      boolean didUpdate = false;
      for (String configString : serializedConfigs) {
         ConfiguratorWrapper config = ConfiguratorWrapper.fromString(
               configString, studio);
         if (config.getIsEnabled()) {
            didUpdate = true;
         }
         pipelineConfigs_.add(config);
      }
      fireTableDataChanged();
      return didUpdate;
   }
}

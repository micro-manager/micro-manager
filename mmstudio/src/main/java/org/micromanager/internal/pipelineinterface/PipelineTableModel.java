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
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.internal.DefaultNewPipelineEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * Model for the Pipeline Table shown in the UI, whenever On-the-fly-processors are active.
 *
 * <p>TODO: currently we redraw the entire table any time it changes, rather than
 * only redrawing the row(s) that are modified.
 */
public final class PipelineTableModel extends AbstractTableModel {
   static final int ENABLED_COLUMN = 0;
   static final int ENABLED_LIVE_COLUMN = 1;
   static final int NAME_COLUMN = 2;
   static final int CONFIGURE_COLUMN = 3;
   private static final int NUM_COLUMNS = 4;
   private static final String SAVED_PIPELINE = "saved pipeline configuration";

   private final ArrayList<ConfiguratorWrapper> pipelineConfigs_;

   PipelineTableModel() {
      pipelineConfigs_ = new ArrayList<>();
   }

   public void addConfigurator(ConfiguratorWrapper configurator) {
      pipelineConfigs_.add(configurator);
      fireTableDataChanged();
   }

   /**
    * Removes a configurator from the model.
    *
    * @param configurator this one will be removed from the pipeline.
    */
   public void removeConfigurator(ConfiguratorWrapper configurator) {
      pipelineConfigs_.remove(configurator);
      configurator.getConfigurator().cleanup();
      fireTableDataChanged();
   }

   /**
    * Clears (i.e. removes all configurators) the complete pipeline.
    */
   public void clearPipeline() {
      // Create a copy of the list as we'll be removing from it as we iterate
      // over it.
      for (ConfiguratorWrapper config : new ArrayList<>(pipelineConfigs_)) {
         removeConfigurator(config);
      }
   }

   /**
    * PLaces a configurator at another position in the list.
    *
    * @param configurator The one to move
    * @param offset How far up or down in the list it should be moved.  Code will check for
    *               bounds and set to 0 or max if out of bounds.
    */
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
      MMStudio.getInstance().events().post(new DefaultNewPipelineEvent());
   }

   /**
    * Provide a list of factories for all enabled processors.
    *
    * @param isLiveMode if true, select configurators enabled for live, if
    *                   false, select generally-enabled configurators.
    */
   public List<ProcessorFactory> getPipelineFactories(boolean isLiveMode) {
      List<ConfiguratorWrapper> configs = getEnabledConfigurators(isLiveMode);
      ArrayList<ProcessorFactory> result = new ArrayList<>();
      for (ConfiguratorWrapper config : configs) {
         try {
            PropertyMap settings = config.getConfigurator().getSettings();
            result.add(config.getPlugin().createFactory(settings));
         } catch (Exception e) {
            ReportingUtils.showError(e, "Failed to construct all parts of this pipeline.");
         }
      }
      return result;
   }

   /**
    * Provide a list of configurators for all processors.
    */
   public List<ConfiguratorWrapper> getPipelineConfigurators() {
      return pipelineConfigs_;
   }

   /**
    * Provide a list of all enabled configurators. If the argument is true then only configurators
    * that are enabled for live mode are returned.
    */
   public List<ConfiguratorWrapper> getEnabledConfigurators(boolean isLiveMode) {
      ArrayList<ConfiguratorWrapper> result = new ArrayList<>();
      for (ConfiguratorWrapper config : pipelineConfigs_) {
         if ((isLiveMode && config.isEnabledInLive())
               || (!isLiveMode && config.isEnabled())) {
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
         default:
            return Object.class;
      }
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
         default:
            return "";
      }
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
            return pipelineConfigs_.get(row).isEnabled();
         case ENABLED_LIVE_COLUMN:
            return pipelineConfigs_.get(row).isEnabledInLive();
         case NAME_COLUMN:
            return pipelineConfigs_.get(row).getName();
         case CONFIGURE_COLUMN:
            return pipelineConfigs_.get(row);
         default:
            return null;
      }
   }

   @Override
   public void setValueAt(Object value, int row, int column) {
      if (column == ENABLED_COLUMN) {
         pipelineConfigs_.get(row).setEnabled((Boolean) value);
         fireTableDataChanged();
      } else if (column == ENABLED_LIVE_COLUMN) {
         pipelineConfigs_.get(row).setEnabledInLive((Boolean) value);
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
      ArrayList<String> serializedConfigs = new ArrayList<>();
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
      List<String> serializedConfigs = studio.profile()
            .getSettings(PipelineTableModel.class)
            .getStringList(SAVED_PIPELINE, new String[] {});
      boolean didUpdate = false;
      for (String configString : serializedConfigs) {
         ConfiguratorWrapper config = ConfiguratorWrapper.fromString(
               configString, studio);
         if (config != null && (config.isEnabled() || config.isEnabledInLive())) {
            didUpdate = true;
         }
         pipelineConfigs_.add(config);
      }
      fireTableDataChanged();
      return didUpdate;
   }
}

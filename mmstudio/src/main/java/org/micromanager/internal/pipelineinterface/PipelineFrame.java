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

import com.google.common.eventbus.Subscribe;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.events.StartupCompleteEvent;
import org.micromanager.events.internal.NewPluginEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.MMFrame;


final public class PipelineFrame extends MMFrame
      implements ListSelectionListener {

   private static final String TITLE = "On-The-Fly Processor Pipeline";

   private final Studio studio_;

   private final JPopupMenu addProcessorPopup_;

   private final PipelineTable pipelineTable_;
   private final JScrollPane pipelineScrollPane_;

   private final JButton removeButton_;
   private final JButton moveUpButton_;
   private final JButton moveDownButton_;
   private final JButton replayButton_;

   public PipelineFrame(Studio studio) {
      super(TITLE);
      setTitle(TITLE);
      studio_ = studio;

      setLayout(new MigLayout("fill, flowy, insets dialog",
            "[align center, grow]unrelated[align left]",
            "[][align top, grow][]"));

      //
      // First column of the layout
      //
      final String downwardsArrow = "<html><b>\u2193</b></html>";
      add(new JLabel("<html><b>Camera</b></html>"), "split 2");
      add(new JLabel(downwardsArrow));

      pipelineTable_ = new PipelineTable();
      pipelineTable_.setRowHeight(pipelineTable_.getRowHeight() * 2);
      pipelineTable_.getSelectionModel().addListSelectionListener(this);

      pipelineScrollPane_ = new JScrollPane(pipelineTable_,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      pipelineScrollPane_.setPreferredSize(new Dimension(370, 80));
      pipelineScrollPane_.setMinimumSize(new Dimension(370,
            pipelineTable_.getRowHeight()));
      add(pipelineScrollPane_, "growx, growy");

      add(new JLabel(downwardsArrow), "split 2");
      add(new JLabel("<html><b>Dataset</b></html>"), "wrap");

      //
      // Second column of the layout
      //
      addProcessorPopup_ = new JPopupMenu();
      final JButton addButton = new JButton("Add...");
      addButton.setIcon(new ImageIcon(MMStudio.class.getResource(
            "/org/micromanager/icons/plus.png")));
      addButton.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            Component button = e.getComponent();
            addProcessorPopup_.show(button, 0, button.getHeight());
         }
      });
      add(addButton, "sizegroup btns, skip 1, split 5");

      removeButton_ = new JButton("Remove");
      removeButton_.setIcon(new ImageIcon (MMStudio.class.getResource(
            "/org/micromanager/icons/minus.png")));
      removeButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            removeSelectedConfigurator();
         }
      });
      add(removeButton_, "sizegroup btns");

      moveUpButton_ = new JButton("Move Up");
      moveUpButton_.setIcon(new ImageIcon (MMStudio.class.getResource(
            "/org/micromanager/icons/arrow_up.png")));
      moveUpButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            moveSelectedConfigurator(-1);
         }
      });
      add(moveUpButton_, "sizegroup btns");

      moveDownButton_ = new JButton("Move Down");
      moveDownButton_.setIcon(new ImageIcon (MMStudio.class.getResource(
            "/org/micromanager/icons/arrow_down.png")));
      moveDownButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            moveSelectedConfigurator(+1);
         }
      });
      add(moveDownButton_, "sizegroup btns, gapbottom push");

      JLabel explanationLabel = new JLabel(
            "<html><div width=\"125\" style=\"font-size: small\">"
            + "Enabled processors in the pipeline are applied in order to "
            + "images acquired by the camera."
            + "</div></html>");
      add(explanationLabel);

      replayButton_ = new JButton("Process Old Data");
      replayButton_.setToolTipText("Apply the current enabled processors to an existing dataset.");
      replayButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (getTableModel().getEnabledConfigurators(false).size() == 0) {
               studio_.logs().showError("Please set up a pipeline first.");
               return;
            }
            ProcessExistingDataDialog.makeDialog(studio_);
         }
      });
      add(replayButton_);

      //
      // Overall constraints
      //
      pack();
      final Dimension contentSize = getContentPane().getPreferredSize();
      final Dimension minSize = getContentPane().getMinimumSize();

      // Compute the difference between the content pane's size and the
      // frame's size, so that we can constrain the frame's size.
      final int widthDelta = getSize().width - getContentPane().getSize().width;
      final int heightDelta = getSize().height - getContentPane().getSize().height;

      final Dimension frameSize = new Dimension(contentSize.width + widthDelta,
            contentSize.height + heightDelta);
      final Dimension minFrameSize = new Dimension(minSize.width + widthDelta,
            minSize.height + heightDelta);
      setPreferredSize(frameSize);
      setMinimumSize(minFrameSize);
      
      super.loadAndRestorePosition(200, 200);

      studio_.events().registerForEvents(this);
      reloadProcessors();
      updateEditButtonStatus(pipelineTable_.getSelectionModel());
   }

   // Handle selection change in pipeline table
   @Override
   public void valueChanged(ListSelectionEvent e) {
      ListSelectionModel model = (ListSelectionModel) e.getSource();
      updateEditButtonStatus(model);
   }

   private void updateEditButtonStatus(ListSelectionModel model) {
      boolean enableEditButtons = !model.isSelectionEmpty();
      removeButton_.setEnabled(enableEditButtons);
      moveUpButton_.setEnabled(enableEditButtons
            && model.getMaxSelectionIndex() > 0);
      moveDownButton_.setEnabled(enableEditButtons
            && model.getMinSelectionIndex() < pipelineTable_.getRowCount() - 1);
   }

   /**
    * A new ProcessorPlugin was registered; re-load our list of registered
    * processors.
    * @param event
    */
   @Subscribe
   public void onNewPlugin(NewPluginEvent event) {
      if (event.getPlugin() instanceof ProcessorPlugin) {
         reloadProcessors();
      }
   }

   /**
    * User has logged in and startup is complete; restore their pipeline.
    */
   @Subscribe
   public void onStartupComplete(StartupCompleteEvent event) {
      try {
         if (getTableModel().restorePipelineFromProfile(studio_)) {
            setVisible(true);
         }
      }
      catch (Exception e) {
         studio_.logs().logError(e, "Failed to reload pipeline");
      }
   }

   /**
    * When shutdown starts, we record the current processing pipeline, so it
    * can be restored later.
    */
   @Subscribe
   public void onShutdownCommencing(ShutdownCommencingEvent event) {
      getTableModel().savePipelineToProfile(studio_);
   }

   /**
    * Re-acquire the entire list of registered processors. We do
    * things this way because we have no idea when we are created vs. when all
    * the plugins are registered (they happen in different threads), thus we
    * have no idea currently how many processors are registered that we don't
    * know about.
    */
   private void reloadProcessors() {
      final HashMap<String, ProcessorPlugin> plugins = studio_.plugins().getProcessorPlugins();
      // Maps the plugin's declared name to the key in the above plugins
      // object.
      final HashMap<String, String> nameToPath = new HashMap<String, String>();
      for (String pluginPath : plugins.keySet()) {
         nameToPath.put(plugins.get(pluginPath).getName(), pluginPath);
      }
      ArrayList<String> names = new ArrayList<String>(nameToPath.keySet());
      Collections.sort(names);
      addProcessorPopup_.removeAll();
      final PropertyMap blankSettings = PropertyMaps.builder().build();
      for (final String name : names) {
         Action addAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
               String path = nameToPath.get(name);
               getTableModel().addConfigurator(
                     new ConfiguratorWrapper(plugins.get(path),
                        plugins.get(path).createConfigurator(blankSettings),
                        name));
            }
         };
         addAction.putValue(Action.NAME, name);
         addProcessorPopup_.add(new JMenuItem(addAction));
      }
   }

   // Convenience function to type-convert the table model for us.
   private PipelineTableModel getTableModel() {
      return (PipelineTableModel) pipelineTable_.getModel();
   }

   private void removeSelectedConfigurator() {
      ConfiguratorWrapper configurator = pipelineTable_.getSelectedConfigurator();
      getTableModel().removeConfigurator(configurator);
   }

   private void moveSelectedConfigurator(int offset) {
      int i = pipelineTable_.getSelectedRow();
      getTableModel().moveConfigurator(pipelineTable_.getSelectedConfigurator(), offset);
      // Retain the selection
      pipelineTable_.getSelectionModel().
            setSelectionInterval(i + offset, i + offset);
   }

   /**
    * Add a new entry to the list of processors.
    */
   public void addAndConfigureProcessor(ProcessorPlugin plugin) {
      // Create it with a blank set of settings.
      ProcessorConfigurator configurator = plugin.createConfigurator(
            PropertyMaps.builder().build());
      getTableModel().addConfigurator(new ConfiguratorWrapper(plugin,
            configurator, plugin.getName()));
      setVisible(true);
      configurator.showGUI();
   }

   /**
    * Add a new entry to the list of processors, which has already been
    * configured.
    */
   public void addConfiguredProcessor(ProcessorConfigurator config,
         ProcessorPlugin plugin) {
      getTableModel().addConfigurator(new ConfiguratorWrapper(plugin,
               config, plugin.getName()));
      setVisible(true);
      config.showGUI();
   }

   /**
    * Generate a list of ProcessorFactories based on the currently-enabled
    * configurators and their settings.
    */
   public List<ProcessorFactory> getPipelineFactories() {
      // We want the processors that are generally enabled, not the ones that
      // are only enabled in Live.
      return getTableModel().getPipelineFactories(false);
   }

   public List<ProcessorFactory> getLivePipelineFactories() {
      // We want the processor that are only enabled in Live.
      return getTableModel().getPipelineFactories(true);
   }

   private List<ProcessorConfigurator> convertWrappersToConfigurators(List<ConfiguratorWrapper> configs){
        ArrayList<ProcessorConfigurator> result = new ArrayList<ProcessorConfigurator>();
        for (ConfiguratorWrapper config : configs) {
            result.add(config.getConfigurator());
        }
        return result; 
   }
   
   /**
    * Return a list of the configurators.
    */
   public List<ProcessorConfigurator> getPipelineConfigurators() {
      return convertWrappersToConfigurators(getTableModel().getPipelineConfigurators());
   }
   
    /**
    * Return a list of the enabled configurators.
    */
   public List<ProcessorConfigurator> getEnabledPipelineConfigurators() {
        List<ConfiguratorWrapper> configs = getTableModel().getEnabledConfigurators(false);
        return convertWrappersToConfigurators(configs);
   }
   
    /**
    * Return a list of the live-mode enabled configurators.
    */
   public List<ProcessorConfigurator> getEnabledLivePipelineConfigurators() {
        List<ConfiguratorWrapper> configs = getTableModel().getEnabledConfigurators(true);
        return convertWrappersToConfigurators(configs);
   }
   
    /*
    * Set whether or not a configurator is enabled.
    */
   public void setConfiguratorEnabled(int row, boolean enabled) {
       int column = getTableModel().ENABLED_COLUMN;
       getTableModel().setValueAt(enabled, row, column);
   }
   
   /*
   * Set whether or not a configurator is enabled for live mode
   */
    public void setConfiguratorEnabledLive(int row, boolean enabled) {
       int column = getTableModel().ENABLED_LIVE_COLUMN;
       getTableModel().setValueAt(enabled, row, column);
    }
   
    /*
    * Get whether or not a configurator is enabled.
    */
   public boolean getConfiguratorEnabled(int row) {
       int column = getTableModel().ENABLED_COLUMN;
       return (boolean) getTableModel().getValueAt(row, column);
   }
   
   /*
   * Get whether or not a configurator is enabled for live mode
   */
    public boolean getConfiguratorEnabledLive(int row) {
       int column = getTableModel().ENABLED_LIVE_COLUMN;
       return (boolean) getTableModel().getValueAt(row, column);
    }

   /**
    * Clear the pipeline table.
    */
   public void clearPipeline() {
      getTableModel().clearPipeline();
   }

   @Override
   public void dispose() {
      super.dispose();
      getTableModel().cleanup();
   }
}

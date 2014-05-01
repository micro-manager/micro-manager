package org.micromanager.pipelineUI;

import com.google.common.eventbus.Subscribe;

import java.awt.Dimension;

import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import mmcorej.TaggedImage;

import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.ScriptInterface;
import org.micromanager.events.EventManager;
import org.micromanager.events.PipelineEvent;
import org.micromanager.events.ProcessorEvent;
import org.micromanager.utils.ReportingUtils;

public class PipelinePanel extends javax.swing.JFrame {
   ScriptInterface gui_;
   AcquisitionEngine engine_;
   static PipelinePanel panelSingleton_;
   // Panel that contains all of our UI elements.
   javax.swing.JPanel subPanel_;
   // Listbox that holds all the DataProcessor types we know about.
   JList registeredProcessorsList_;
   // Name model for the above.
   DefaultListModel processorNameModel_;

   // Panel that holds all the ProcessorPanels for instanced DataProcessors.
   javax.swing.JPanel processorsPanel_;
   // Scrolling view of the above panel.
   JScrollPane processorsScroller_;

   public PipelinePanel(ScriptInterface gui, AcquisitionEngine engine) {
      super("Image pipeline");
      gui_ = gui;
      engine_ = engine;
      panelSingleton_ = this;

      setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
      setLocationRelativeTo(null);

      // Create a panel to hold all our contents (with a horizontal layout).
      subPanel_ = new javax.swing.JPanel(
            new net.miginfocom.swing.MigLayout());

      // Set up the panel that holds the current active pipeline
      processorsPanel_ = new javax.swing.JPanel(
            new net.miginfocom.swing.MigLayout("wrap 1"));

      processorsScroller_ = new JScrollPane(processorsPanel_, 
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, 
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      // Make it wide enough to hold each ProcessorPanel, and tall enough to
      // show 3 without scrolling.
      processorsScroller_.setPreferredSize(
            new java.awt.Dimension(ProcessorPanel.preferredWidth,
               ProcessorPanel.preferredHeight * 3));
      subPanel_.add(processorsScroller_);

      // Set up the listbox for available DataProcessor types.
      processorNameModel_ = new DefaultListModel();

      registeredProcessorsList_ = new JList(processorNameModel_);
      registeredProcessorsList_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      registeredProcessorsList_.setLayoutOrientation(JList.VERTICAL);
      JScrollPane processorScroller = new JScrollPane(registeredProcessorsList_);
      subPanel_.add(processorScroller);

      // Add a button to let the user make new processors.
      JButton newButton = new JButton("New");
      newButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            makeNewProcessor();
         }
      });
      subPanel_.add(newButton);
      
      add(subPanel_);

      setMinimumSize(getPreferredSize());

      // Set up listening to the registration of new DataProcessors and
      // modification of the image pipeline.
      EventManager.register(this);

      reloadProcessors();
   }

   /** 
    * A new ProcessorPlugin was registered; re-load our list of registered
    * processors. 
    */
   @Subscribe
   public void newProcessorRegistered(ProcessorEvent event) {
      panelSingleton_.reloadProcessors();
   }

   /**
    * The image pipeline was modified; reload our display of it.
    */
   @Subscribe
   public void pipelineChanged(PipelineEvent event) {
      panelSingleton_.reloadPipeline(event.getPipeline());
   }

   /**
    * Re-acquire the entire list of registered processors from the engine.
    * We do things this way because we have no idea when we are
    * created vs. when all the plugins are registered (they happen in 
    * different threads), thus we have no idea currently how many processors
    * are registered that we don't know about. 
    */
   private void reloadProcessors() {
      List<String> names = engine_.getSortedDataProcessorNames();
      processorNameModel_.clear();
      for (String name : names) {
         processorNameModel_.addElement(name);
      }
   }

   /**
    * Re-generate our list of active processors.
    */
   private void reloadPipeline(List<DataProcessor<TaggedImage>> pipeline) {
      processorsPanel_.removeAll();
      for (DataProcessor<TaggedImage> processor : pipeline) {
         String name = engine_.getNameForProcessorClass(processor.getClass());
         ProcessorPanel panel = new ProcessorPanel(name, processor, gui_);
         processorsPanel_.add(panel);
      }
      processorsPanel_.validate();
      processorsPanel_.repaint();
      subPanel_.revalidate();
   }

   /**
    * Generate a new Processor of the type currently selected in the list of
    * registered processors.
    */
   void makeNewProcessor() {
      int index = registeredProcessorsList_.getSelectedIndex();
      String name = (String) processorNameModel_.get(index);
      // Note this will invoke our listener and make us recreate our pipeline
      // display.
      engine_.makeProcessor(name, gui_);
   }
}

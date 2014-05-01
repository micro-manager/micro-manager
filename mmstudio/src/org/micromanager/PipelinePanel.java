package org.micromanager;

import java.lang.reflect.Method;

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
import org.micromanager.utils.ReportingUtils;

public class PipelinePanel extends javax.swing.JFrame {
   ScriptInterface gui_;
   AcquisitionEngine engine_;
   static PipelinePanel panelSingleton_;
   // Listbox that holds all the DataProcessor types we know about.
   JList registeredProcessorsList_;
   // Name model for the above.
   DefaultListModel processorNameModel_;

   // Listbox that holds all of the DataProcessor instances currently in the 
   // pipeline.
   JList pipelineList_;
   // Name model for the above. 
   DefaultListModel pipelineNameModel_;

   PipelinePanel(ScriptInterface gui, AcquisitionEngine engine) {
      super("Image pipeline");
      gui_ = gui;
      engine_ = engine;
      panelSingleton_ = this;

      setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
      setLocationRelativeTo(null);

      processorNameModel_ = new DefaultListModel();

      registeredProcessorsList_ = new JList(processorNameModel_);
      registeredProcessorsList_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      registeredProcessorsList_.setLayoutOrientation(JList.VERTICAL);
      JScrollPane processorScroller = new JScrollPane(registeredProcessorsList_);

      pipelineNameModel_ = new DefaultListModel();

      pipelineList_ = new JList(pipelineNameModel_);
      pipelineList_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      pipelineList_.setLayoutOrientation(JList.VERTICAL);
      JScrollPane pipelineScroller = new JScrollPane(pipelineList_);

      javax.swing.JPanel subPanel = new javax.swing.JPanel(
            new net.miginfocom.swing.MigLayout());
      subPanel.add(pipelineScroller);
      subPanel.add(processorScroller);

      // Add a button to let the user make new processors.
      JButton newButton = new JButton("New");
      newButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            makeNewProcessor();
         }
      });
      subPanel.add(newButton);
      
      add(subPanel);

      // Set up listening to the registration of new DataProcessors
      try {
         Class[] params = new Class[1];
         params[0] = String.class;
         Method listener = getClass().getDeclaredMethod(
               "newProcessorRegistered", params);
         engine_.addRegistrationListener(listener);
      }
      catch (Exception ex) {
         ReportingUtils.logError("Failed to add listener for DataProcessor registration: " + ex);
      }

      // Set up listening to the creation/destruction/reordering of the 
      // image pipeline.
      try {
         Class [] params = new Class[1];
         params[0] = List.class;
         Method listener = getClass().getDeclaredMethod(
               "pipelineChanged", params);
         engine_.addPipelineChangedListener(listener);
      }
      catch (Exception ex) {
         ReportingUtils.logError("Failed to add listener for DataProcessor creation: " + ex);
      }

      reloadProcessors();
   }

   /** 
    * A new ProcessorPlugin was registered; re-load our list of registered
    * processors. 
    */
   public static void newProcessorRegistered(String name) {
      panelSingleton_.reloadProcessors();
   }

   /**
    * The image pipeline was modified; reload our display of it.
    */
   public static void pipelineChanged(List<DataProcessor<TaggedImage>> pipeline) {
      panelSingleton_.reloadPipeline(pipeline);
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
      pipelineNameModel_.clear();
      for (DataProcessor<TaggedImage> processor : pipeline) {
         String name = engine_.getNameForProcessorClass(processor.getClass());
         pipelineNameModel_.addElement(name);
      }
   }

   /**
    * Generate a new Processor of the type currently selected in the list of
    * registered processors.
    */
   void makeNewProcessor() {
      int index = registeredProcessorsList_.getSelectedIndex();
      String name = (String) processorNameModel_.get(index);
      engine_.makeProcessor(name, gui_);
   }
}

package org.micromanager;

import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JList;

import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.utils.ReportingUtils;

class PipelinePanel extends javax.swing.JFrame {
   AcquisitionEngine engine_;

   PipelinePanel(AcquisitionEngine engine) {
      super("Image pipeline");
      engine_ = engine;

      setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
      setLocationRelativeTo(null);

      // List of DataProcessor names.
      List<String> processorNames = engine_.getSortedDataProcessorNames();

      ReportingUtils.logError("Got processor names " + processorNames);

      // Make a ListModel out of the above.
      DefaultListModel nameModel = new DefaultListModel();
      for (String name : processorNames) {
         ReportingUtils.logError("Adding " + name + " to listbox");
         nameModel.addElement(name);
      }

      // Listbox that holds all the DataProcessor types we know about.
      JList registeredProcessors = new JList(nameModel);

      javax.swing.JPanel subPanel_ = new javax.swing.JPanel(
            new net.miginfocom.swing.MigLayout());
      subPanel_.add(registeredProcessors);
      add(subPanel_);
   }
}

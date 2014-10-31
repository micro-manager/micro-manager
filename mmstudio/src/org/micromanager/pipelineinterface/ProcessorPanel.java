package org.micromanager.pipelineinterface;

import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicArrowButton;

import java.util.List;

import mmcorej.TaggedImage;


import org.micromanager.api.DataProcessor;
import org.micromanager.api.ScriptInterface;
import org.micromanager.events.EventManager;
import org.micromanager.events.ProcessorEnabledEvent;
import org.micromanager.utils.ReportingUtils;

public class ProcessorPanel extends JPanel {
   // The DataProcessor we are displaying controls for. 
   DataProcessor<TaggedImage> processor_;
   // We need access to the ScriptInterface to enable/disable the Processor.
   ScriptInterface gui_;
   // Checkbox for enabling/disabling the Processor.
   JCheckBox activeBox_;

   // Preferred width of the panel.
   public static int preferredWidth = 400;
   // Preferred height of the panel.
   public static int preferredHeight = 80;

   ProcessorPanel(String name, DataProcessor<TaggedImage> processor,
         ScriptInterface gui) {
      // The central element is the processor name, i.e. the only element
      // of variable size. We want it to be of fixed size so that elements
      // to its right are properly-aligned.
      super(new net.miginfocom.swing.MigLayout("wrap 3", 
               "[][200!][]"));
      setBorder(javax.swing.BorderFactory.createRaisedBevelBorder());
      processor_ = processor;
      gui_ = gui;

      activeBox_ = new JCheckBox("Active:", processor.getIsEnabled());
      activeBox_.setToolTipText("Toggle whether or not this DataProcessor is allowed to modify images.");
      activeBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            toggleActive();
         }
      });
      add(activeBox_);

      JLabel label = new JLabel(name);
      add(label);

      // Move the processor up (earlier) in the pipeline.
      BasicArrowButton upButton = new BasicArrowButton(BasicArrowButton.NORTH);
      upButton.setToolTipText("Move this DataProcessor earlier in the pipeline.");
      upButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            moveProcessor(-1);
         }
      });
      add(upButton);

      JButton configButton = new JButton("Configure");
      configButton.setToolTipText("Bring up the configuration interface for this DataProcessor, if applicable.");
      configButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            processor_.makeConfigurationGUI();
         }
      });
      add(configButton);

      JButton deleteButton = new JButton("Delete");
      deleteButton.setToolTipText("Delete this DataProcessor, removing it from the pipeline.");
      deleteButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            gui_.removeImageProcessor(processor_);
         }
      });
      add(deleteButton);

      // Move the processor down (later) in the pipeline.
      BasicArrowButton downButton = new BasicArrowButton(BasicArrowButton.SOUTH);
      downButton.setToolTipText("Move this DataProcessor later in the pipeline.");
      downButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            moveProcessor(1);
         }
      });
      add(downButton);

      EventManager.register(this);
   }

   // Overriding this method so that layout of these panels works properly.
   public Dimension getPreferredSize() {
      return new Dimension(ProcessorPanel.preferredWidth, ProcessorPanel.preferredHeight);
   }

   // Toggle whether or not this Processor belongs in the pipeline.
   private void toggleActive() {
      processor_.setEnabled(activeBox_.isSelected());
   }

   @Subscribe
   public void processorEnabledChanged(ProcessorEnabledEvent e) {
      DataProcessor<?> processor = e.getProcessor();
      if (processor == processor_) {
         activeBox_.setSelected(e.getEnabled());
      }
   }

   // Adjust the processor's position in the pipeline by the given offset.
   private void moveProcessor(int offset) {
      List<DataProcessor<TaggedImage>> pipeline = gui_.getImageProcessorPipeline();
      // NB for some reason using List.indexOf doesn't work here, so we 
      // manually scan the list for a matching Processor.
      int curIndex = 0;
      for (DataProcessor<TaggedImage> altProcessor : pipeline) {
         if (altProcessor.hashCode() == processor_.hashCode()) {
            break;
         }
         curIndex++;
      }
      if (curIndex == pipeline.size()) {
         ReportingUtils.logError("Tried to move a processor that isn't actually in the pipeline!");
         return;
      }
      int targetIndex = curIndex + offset;
      if (targetIndex < 0 || targetIndex >= pipeline.size()) {
         // Processor is already at the end of the pipeline and can't be 
         // moved further.
         return;
      }
      pipeline.remove(processor_);
      pipeline.add(targetIndex, processor_);
      gui_.setImageProcessorPipeline(pipeline);
   }
}

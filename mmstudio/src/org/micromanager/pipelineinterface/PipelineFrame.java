package org.micromanager.pipelineinterface;

import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import mmcorej.TaggedImage;

import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.ScriptInterface;
import org.micromanager.events.EventManager;
import org.micromanager.events.PipelineEvent;
import org.micromanager.events.ProcessorEvent;

public class PipelineFrame extends JFrame {
   ScriptInterface gui_;
   AcquisitionEngine engine_;
   // Listbox that holds all the DataProcessor types we know about.
   JList registeredProcessorsList_;
   // Name model for the above.
   DefaultListModel processorNameModel_;

   // Panel that holds all the ProcessorPanels for instanced DataProcessors.
   JPanel processorsPanel_;
   // Scrolling view of the above panel.
   JScrollPane processorsScroller_;

   public PipelineFrame(ScriptInterface gui, AcquisitionEngine engine) {
      super("Image Processor Pipeline");
      gui_ = gui;
      engine_ = engine;

      setLocationRelativeTo(null);

      JPanel contentPanel = new JPanel();
      contentPanel.setLayout(new net.miginfocom.swing.MigLayout("fill, flowy, insets dialog",
            "[align left]unrelated[align center]", "[align top, grow]"));

      //
      // First column of the layout
      //

      contentPanel.add(new javax.swing.JLabel("Available Image Processors:"), "split 3, gapafter related");

      // Set up the listbox for available DataProcessor types.
      processorNameModel_ = new DefaultListModel();
      registeredProcessorsList_ = new JList(processorNameModel_);
      registeredProcessorsList_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      registeredProcessorsList_.setLayoutOrientation(JList.VERTICAL);
      JScrollPane registeredScroller = new JScrollPane(registeredProcessorsList_);
      contentPanel.add(registeredScroller, "growx, gapafter related");

      // Add a button to let the user make new processors.
      JButton newButton = new JButton("New");
      newButton.setToolTipText("Create a new DataProcessor of the selected type, and add it to the end of the pipeline");
      newButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            makeNewProcessor();
         }
      });
      contentPanel.add(newButton, "gapafter push, wrap");

      //
      // Second column of the layout
      //

      contentPanel.add(new javax.swing.JLabel("Image Processor Pipeline:"), "align left, split 2");

      // Set up the panel that holds the current active pipeline
      processorsPanel_ = new JPanel(new net.miginfocom.swing.MigLayout(
               "aligny top, fillx, insets 0, wrap 1"));

      processorsScroller_ = new JScrollPane(processorsPanel_,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      // Make it wide enough to hold each ProcessorPanel, and tall enough to
      // show 3 without scrolling.
      processorsScroller_.setPreferredSize(
            new Dimension(ProcessorPanel.preferredWidth,
               ProcessorPanel.preferredHeight * 3));
      processorsScroller_.setMinimumSize(
            new Dimension(ProcessorPanel.preferredWidth,
               ProcessorPanel.preferredHeight));
      processorsScroller_.setMaximumSize(
            new Dimension(ProcessorPanel.preferredWidth, 8192));
      contentPanel.add(processorsScroller_, "growx, growy");

      //
      // Overall constraints
      //

      contentPanel.validate();
      final Dimension contentSize = contentPanel.getPreferredSize();
      final Dimension minSize = contentPanel.getMinimumSize();
      minSize.width = contentSize.width;
      final Dimension maxSize = new Dimension(contentSize.width, 8192);
      contentPanel.setMinimumSize(minSize);
      contentPanel.setMaximumSize(maxSize);

      // We want the window (frame) to be resizable, but we don't want its
      // width to change.
      // JFrame.setMaximumSize() is broken (a Swing bug). We work around this
      // 1) by placing all components in a JPanel (contentPanel), whose size
      // behaves exactly as we would like the frame's size to behave, and 2)
      // by always snapping back to our preferred width using a
      // ComponentListener. So the user sees extra blank space to the right
      // while resizing the window, but the contents resize correctly and the
      // window snaps to the correct width when the mouse is released.
      // TODO Can we factor out this behavior into a separate class?
      setLayout(new net.miginfocom.swing.MigLayout(
            "align left, filly, insets 0"));
      add(contentPanel, "align left, growy");

      // Compute the difference between the content pane's size and the
      // frame's size, so that we can constrain the frame's size.
      pack();
      final int widthDelta = getSize().width - getContentPane().getSize().width;
      final int heightDelta = getSize().height - getContentPane().getSize().height;

      final Dimension frameSize = new Dimension(contentSize.width + widthDelta,
            contentSize.height + heightDelta);
      final Dimension minFrameSize = new Dimension(minSize.width + widthDelta,
            minSize.height + heightDelta);
      final Dimension maxFrameSize = new Dimension(maxSize.width + widthDelta,
            maxSize.height + heightDelta);
      setPreferredSize(frameSize);
      setMinimumSize(minFrameSize);
      setMaximumSize(maxFrameSize); // broken
      setMaximizedBounds(new Rectangle(getX(), 0, maxFrameSize.width, 4096));
      addComponentListener(new ComponentAdapter() {
         @Override public void componentResized(ComponentEvent e) {
            Dimension size = getSize();
            size.width = Math.max(size.width, minFrameSize.width);
            size.width = Math.min(size.width, maxFrameSize.width);
            size.height = Math.max(size.height, minFrameSize.height);
            size.height = Math.min(size.height, maxFrameSize.height);
            setSize(size);
         }
         @Override public void componentMoved(ComponentEvent e) {
            if (getExtendedState() == Frame.NORMAL) {
               setMaximizedBounds(new Rectangle(getX(), 0, maxFrameSize.width, 4096));
            }
         }
      });

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
      reloadProcessors();
   }

   /**
    * The image pipeline was modified; reload our display of it.
    */
   @Subscribe
   public void pipelineChanged(PipelineEvent event) {
      reloadPipeline(event.getPipeline());
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
         @SuppressWarnings("unchecked")
         Class<? extends DataProcessor<TaggedImage>> procCls =
            (Class<? extends DataProcessor<TaggedImage>>) processor.getClass();

         String name = engine_.getNameForProcessorClass(procCls);
         ProcessorPanel panel = new ProcessorPanel(name, processor, gui_);
         processorsPanel_.add(panel);
      }
      processorsPanel_.validate();
      processorsPanel_.repaint();
      validate();
   }

   /**
    * Generate a new Processor of the type currently selected in the list of
    * registered processors.
    */
   void makeNewProcessor() {
      int index = registeredProcessorsList_.getSelectedIndex();
      if (index == -1) {
         // No selected processor type.
         return;
      }
      String name = (String) processorNameModel_.get(index);
      // Note this will invoke our listener and make us recreate our pipeline
      // display.
      engine_.makeProcessor(name, gui_);
   }
}

package org.micromanager.pipelineinterface;

import com.google.common.eventbus.Subscribe;
import com.swtdesigner.SwingResourceManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.ScriptInterface;
import org.micromanager.events.EventManager;
import org.micromanager.events.ProcessorEvent;


final public class PipelineFrame extends JFrame
      implements ListSelectionListener {

   private final ScriptInterface gui_;
   private final AcquisitionEngine engine_;

   private final JPopupMenu addProcessorPopup_;

   private final PipelineTable pipelineTable_;
   private final JScrollPane pipelineScrollPane_;

   private final JButton removeButton_;
   private final JButton moveUpButton_;
   private final JButton moveDownButton_;

   public PipelineFrame(ScriptInterface gui, AcquisitionEngine engine) {
      super("Image Processor Pipeline");
      gui_ = gui;
      engine_ = engine;

      setLocationRelativeTo(null);

      JPanel contentPanel = new JPanel();
      contentPanel.setLayout(new MigLayout("fill, flowy, insets dialog",
            "[align center, grow]unrelated[align left]",
            "[][align top, grow][]"));

      //
      // First column of the layout
      //
      final String downwardsArrow = "<html><b>\u2193</b></html>";
      contentPanel.add(new JLabel("<html><b>Camera</b></html>"), "split 2");
      contentPanel.add(new JLabel(downwardsArrow));

      pipelineTable_ = new PipelineTable(gui_, engine_);
      pipelineTable_.setRowHeight(pipelineTable_.getRowHeight() * 2);
      pipelineTable_.getSelectionModel().addListSelectionListener(this);

      pipelineScrollPane_ = new JScrollPane(pipelineTable_,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      pipelineScrollPane_.setPreferredSize(new Dimension(320, 80));
      pipelineScrollPane_.setMinimumSize(new Dimension(320,
            pipelineTable_.getRowHeight()));
      contentPanel.add(pipelineScrollPane_, "growx, growy");

      contentPanel.add(new JLabel(downwardsArrow), "split 2");
      contentPanel.add(new JLabel("<html><b>Dataset</b></html>"), "wrap");

      //
      // Second column of the layout
      //
      addProcessorPopup_ = new JPopupMenu();
      final JButton addButton = new JButton("Add...");
      addButton.setIcon(SwingResourceManager.getIcon(MMStudio.class,
            "/org/micromanager/icons/plus.png"));
      addButton.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            Component button = e.getComponent();
            addProcessorPopup_.show(button, 0, button.getHeight());
         }
      });
      contentPanel.add(addButton, "sizegroup btns, skip 1, split 5");

      removeButton_ = new JButton("Remove");
      removeButton_.setIcon(SwingResourceManager.getIcon(MMStudio.class,
            "/org/micromanager/icons/minus.png"));
      removeButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            removeSelectedProcessor();
         }
      });
      contentPanel.add(removeButton_, "sizegroup btns");

      moveUpButton_ = new JButton("Move Up");
      moveUpButton_.setIcon(SwingResourceManager.getIcon(MMStudio.class,
            "/org/micromanager/icons/arrow_up.png"));
      moveUpButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            moveSelectedProcessor(-1);
         }
      });
      contentPanel.add(moveUpButton_, "sizegroup btns");

      moveDownButton_ = new JButton("Move Down");
      moveDownButton_.setIcon(SwingResourceManager.getIcon(MMStudio.class,
            "/org/micromanager/icons/arrow_down.png"));
      moveDownButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            moveSelectedProcessor(+1);
         }
      });
      contentPanel.add(moveDownButton_, "sizegroup btns, gapbottom push");

      JLabel explanationLabel = new JLabel(
            "<html><div width=\"125\" style=\"font-size: small\">"
            + "The active image processors in the pipeline are applied to "
            + "acquired images in order."
            + "</div></html>");
      contentPanel.add(explanationLabel);

      //
      // Overall constraints
      //
      contentPanel.validate();
      final Dimension contentSize = contentPanel.getPreferredSize();
      final Dimension minSize = contentPanel.getMinimumSize();
      minSize.width = contentSize.width;
      contentPanel.setMinimumSize(minSize);

      setLayout(new MigLayout("fill, insets 0, nogrid"));
      add(contentPanel, "growx, growy");

      // Compute the difference between the content pane's size and the
      // frame's size, so that we can constrain the frame's size.
      pack();
      final int widthDelta = getSize().width - getContentPane().getSize().width;
      final int heightDelta = getSize().height - getContentPane().getSize().height;

      final Dimension frameSize = new Dimension(contentSize.width + widthDelta,
            contentSize.height + heightDelta);
      final Dimension minFrameSize = new Dimension(minSize.width + widthDelta,
            minSize.height + heightDelta);
      setPreferredSize(frameSize);
      setMinimumSize(minFrameSize);

      // Set up listening to the registration of new DataProcessors and
      // modification of the image pipeline.
      EventManager.register(this);

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
    */
   @Subscribe
   public void newProcessorRegistered(ProcessorEvent event) {
      reloadProcessors();
   }

   /**
    * Re-acquire the entire list of registered processors from the engine. We do
    * things this way because we have no idea when we are created vs. when all
    * the plugins are registered (they happen in different threads), thus we
    * have no idea currently how many processors are registered that we don't
    * know about.
    */
   private void reloadProcessors() {
      List<String> names = engine_.getSortedDataProcessorNames();
      addProcessorPopup_.removeAll();
      for (final String name : names) {
         Action addAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
               // This will fire change events, so we need not directly update
               // our pipeline display.
               engine_.makeProcessor(name, gui_);
            }
         };
         addAction.putValue(Action.NAME, name);
         addProcessorPopup_.add(new JMenuItem(addAction));
      }
   }

   private void removeSelectedProcessor() {
      DataProcessor<TaggedImage> processor
            = pipelineTable_.getSelectedProcessor();
      gui_.removeImageProcessor(processor);
   }

   private void moveSelectedProcessor(int offset) {
      int i = pipelineTable_.getSelectedRow();
      moveProcessor(pipelineTable_.getSelectedProcessor(), offset);
      // Retain the selection
      pipelineTable_.getSelectionModel().
            setSelectionInterval(i + offset, i + offset);
   }

   private void moveProcessor(DataProcessor<TaggedImage> processor,
         int offset) {
      List<DataProcessor<TaggedImage>> pipeline
            = gui_.getImageProcessorPipeline();
      int oldIndex = pipeline.indexOf(processor);
      if (oldIndex < 0) {
         return;
      }

      int newIndex = oldIndex + offset;
      newIndex = Math.max(0, newIndex);
      newIndex = Math.min(newIndex, pipeline.size() - 1);
      pipeline.remove(oldIndex);
      pipeline.add(newIndex, processor);

      gui_.setImageProcessorPipeline(pipeline);
   }
}

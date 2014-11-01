package org.micromanager.pipelineinterface;

import com.google.common.eventbus.Subscribe;
import com.swtdesigner.SwingResourceManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.ScriptInterface;
import org.micromanager.events.EventManager;
import org.micromanager.events.PipelineEvent;
import org.micromanager.events.ProcessorEvent;

final public class PipelineFrame extends JFrame {

   private final ScriptInterface gui_;
   private final AcquisitionEngine engine_;

   private final JPopupMenu addProcessorPopup_;

   // Panel that holds all the ProcessorPanels for instanced DataProcessors.
   private final JPanel processorsPanel_;
   // Scrolling view of the above panel.
   private final JScrollPane processorsScroller_;
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
            "[align center]unrelated[align left]",
            "[][align top, grow][]"));

      //
      // First column of the layout
      //
      final String downwardsArrow = "<html><b>\u2193</b></html>";
      contentPanel.add(new JLabel("<html><b>Camera</b></html>"), "split 2");
      contentPanel.add(new JLabel(downwardsArrow));

      // Set up the panel that holds the current active pipeline
      processorsPanel_ = new JPanel(new MigLayout(
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
         @Override public void mousePressed(MouseEvent e) {
            Component button = e.getComponent();
            addProcessorPopup_.show(button, 0, button.getHeight());
         }
      });
      contentPanel.add(addButton, "sizegroup btns, skip 1, split 5");

      removeButton_ = new JButton("Remove");
      removeButton_.setIcon(SwingResourceManager.getIcon(MMStudio.class,
            "/org/micromanager/icons/minus.png"));
      removeButton_.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            // remove selected
         }
      });
      contentPanel.add(removeButton_, "sizegroup btns");

      moveUpButton_ = new JButton("Move Up");
      moveUpButton_.setIcon(SwingResourceManager.getIcon(MMStudio.class,
            "/org/micromanager/icons/arrow_up.png"));
      moveUpButton_.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            // move up selected
         }
      });
      contentPanel.add(moveUpButton_, "sizegroup btns");

      moveDownButton_ = new JButton("Move Down");
      moveDownButton_.setIcon(SwingResourceManager.getIcon(MMStudio.class,
            "/org/micromanager/icons/arrow_down.png"));
      moveDownButton_.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            // move down selected
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
      setLayout(new MigLayout("align left, filly, insets 0"));
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
         @Override
         public void componentResized(ComponentEvent e) {
            Dimension size = getSize();
            size.width = Math.max(size.width, minFrameSize.width);
            size.width = Math.min(size.width, maxFrameSize.width);
            size.height = Math.max(size.height, minFrameSize.height);
            size.height = Math.min(size.height, maxFrameSize.height);
            setSize(size);
         }

         @Override
         public void componentMoved(ComponentEvent e) {
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

   /**
    * Re-generate our list of active processors.
    */
   private void reloadPipeline(List<DataProcessor<TaggedImage>> pipeline) {
      processorsPanel_.removeAll();
      for (DataProcessor<TaggedImage> processor : pipeline) {
         @SuppressWarnings("unchecked")
         Class<? extends DataProcessor<TaggedImage>> procCls
               = (Class<? extends DataProcessor<TaggedImage>>) processor.getClass();

         String name = engine_.getNameForProcessorClass(procCls);
         ProcessorPanel panel = new ProcessorPanel(name, processor, gui_);
         processorsPanel_.add(panel);
      }
      processorsPanel_.validate();
      processorsPanel_.repaint();
      validate();
   }
}

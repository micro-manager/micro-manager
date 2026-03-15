package org.micromanager.explorer;

import java.awt.Toolkit;
import java.io.File;
import java.net.URL;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Simple dialog for the Explorer plugin.
 * Lets the user configure a temp path and tile overlap, then start/open/stop an explore session.
 */
public class ExplorerFrame extends JFrame {

   private static final int DEFAULT_WIN_X = 100;
   private static final int DEFAULT_WIN_Y = 100;
   private static final String DIALOG_TITLE = "Explorer";

   static final String EXPLORE_TMP_PATH = "ExploreTmpPath";
   static final String EXPLORE_OVERLAP_PERCENT = "ExploreOverlapPercent";

   private final Studio studio_;
   private final MutablePropertyMapView settings_;
   private final ExplorerManager explorerManager_;

   private JButton stopButton_;

   public org.micromanager.PropertyMap getSettings() {
      return settings_.toPropertyMap();
   }

   public ExplorerFrame(Studio studio) {
      studio_ = studio;
      settings_ = studio_.profile().getSettings(this.getClass());
      explorerManager_ = new ExplorerManager(studio, this);

      initComponents();

      super.setLocation(DEFAULT_WIN_X, DEFAULT_WIN_Y);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
   }

   private void initComponents() {
      super.setTitle(DIALOG_TITLE);
      URL iconUrl = getClass().getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
      }
      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setLayout(new MigLayout("fillx", "[grow, fill][]"));

      add(new JLabel("Tmp Path:"), "split 3");
      JTextField tmpPathField = new JTextField(25);
      tmpPathField.setToolTipText(
            "Directory for temporary explore data (uses system temp dir if empty)");
      tmpPathField.setText(settings_.getString(EXPLORE_TMP_PATH,
            System.getProperty("java.io.tmpdir")));
      tmpPathField.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void insertUpdate(DocumentEvent e) {
            settings_.putString(EXPLORE_TMP_PATH, tmpPathField.getText());
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            settings_.putString(EXPLORE_TMP_PATH, tmpPathField.getText());
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            settings_.putString(EXPLORE_TMP_PATH, tmpPathField.getText());
         }
      });
      add(tmpPathField, "growx");

      JButton browseButton = new JButton("...");
      browseButton.setToolTipText("Browse for a temporary storage directory");
      browseButton.addActionListener(e -> {
         JFileChooser chooser = new JFileChooser();
         chooser.setDialogTitle("Select temporary storage directory");
         chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
         String current = tmpPathField.getText().trim();
         if (!current.isEmpty()) {
            chooser.setCurrentDirectory(new File(current));
         }
         if (chooser.showOpenDialog(ExplorerFrame.this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            tmpPathField.setText(path);
            settings_.putString(EXPLORE_TMP_PATH, path);
         }
      });
      add(browseButton, "wrap");

      add(new JLabel("Overlap (%):"), "split 2");
      SpinnerNumberModel overlapModel = new SpinnerNumberModel(10, 0, 50, 5);
      JSpinner overlapSpinner = new JSpinner(overlapModel);
      overlapSpinner.setToolTipText(
            "Percentage overlap between adjacent tiles (0-50%).");
      overlapSpinner.setValue(settings_.getInteger(EXPLORE_OVERLAP_PERCENT, 10));
      overlapSpinner.addChangeListener(e ->
            settings_.putInteger(EXPLORE_OVERLAP_PERCENT, (Integer) overlapSpinner.getValue()));
      add(overlapSpinner, "wrap");

      JButton openButton = new JButton("Open Existing");
      openButton.setToolTipText("Open a previously saved Explorer dataset.");
      openButton.addActionListener(e -> openExplore());
      add(openButton, "split 4");

      JButton startButton = new JButton("Start");
      startButton.setToolTipText(
            "Start explore mode. Right-click to select tiles, left-click to acquire.");
      startButton.addActionListener(e -> explorerManager_.startExplore());
      add(startButton);

      stopButton_ = new JButton("Interrupt");
      stopButton_.setToolTipText("Interrupt tile acquisition after the current tile finishes.");
      stopButton_.setEnabled(false);
      stopButton_.addActionListener(e -> explorerManager_.interruptAcquisition());
      add(stopButton_);

      JButton helpButton = new JButton("Help");
      helpButton.addActionListener(e -> JOptionPane.showMessageDialog(
            this,
            "Navigation:\n"
                  + "  Right-drag: pan view\n"
                  + "  Scroll wheel: zoom in/out\n"
                  + "\n"
                  + "Tile selection (live explore):\n"
                  + "  Right-click: select tile\n"
                  + "  Left-drag: expand selection\n"
                  + "  Left-click: acquire (or queue) selected tiles\n"
                  + "  Stop: stop all queued and running acquisitions\n"
                  + "  Ctrl+left-click: move stage to position\n"
                  + "\n"
                  + "Images pass through the active Data Processing Pipeline.\n"
                  + "Configure the pipeline in MM's Data Processing Pipeline window.",
            "Explorer Help", JOptionPane.PLAIN_MESSAGE));
      add(helpButton, "wrap");

      pack();
   }

   private void openExplore() {
      File result = FileDialogs.openDir(ExplorerFrame.this,
              "Select Explorer Dataset",
              FileDialogs.MM_DATA_SET);
      if (result != null) {
         File parent = result.getParentFile();
         if (parent != null) {
            FileDialogs.storePath(FileDialogs.MM_DATA_SET, parent);
         }
         explorerManager_.openExplore(result.getAbsolutePath());
      }
   }

   /**
    * Enables or disables the Stop button.
    * Called from ExplorerManager; switches to EDT.
    */
   public void setAcquisitionInProgress(boolean inProgress) {
      SwingUtilities.invokeLater(() -> {
         if (stopButton_ != null) {
            stopButton_.setEnabled(inProgress);
         }
      });
   }
}

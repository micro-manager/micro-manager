package org.micromanager.explorer;

import java.awt.Toolkit;
import java.net.URL;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.positionlist.utils.ZGenerator;
import org.micromanager.internal.utils.WindowPositioning;

/**
 * Stand-alone window for Refine Z. Opened from the Explorer dialog's "Refine Z..." button so the
 * Explorer dialog stays compact. Lets the operator measure Z at reference points (Automatic via
 * autofocus, or Manual by focusing) and pick the interpolation method; the measured surface is
 * baked into the generated positions when "Add to Position List" is pressed in the Explorer dialog.
 *
 * <p>All actions delegate to {@link ExplorerManager}; the manager pushes status and running state
 * back here. Manual mode arms the Explorer canvas so a ctrl+left-click navigates to a tile.</p>
 */
public class RefineZFrame extends JFrame {

   private final Studio studio_;
   private final ExplorerManager explorerManager_;
   private final ExplorerFrame explorerFrame_;

   private final JRadioButton autoRadio_;
   private final JRadioButton manualRadio_;
   private final JComboBox<ZGenerator.Type> interpCombo_;
   private final JComboBox<String> afCombo_;
   private final JSpinner pointsSpinner_;
   private final JButton startButton_;
   private final JButton cancelButton_;
   private final JButton setButton_;
   private final JButton clearButton_;
   private final JButton addButton_;
   private final JButton closeButton_;
   private final JLabel statusLabel_;

   private boolean running_ = false;

   /**
    * Constructs the Refine Z window.
    *
    * @param studio the Studio
    * @param explorerManager the manager that performs the refinement
    * @param explorerFrame the owning Explorer dialog (read for the within-vessel option)
    */
   public RefineZFrame(Studio studio, ExplorerManager explorerManager,
                       ExplorerFrame explorerFrame) {
      studio_ = studio;
      explorerManager_ = explorerManager;
      explorerFrame_ = explorerFrame;

      setTitle("Refine Z");
      URL iconUrl = getClass().getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
      }
      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setLayout(new MigLayout("fillx", "[grow,fill]"));

      JPanel panel = new JPanel(new MigLayout("insets 6", "[]8[]", "[]6[]"));

      panel.add(new JLabel("Interpolation:"), "split 2");
      interpCombo_ = new JComboBox<>(new ZGenerator.Type[] {
            ZGenerator.Type.SHEPINTERPOLATE, ZGenerator.Type.AVERAGE});
      interpCombo_.setToolTipText("Z interpolation method");
      interpCombo_.addActionListener(e ->
            explorerManager_.setRefineZMethod((ZGenerator.Type) interpCombo_.getSelectedItem()));
      panel.add(interpCombo_, "wrap");

      autoRadio_ = new JRadioButton("Automatic", true);
      manualRadio_ = new JRadioButton("Manual", false);
      ButtonGroup modeGroup = new ButtonGroup();
      modeGroup.add(autoRadio_);
      modeGroup.add(manualRadio_);
      // The canvas is armed for refinement whenever this window is open (both modes use
      // Ctrl-click navigation + Set Z), so the mode radios only toggle which controls are live.
      autoRadio_.addActionListener(e -> updateEnabled());
      manualRadio_.addActionListener(e -> updateEnabled());
      panel.add(autoRadio_, "split 2");
      panel.add(manualRadio_, "wrap");

      panel.add(new JLabel("Points:"), "split 4");
      pointsSpinner_ = new JSpinner(new SpinnerNumberModel(5, 1, 999, 1));
      pointsSpinner_.setToolTipText("Number of autofocus reference points");
      panel.add(pointsSpinner_);
      afCombo_ = new JComboBox<>();
      afCombo_.setToolTipText("Autofocus method to run at each reference point");
      panel.add(afCombo_);
      startButton_ = new JButton("Start");
      startButton_.addActionListener(e -> {
         Object af = afCombo_.getSelectedItem();
         explorerManager_.startRefineZAutomatic(
               (Integer) pointsSpinner_.getValue(),
               af != null ? af.toString() : null,
               explorerFrame_.isWithinVesselSelected());
      });
      panel.add(startButton_, "wrap");

      cancelButton_ = new JButton("Cancel");
      cancelButton_.setToolTipText("Cancel the running automatic Refine Z");
      cancelButton_.addActionListener(e -> explorerManager_.cancelRefineZ());
      panel.add(cancelButton_, "split 3");
      setButton_ = new JButton("Set Z");
      setButton_.setToolTipText(
            "Manual mode: Ctrl-click a tile, focus, then click Set Z to record it.");
      setButton_.addActionListener(e -> explorerManager_.addManualRefineZ());
      panel.add(setButton_);
      clearButton_ = new JButton("Clear Z");
      clearButton_.setToolTipText("Discard all Refine-Z reference points");
      clearButton_.addActionListener(e -> explorerManager_.clearRefineZ());
      panel.add(clearButton_, "wrap");

      addButton_ = new JButton("Add to Position List");
      addButton_.setToolTipText(
            "Bake the interpolated Z into the positions, add them to the MM position list, "
            + "and close this window.");
      addButton_.addActionListener(e -> {
         explorerManager_.createPositionsFromRoi(explorerFrame_.isWithinVesselSelected());
         dispose();
      });
      panel.add(addButton_, "split 2");
      closeButton_ = new JButton("Close");
      closeButton_.setToolTipText("Close this window without adding positions.");
      closeButton_.addActionListener(e -> dispose());
      panel.add(closeButton_, "wrap");

      statusLabel_ = new JLabel(" ");
      panel.add(statusLabel_, "span, growx, wrap");

      add(panel, "growx, wrap");

      // Populate autofocus methods and select the current interpolation method.
      refreshAfMethods();
      explorerManager_.setRefineZMethod((ZGenerator.Type) interpCombo_.getSelectedItem());

      pack();
      setLocationRelativeTo(explorerFrame);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
      updateEnabled();
   }

   private void refreshAfMethods() {
      Object selected = afCombo_.getSelectedItem();
      afCombo_.removeAllItems();
      try {
         for (String name : studio_.getAutofocusManager().getAllAutofocusMethods()) {
            afCombo_.addItem(name);
         }
      } catch (Exception e) {
         // No autofocus methods available; combo stays empty.
      }
      if (selected != null) {
         afCombo_.setSelectedItem(selected);
      }
   }

   /** Notifies whether an automatic Refine-Z run is in progress; switches to EDT. */
   public void setRefineZRunning(boolean running) {
      SwingUtilities.invokeLater(() -> {
         running_ = running;
         updateEnabled();
      });
   }

   /** Sets the status text shown at the bottom of the window; switches to EDT. */
   public void setRefineZStatus(String text) {
      SwingUtilities.invokeLater(() ->
            statusLabel_.setText(text == null || text.isEmpty() ? " " : text));
   }

   private void updateEnabled() {
      final boolean auto = autoRadio_.isSelected();
      final boolean manual = manualRadio_.isSelected();
      interpCombo_.setEnabled(!running_);
      autoRadio_.setEnabled(!running_);
      manualRadio_.setEnabled(!running_);
      pointsSpinner_.setEnabled(auto && !running_);
      afCombo_.setEnabled(auto && !running_);
      startButton_.setEnabled(auto && !running_);
      cancelButton_.setEnabled(auto && running_);
      setButton_.setEnabled(manual && !running_);
      clearButton_.setEnabled(!running_);
      addButton_.setEnabled(!running_);
      closeButton_.setEnabled(!running_);
   }

   @Override
   public void dispose() {
      // Closing disarms the canvas (re-enables ROI drawing). The manager keeps any collected
      // points until the Explorer draw session ends, so re-opening preserves the surface.
      explorerManager_.cancelRefineZ();
      explorerManager_.onRefineZFrameClosed(this);
      super.dispose();
   }
}

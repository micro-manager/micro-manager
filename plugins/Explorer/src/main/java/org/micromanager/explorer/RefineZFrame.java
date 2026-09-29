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
import org.micromanager.propertymap.MutablePropertyMapView;

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

   private static final String EDGE_MARGIN = "RefineZEdgeMargin";
   private static final String AF_METHOD = "RefineZAutofocusMethod";

   private final Studio studio_;
   private final ExplorerManager explorerManager_;
   private final ExplorerFrame explorerFrame_;
   private final MutablePropertyMapView settings_;

   private final JRadioButton autoRadio_;
   private final JRadioButton manualRadio_;
   private final JComboBox<ZGenerator.Type> interpCombo_;
   private final JComboBox<String> afCombo_;
   private final JSpinner pointsSpinner_;
   private final JSpinner edgeMarginSpinner_;
   private final JButton startButton_;
   private final JButton cancelButton_;
   private final JButton setButton_;
   private final JButton clearButton_;
   private final JButton addButton_;
   private final JButton closeButton_;
   private final JLabel statusLabel_;

   private boolean running_ = false;
   // True while we are programmatically syncing the AF combo to the main method, to avoid the
   // combo's action listener echoing the change back to the AutofocusManager.
   private boolean syncingAf_ = false;

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
      settings_ = studio_.profile().getSettings(RefineZFrame.class);

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
      afCombo_.setToolTipText(
            "Autofocus method to run at each reference point (shared with the main MM window)");
      // Selecting a method here switches the main MM autofocus method too, keeping them in sync.
      afCombo_.addActionListener(e -> {
         Object sel = afCombo_.getSelectedItem();
         if (sel != null && !syncingAf_) {
            settings_.putString(AF_METHOD, sel.toString());
            try {
               studio_.getAutofocusManager().setAutofocusMethodByName(sel.toString());
            } catch (Exception ex) {
               studio_.logs().logError(ex, "Refine Z: could not select autofocus method");
            }
         }
      });
      panel.add(afCombo_);
      // Created before the Start button so its action listener can read it (final field).
      edgeMarginSpinner_ = new JSpinner(new SpinnerNumberModel(
            settings_.getInteger(EDGE_MARGIN, 0), 0, 99, 1));
      edgeMarginSpinner_.setToolTipText(
            "Exclude reference points within this many tiles of the grid edge. "
            + "Outer tiles often only partially overlap the sample and can fail autofocus. "
            + "0 = use all tiles.");
      edgeMarginSpinner_.addChangeListener(e ->
            settings_.putInteger(EDGE_MARGIN, (Integer) edgeMarginSpinner_.getValue()));
      startButton_ = new JButton("Start");
      startButton_.addActionListener(e -> {
         Object af = afCombo_.getSelectedItem();
         explorerManager_.startRefineZAutomatic(
               (Integer) pointsSpinner_.getValue(),
               af != null ? af.toString() : null,
               explorerFrame_.isWithinVesselSelected(),
               (Integer) edgeMarginSpinner_.getValue());
      });
      panel.add(startButton_, "wrap");

      panel.add(new JLabel("Exclude edge margin (tiles):"), "split 2");
      panel.add(edgeMarginSpinner_, "wrap");

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

      // Populate autofocus methods. Restore the method saved in the profile when it is still
      // available, applying it to the main MM window so the two stay in sync; otherwise fall back
      // to whatever method MM currently has active.
      refreshAfMethods();
      restoreSavedAfMethod();
      explorerManager_.setRefineZMethod((ZGenerator.Type) interpCombo_.getSelectedItem());

      // Re-sync the AF method from the main window each time this window regains focus, since the
      // AutofocusManager has no change event to subscribe to.
      addWindowFocusListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowGainedFocus(java.awt.event.WindowEvent e) {
            refreshAfMethods();
         }
      });

      pack();
      setLocationRelativeTo(explorerFrame);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
      updateEnabled();
   }

   /**
    * Repopulates the autofocus list and selects the method currently active in the main MM window,
    * so the Refine Z device stays in sync with the rest of the application.
    */
   private void refreshAfMethods() {
      syncingAf_ = true;
      try {
         afCombo_.removeAllItems();
         for (String name : studio_.getAutofocusManager().getAllAutofocusMethods()) {
            afCombo_.addItem(name);
         }
         org.micromanager.AutofocusPlugin current =
               studio_.getAutofocusManager().getAutofocusMethod();
         if (current != null) {
            afCombo_.setSelectedItem(current.getName());
         }
      } catch (Exception e) {
         // No autofocus methods available; combo stays empty.
      } finally {
         syncingAf_ = false;
      }
   }

   /**
    * Selects the autofocus method remembered in the profile (if present and still installed) and
    * pushes it to the main MM window. Called once at construction so a remembered choice wins over
    * MM's current method; later focus-driven {@link #refreshAfMethods()} calls follow MM.
    */
   private void restoreSavedAfMethod() {
      String saved = settings_.getString(AF_METHOD, null);
      if (saved == null || saved.isEmpty()) {
         return;
      }
      for (int i = 0; i < afCombo_.getItemCount(); i++) {
         if (saved.equals(afCombo_.getItemAt(i))) {
            // Not syncing: we want this selection to propagate to the AutofocusManager.
            afCombo_.setSelectedItem(saved);
            return;
         }
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
      edgeMarginSpinner_.setEnabled(auto && !running_);
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

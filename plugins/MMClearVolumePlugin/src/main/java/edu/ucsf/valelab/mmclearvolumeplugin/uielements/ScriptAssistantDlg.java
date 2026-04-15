package edu.ucsf.valelab.mmclearvolumeplugin.uielements;

import com.jogamp.opengl.math.Quaternion;
import edu.ucsf.valelab.mmclearvolumeplugin.CVViewer;
import edu.ucsf.valelab.mmclearvolumeplugin.recorder.AnimationFrameRecorder;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;
import org.micromanager.display.DisplaySettings;

/**
 * Interactive keyframe editor that helps users build 3D animation scripts.
 *
 * <p>The user adjusts the ClearVolume viewer (rotation, clipping, channel
 * settings, time point) and clicks "Add Keyframe" to record the current
 * display state with a thumbnail. After setting all desired keyframes, the
 * user clicks "Generate Script" to produce a 3DScript-language text block
 * that transitions between the keyframes. The text is passed to the
 * {@code scriptConsumer} provided at construction (typically used to insert
 * it at the cursor in the parent {@link AnimationScriptDlg}).
 */
public final class ScriptAssistantDlg extends JDialog {

   // -----------------------------------------------------------------------
   // Constants
   // -----------------------------------------------------------------------

   private static final int THUMB_W = 120;
   private static final int THUMB_H = 90;
   private static final float ROT_THRESHOLD_DEG = 0.5f;
   private static final float TRANS_THRESHOLD = 1e-4f;
   private static final float CLIP_THRESHOLD = 0.001f;
   private static final double CH_THRESHOLD = 0.001;

   private static final String EASING_LINEAR = "linear";

   // -----------------------------------------------------------------------
   // Data model
   // -----------------------------------------------------------------------

   /**
    * Immutable snapshot of all animated display properties at one keyframe.
    * {@code thumbnail} is set asynchronously after construction.
    */
   static final class Keyframe {
      final CVViewer.ViewerState state;
      final int timePoint;           // 0-based
      final Color[] channelColors;   // per-channel, may contain nulls
      BufferedImage thumbnail;       // set on EDT after async capture
      int frameNumber;               // 1-based, editable by user

      Keyframe(CVViewer.ViewerState state, int timePoint,
               Color[] channelColors, int frameNumber) {
         this.state = state;
         this.timePoint = timePoint;
         this.channelColors = channelColors;
         this.frameNumber = frameNumber;
      }
   }

   // -----------------------------------------------------------------------
   // UI fields
   // -----------------------------------------------------------------------

   private final JPanel reelPanel_;
   private final JScrollPane reelScroll_;
   private final JComboBox<String> easingCombo_;
   private final JCheckBox includeColorsCheck_;
   private final JButton addKfButton_;
   private final JButton removeSelectedButton_;
   private final JButton generateButton_;
   private final JTextField statusLabel_;

   // -----------------------------------------------------------------------
   // State
   // -----------------------------------------------------------------------

   private final CVViewer viewer_;
   private final Consumer<String> scriptConsumer_;
   private final List<Keyframe> keyframes_ = new ArrayList<Keyframe>();

   /** 0-based index of the currently selected card, or −1 if none selected. */
   private int selectedIndex_ = -1;

   // -----------------------------------------------------------------------
   // Constructor
   // -----------------------------------------------------------------------

   /**
    * Creates and shows the Script Reel dialog.
    *
    * @param viewer         the ClearVolume viewer to observe
    * @param scriptConsumer called on the EDT with the generated script text
    *                       when the user clicks "Generate Script"
    */
   public ScriptAssistantDlg(CVViewer viewer, Consumer<String> scriptConsumer) {
      super((java.awt.Frame) null, "Script Reel - " + viewer.getName(), false);
      viewer_ = viewer;
      scriptConsumer_ = scriptConsumer;

      java.net.URL iconUrl = getClass()
            .getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
      }

      // Use BorderLayout for the outer panel so the reel fills the center and
      // the controls are pinned to the bottom — avoids MigLayout flowy sizing issues.
      final JPanel panel = new JPanel(new java.awt.BorderLayout(0, 6));
      panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

      // ---- Top: options row ----
      JPanel optionsRow = new JPanel(new MigLayout("insets 0, fillx"));
      easingCombo_ = new JComboBox<>(new String[]{
            "ease-in-out", "linear", "ease-in", "ease-out", "ease"});
      includeColorsCheck_ = new JCheckBox("Include channel colors", false);
      optionsRow.add(new JLabel("Easing:"), "");
      optionsRow.add(easingCombo_, "");
      optionsRow.add(includeColorsCheck_, "gapleft 12");
      panel.add(optionsRow, java.awt.BorderLayout.NORTH);

      // ---- Center: reel ----
      // Use a plain FlowLayout so cards never wrap and the scroll pane handles overflow.
      reelPanel_ = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
      reelPanel_.setBackground(UIManager.getColor("Panel.background"));
      reelScroll_ = new JScrollPane(reelPanel_,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      // Height: thumbnail + frame-number row + KF-label row + card insets/border.
      reelScroll_.setPreferredSize(new java.awt.Dimension(660, THUMB_H + 72));
      reelScroll_.setBorder(BorderFactory.createEtchedBorder());
      panel.add(reelScroll_, java.awt.BorderLayout.CENTER);

      // ---- South: buttons + status bar ----
      final JPanel southPanel = new JPanel(new MigLayout("insets 0, fillx, flowy"));

      addKfButton_ = new JButton("Add Keyframe");
      addKfButton_.setToolTipText(
            "Capture the current viewer state as the next keyframe");
      removeSelectedButton_ = new JButton("Remove Selected");
      removeSelectedButton_.setToolTipText(
            "Remove the selected keyframe from the reel");
      generateButton_ = new JButton("Generate Script");
      generateButton_.setToolTipText(
            "Generate a script block transitioning between the captured keyframes");
      final JButton cancelButton = new JButton("Cancel");

      addKfButton_.addActionListener((ActionEvent e) -> captureKeyframe());
      removeSelectedButton_.addActionListener((ActionEvent e) -> removeSelected());
      generateButton_.addActionListener((ActionEvent e) -> doGenerate());
      cancelButton.addActionListener((ActionEvent e) -> dispose());

      southPanel.add(addKfButton_, "split 4, flowx, align right");
      southPanel.add(removeSelectedButton_, "");
      southPanel.add(generateButton_, "");
      southPanel.add(cancelButton, "wrap");

      statusLabel_ = new JTextField(" ");
      statusLabel_.setEditable(false);
      statusLabel_.setBorder(BorderFactory.createEmptyBorder());
      statusLabel_.setBackground(UIManager.getColor("Panel.background"));
      southPanel.add(statusLabel_, "growx");

      panel.add(southPanel, java.awt.BorderLayout.SOUTH);

      getContentPane().add(panel, java.awt.BorderLayout.CENTER);
      pack();
      setLocationRelativeTo(null);

      updateButtonStates();

      setVisible(true);

      // Auto-capture the first keyframe immediately.
      captureKeyframe();
   }

   // -----------------------------------------------------------------------
   // Keyframe capture
   // -----------------------------------------------------------------------

   private void captureKeyframe() {
      addKfButton_.setEnabled(false);
      statusLabel_.setText("Capturing...");

      // Compute the next frame number on the EDT before spawning the thread,
      // so we read keyframes_ only from the EDT.
      final int nextFrameNum = keyframes_.isEmpty() ? 1
            : keyframes_.get(keyframes_.size() - 1).frameNumber + 30;

      Thread t = new Thread(() -> {
         final CVViewer.ViewerState state = viewer_.snapshotState();
         if (state == null) {
            SwingUtilities.invokeLater(() -> {
               statusLabel_.setText("Viewer not ready - cannot capture.");
               addKfButton_.setEnabled(true);
            });
            return;
         }
         final int tp = viewer_.getCurrentTimePoint();
         final Color[] colors = captureChannelColors(state.layerVisible.length);

         BufferedImage raw = null;
         AnimationFrameRecorder recorder = new AnimationFrameRecorder();
         try {
            viewer_.setAnimationRecorder(recorder);
            recorder.arm();
            viewer_.addTranslationZ(0f); // force redraw
            if (recorder.await(3, TimeUnit.SECONDS)) {
               raw = recorder.getLastFrame();
            }
         } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
         } finally {
            viewer_.setAnimationRecorder(null);
         }

         final BufferedImage thumb = scaleThumbnail(raw, THUMB_W, THUMB_H);
         final Keyframe kf = new Keyframe(state, tp, colors, nextFrameNum);
         kf.thumbnail = thumb;

         SwingUtilities.invokeLater(() -> {
            keyframes_.add(kf);
            addCardToReel(kf, keyframes_.size());
            reelPanel_.revalidate();
            reelPanel_.repaint();
            // Select the newly added card.
            setSelectedIndex(keyframes_.size() - 1);
            // Scroll to the rightmost card.
            SwingUtilities.invokeLater(() ->
                  reelScroll_.getHorizontalScrollBar().setValue(
                        reelScroll_.getHorizontalScrollBar().getMaximum()));
            statusLabel_.setText("Keyframe " + keyframes_.size() + " added.");
            addKfButton_.setEnabled(true);
         });
      }, "CV-ScriptAssistant-Capture");
      t.setDaemon(true);
      t.start();
   }

   private Color[] captureChannelColors(int nCh) {
      Color[] colors = new Color[nCh];
      try {
         DisplaySettings ds = viewer_.getDisplaySettings();
         if (ds != null) {
            for (int ch = 0; ch < nCh; ch++) {
               try {
                  colors[ch] = ds.getChannelColor(ch);
               } catch (Exception ex) {
                  // channel index out of range — leave null
               }
            }
         }
      } catch (Exception ex) {
         // displaySettings not available
      }
      return colors;
   }

   // -----------------------------------------------------------------------
   // Reel card UI
   // -----------------------------------------------------------------------

   private void addCardToReel(Keyframe kf, int index) {
      // The card's position in the reel is index-1 (0-based).
      final int cardIndex = index - 1;
      KeyframeCard card = new KeyframeCard(kf, index,
            () -> setSelectedIndex(cardIndex));
      reelPanel_.add(card);
   }

   /**
    * Panel representing one keyframe in the reel strip.
    */
   private static final class KeyframeCard extends JPanel {
      private static final java.awt.Color SELECTED_COLOR =
            UIManager.getColor("List.selectionBackground") != null
                  ? UIManager.getColor("List.selectionBackground")
                  : new java.awt.Color(0x3875D7);

      private final Keyframe kf_;

      KeyframeCard(Keyframe kf, int index, Runnable onSelect) {
         super(new MigLayout("insets 4, flowy, alignx center"));
         kf_ = kf;
         setBorder(BorderFactory.createEtchedBorder());
         setBackground(UIManager.getColor("Panel.background"));

         // Thumbnail.
         JLabel imgLabel = new JLabel(new ImageIcon(kf.thumbnail));
         imgLabel.setPreferredSize(new java.awt.Dimension(THUMB_W, THUMB_H));
         add(imgLabel, "");

         // Frame-number field.
         JTextField frameField = new JTextField(
               String.valueOf(kf.frameNumber), 4);
         frameField.setHorizontalAlignment(JTextField.RIGHT);
         frameField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
               validateFrameField(frameField);
            }
         });
         frameField.addActionListener((ActionEvent e) ->
               validateFrameField(frameField));
         add(new JLabel("Frame:"), "split 2, flowx");
         add(frameField, "");

         // Index label.
         add(new JLabel("KF " + index), "alignx center");

         // Clicking anywhere on the card (or its children) selects it.
         MouseAdapter selectOnClick = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
               onSelect.run();
            }
         };
         addMouseListener(selectOnClick);
         imgLabel.addMouseListener(selectOnClick);
         // Frame field gets focus normally; clicking it also selects the card.
         frameField.addMouseListener(selectOnClick);
      }

      /** Highlights or un-highlights this card to reflect selection state. */
      void setSelected(boolean selected) {
         setBorder(selected
               ? BorderFactory.createLineBorder(SELECTED_COLOR, 2)
               : BorderFactory.createEtchedBorder());
      }

      private void validateFrameField(JTextField field) {
         try {
            int v = Integer.parseInt(field.getText().trim());
            if (v < 1) {
               throw new NumberFormatException("must be positive");
            }
            kf_.frameNumber = v;
         } catch (NumberFormatException ex) {
            field.setText(String.valueOf(kf_.frameNumber));
         }
      }
   }

   // -----------------------------------------------------------------------
   // Selection
   // -----------------------------------------------------------------------

   /**
    * Sets the selected card index and repaints the reel to reflect the new
    * selection highlight.  Pass −1 to deselect all.
    */
   private void setSelectedIndex(int index) {
      selectedIndex_ = index;
      // Update border on every card to reflect the new selection state.
      java.awt.Component[] cards = reelPanel_.getComponents();
      for (int i = 0; i < cards.length; i++) {
         if (cards[i] instanceof KeyframeCard) {
            ((KeyframeCard) cards[i]).setSelected(i == index);
         }
      }
      updateButtonStates();
   }

   // -----------------------------------------------------------------------
   // Remove selected
   // -----------------------------------------------------------------------

   private void removeSelected() {
      if (selectedIndex_ < 0 || selectedIndex_ >= keyframes_.size()) {
         return;
      }
      int removedIndex = selectedIndex_;
      keyframes_.remove(removedIndex);
      // After removal select the card just before the removed one (or none).
      int newSelection = keyframes_.isEmpty() ? -1
            : Math.max(0, removedIndex - 1);
      selectedIndex_ = -1; // clear before rebuild so setSelectedIndex works cleanly
      // Rebuild the reel (simpler than tracking individual card components).
      reelPanel_.removeAll();
      for (int i = 0; i < keyframes_.size(); i++) {
         addCardToReel(keyframes_.get(i), i + 1);
      }
      reelPanel_.revalidate();
      reelPanel_.repaint();
      setSelectedIndex(newSelection);
      statusLabel_.setText(keyframes_.isEmpty()
            ? "All keyframes removed."
            : "Keyframe " + (removedIndex + 1) + " removed. "
                  + keyframes_.size() + " keyframe(s) remain.");
   }

   // -----------------------------------------------------------------------
   // Generate script
   // -----------------------------------------------------------------------

   private void doGenerate() {
      // Warn if frame numbers are not strictly increasing.
      for (int i = 0; i < keyframes_.size() - 1; i++) {
         if (keyframes_.get(i).frameNumber >= keyframes_.get(i + 1).frameNumber) {
            statusLabel_.setText(
                  "Warning: frame numbers not strictly increasing - script may be incorrect.");
            break;
         }
      }
      String script = generateScript();
      if (!script.isEmpty()) {
         scriptConsumer_.accept(script);
      }
      dispose();
   }

   private String generateScript() {
      if (keyframes_.size() < 2) {
         return "";
      }
      String easing = (String) easingCombo_.getSelectedItem();
      String easingSuffix = EASING_LINEAR.equals(easing) ? "" : " " + easing;
      boolean includeColors = includeColorsCheck_.isSelected();

      StringBuilder sb = new StringBuilder();

      // Emit an absolute-state block at the first keyframe's frame number.
      // This pins all properties to their starting values so the animation is
      // reproducible regardless of the viewer state when the script is run.
      List<String> initLines = computeAbsoluteState(
            keyframes_.get(0), includeColors);
      if (!initLines.isEmpty()) {
         sb.append("At frame ").append(keyframes_.get(0).frameNumber).append(":\n");
         for (String line : initLines) {
            sb.append("- ").append(line).append("\n");
         }
         sb.append("\n");
      }

      // Emit transition blocks between consecutive keyframe pairs.
      for (int i = 0; i < keyframes_.size() - 1; i++) {
         Keyframe k0 = keyframes_.get(i);
         Keyframe k1 = keyframes_.get(i + 1);
         List<String> lines = computeDeltas(k0, k1, includeColors, easingSuffix);
         if (lines.isEmpty()) {
            continue;
         }
         if (k0.frameNumber == k1.frameNumber) {
            sb.append("At frame ").append(k0.frameNumber).append(":\n");
         } else {
            sb.append("From frame ").append(k0.frameNumber)
                  .append(" to frame ").append(k1.frameNumber).append(":\n");
         }
         for (String line : lines) {
            sb.append("- ").append(line).append("\n");
         }
         sb.append("\n");
      }
      return sb.toString();
   }

   /**
    * Generates a list of absolute-value instructions that fully describe the
    * state of {@code kf}. Unlike the delta instructions these set every
    * animated property to its exact value at that keyframe, so the animation
    * begins from a known, reproducible state.
    */
   private static List<String> computeAbsoluteState(Keyframe kf,
                                                     boolean includeColors) {
      List<String> out = new ArrayList<String>();
      CVViewer.ViewerState s = kf.state;

      // Emit "reset" first: restores the pre-animation baseline in the player
      // before any rotation/translation deltas are applied, making the init
      // block fully reproducible regardless of the viewer's current state.
      out.add("reset");

      // The script language expresses rotation and translation as *deltas*, not
      // absolute set. Because "reset" restores the known baseline first, the
      // deltas below land on a reproducible starting point.

      // Rotation: decompose the keyframe quaternion into axis-angle.
      // Q applied to the identity quaternion gives the full rotation to reach this state.
      Quaternion q = s.quaternion;
      if (q != null) {
         // Work on a copy; toAngleAxis does not mutate but normalize does.
         Quaternion qCopy = new Quaternion(q);
         qCopy.normalize();
         float[] axis = new float[3];
         float angleRad = qCopy.toAngleAxis(axis);
         float angleDeg = (float) Math.toDegrees(angleRad);
         if (Math.abs(angleDeg) >= ROT_THRESHOLD_DEG) {
            float ax = axis[0];
            float ay = axis[1];
            float az = axis[2];
            boolean isHorizontal =
                  Math.abs(ay) > 0.99f && Math.abs(ax) < 0.1f && Math.abs(az) < 0.1f;
            boolean isVertical =
                  Math.abs(ax) > 0.99f && Math.abs(ay) < 0.1f && Math.abs(az) < 0.1f;
            if (isHorizontal) {
               float deg = ay > 0 ? angleDeg : -angleDeg;
               out.add(String.format(Locale.US,
                     "rotate by %.2f degrees horizontally", deg));
            } else if (isVertical) {
               float deg = ax > 0 ? angleDeg : -angleDeg;
               out.add(String.format(Locale.US,
                     "rotate by %.2f degrees vertically", deg));
            } else {
               if (angleDeg < 0) {
                  angleDeg = -angleDeg;
                  ax = -ax;
                  ay = -ay;
                  az = -az;
               }
               out.add(String.format(Locale.US,
                     "rotate by %.2f degrees around %.4f %.4f %.4f",
                     angleDeg, ax, ay, az));
            }
         }
      }

      // Translation / zoom — emit as a single delta from origin (0,0,0).
      // Since the script player accumulates these as addTranslationX/Y/Z, emitting
      // the absolute values in an At-frame block snaps the viewer to the recorded
      // position when running from default state.
      float tx = s.translationX;
      float ty = s.translationY;
      float tz = s.translationZ;
      boolean hasX = Math.abs(tx) > TRANS_THRESHOLD;
      boolean hasY = Math.abs(ty) > TRANS_THRESHOLD;
      boolean hasZ = Math.abs(tz) > TRANS_THRESHOLD;
      if (hasX && !hasY && !hasZ) {
         out.add(String.format(Locale.US, "translate horizontally by %.4f", tx));
      } else if (!hasX && hasY && !hasZ) {
         out.add(String.format(Locale.US, "translate vertically by %.4f", ty));
      } else if (!hasX && !hasY && hasZ) {
         out.add(String.format(Locale.US, "zoom by %.4f", tz));
      } else if (hasX || hasY || hasZ) {
         out.add(String.format(Locale.US,
               "translate by %.4f %.4f %.4f", tx, ty, tz));
      }

      // Clipping planes — emit absolute target values.
      float[] clip = s.clipBox;
      if (clip != null && clip.length >= 6) {
         String[][] labels = {
               {"min", "x"}, {"max", "x"},
               {"min", "y"}, {"max", "y"}
         };
         for (int i = 0; i < 4; i++) {
            out.add(String.format(Locale.US,
                  "change bounding box %s %s to %.4f",
                  labels[i][0], labels[i][1], clip[i]));
         }
         out.add(String.format(Locale.US,
               "change front clipping to %.4f", clip[4]));
         out.add(String.format(Locale.US,
               "change back clipping to %.4f", clip[5]));
      }

      // Channel intensity, gamma, visibility (and optionally color).
      if (s.transferRangeMin != null) {
         int nCh = s.transferRangeMin.length;
         for (int ch = 0; ch < nCh; ch++) {
            double gamma = (s.gamma != null) ? s.gamma[ch] : 1.0;
            out.add(String.format(Locale.US,
                  "change channel %d intensity to %.4f %.4f %.3f",
                  ch + 1, s.transferRangeMin[ch], s.transferRangeMax[ch], gamma));
            if (s.layerVisible != null && ch < s.layerVisible.length) {
               out.add(String.format(Locale.US,
                     "change channel %d visibility to %s",
                     ch + 1, s.layerVisible[ch] ? "on" : "off"));
            }
            if (includeColors && kf.channelColors != null
                  && ch < kf.channelColors.length
                  && kf.channelColors[ch] != null) {
               Color c = kf.channelColors[ch];
               out.add(String.format(Locale.US,
                     "change channel %d color to %d %d %d",
                     ch + 1, c.getRed(), c.getGreen(), c.getBlue()));
            }
         }
      }

      // Time point (1-based in script language).
      out.add(String.format(Locale.US, "change time to %d", kf.timePoint + 1));

      return out;
   }

   // -----------------------------------------------------------------------
   // Delta computation
   // -----------------------------------------------------------------------

   private static List<String> computeDeltas(Keyframe k0, Keyframe k1,
                                             boolean includeColors,
                                             String easingSuffix) {
      List<String> lines = new ArrayList<String>();

      computeRotation(k0.state, k1.state, easingSuffix, lines);
      computeTranslation(k0.state, k1.state, easingSuffix, lines);
      computeClip(k0.state, k1.state, easingSuffix, lines);
      computeChannels(k0.state, k1.state, includeColors,
            k0.channelColors, k1.channelColors, easingSuffix, lines);
      computeTimePoint(k0.timePoint, k1.timePoint, lines);

      return lines;
   }

   private static void computeRotation(CVViewer.ViewerState s0,
                                       CVViewer.ViewerState s1,
                                       String easingSuffix,
                                       List<String> out) {
      Quaternion q0 = s0.quaternion;
      Quaternion q1 = s1.quaternion;
      if (q0 == null || q1 == null) {
         return;
      }
      // Q_delta = Q1 * conj(Q0)  — always work on copies; methods mutate in-place.
      Quaternion qDelta = new Quaternion(q1);
      qDelta.mult(new Quaternion(q0).conjugate());
      qDelta.normalize();

      float[] axis = new float[3];
      float angleRad = qDelta.toAngleAxis(axis);
      float angleDeg = (float) Math.toDegrees(angleRad);

      if (Math.abs(angleDeg) < ROT_THRESHOLD_DEG) {
         return;
      }

      float ax = axis[0];
      float ay = axis[1];
      float az = axis[2];

      boolean isHorizontal =
            Math.abs(ay) > 0.99f && Math.abs(ax) < 0.1f && Math.abs(az) < 0.1f;
      boolean isVertical =
            Math.abs(ax) > 0.99f && Math.abs(ay) < 0.1f && Math.abs(az) < 0.1f;

      if (isHorizontal) {
         float deg = ay > 0 ? angleDeg : -angleDeg;
         out.add(String.format(Locale.US,
               "rotate by %.2f degrees horizontally%s", deg, easingSuffix));
      } else if (isVertical) {
         float deg = ax > 0 ? angleDeg : -angleDeg;
         out.add(String.format(Locale.US,
               "rotate by %.2f degrees vertically%s", deg, easingSuffix));
      } else {
         // Ensure positive angle convention; flip axis if angle came out negative.
         if (angleDeg < 0) {
            angleDeg = -angleDeg;
            ax = -ax;
            ay = -ay;
            az = -az;
         }
         out.add(String.format(Locale.US,
               "rotate by %.2f degrees around %.4f %.4f %.4f%s",
               angleDeg, ax, ay, az, easingSuffix));
      }
   }

   private static void computeTranslation(CVViewer.ViewerState s0,
                                          CVViewer.ViewerState s1,
                                          String easingSuffix,
                                          List<String> out) {
      float dx = s1.translationX - s0.translationX;
      float dy = s1.translationY - s0.translationY;
      float dz = s1.translationZ - s0.translationZ;

      boolean hasX = Math.abs(dx) > TRANS_THRESHOLD;
      boolean hasY = Math.abs(dy) > TRANS_THRESHOLD;
      boolean hasZ = Math.abs(dz) > TRANS_THRESHOLD;

      if (!hasX && !hasY && !hasZ) {
         return;
      }
      if (hasX && !hasY && !hasZ) {
         out.add(String.format(Locale.US,
               "translate horizontally by %.4f%s", dx, easingSuffix));
      } else if (!hasX && hasY && !hasZ) {
         out.add(String.format(Locale.US,
               "translate vertically by %.4f%s", dy, easingSuffix));
      } else if (!hasX && !hasY) {
         out.add(String.format(Locale.US,
               "zoom by %.4f%s", dz, easingSuffix));
      } else {
         out.add(String.format(Locale.US,
               "translate by %.4f %.4f %.4f%s", dx, dy, dz, easingSuffix));
      }
   }

   private static void computeClip(CVViewer.ViewerState s0,
                                   CVViewer.ViewerState s1,
                                   String easingSuffix,
                                   List<String> out) {
      float[] c0 = s0.clipBox;
      float[] c1 = s1.clipBox;
      if (c0 == null || c1 == null || c0.length < 6 || c1.length < 6) {
         return;
      }
      // indices: [0]=minX [1]=maxX [2]=minY [3]=maxY [4]=minZ [5]=maxZ
      String[][] labels = {
            {"min", "x"}, {"max", "x"},
            {"min", "y"}, {"max", "y"}
      };
      for (int i = 0; i < 4; i++) {
         if (Math.abs(c1[i] - c0[i]) > CLIP_THRESHOLD) {
            out.add(String.format(Locale.US,
                  "change bounding box %s %s to %.4f%s",
                  labels[i][0], labels[i][1], c1[i], easingSuffix));
         }
      }
      if (Math.abs(c1[4] - c0[4]) > CLIP_THRESHOLD) {
         out.add(String.format(Locale.US,
               "change front clipping to %.4f%s", c1[4], easingSuffix));
      }
      if (Math.abs(c1[5] - c0[5]) > CLIP_THRESHOLD) {
         out.add(String.format(Locale.US,
               "change back clipping to %.4f%s", c1[5], easingSuffix));
      }
   }

   private static void computeChannels(CVViewer.ViewerState s0,
                                       CVViewer.ViewerState s1,
                                       boolean includeColors,
                                       Color[] colors0,
                                       Color[] colors1,
                                       String easingSuffix,
                                       List<String> out) {
      if (s0.transferRangeMin == null || s1.transferRangeMin == null) {
         return;
      }
      int nCh = Math.min(s0.transferRangeMin.length, s1.transferRangeMin.length);

      for (int ch = 0; ch < nCh; ch++) {
         // Intensity + gamma.
         boolean minChanged = Math.abs(
               s1.transferRangeMin[ch] - s0.transferRangeMin[ch]) > CH_THRESHOLD;
         boolean maxChanged = Math.abs(
               s1.transferRangeMax[ch] - s0.transferRangeMax[ch]) > CH_THRESHOLD;
         boolean gammaChanged = s0.gamma != null && s1.gamma != null
               && Math.abs(s1.gamma[ch] - s0.gamma[ch]) > CH_THRESHOLD;

         if (minChanged || maxChanged || gammaChanged) {
            double gamma1 = (s1.gamma != null) ? s1.gamma[ch] : 1.0;
            out.add(String.format(Locale.US,
                  "change channel %d intensity to %.4f %.4f %.3f%s",
                  ch + 1,
                  s1.transferRangeMin[ch],
                  s1.transferRangeMax[ch],
                  gamma1,
                  easingSuffix));
         }

         // Visibility.
         if (s0.layerVisible != null && s1.layerVisible != null
               && ch < s0.layerVisible.length && ch < s1.layerVisible.length
               && s0.layerVisible[ch] != s1.layerVisible[ch]) {
            out.add(String.format(Locale.US,
                  "change channel %d visibility to %s",
                  ch + 1,
                  s1.layerVisible[ch] ? "on" : "off"));
         }

         // Color (optional).
         if (includeColors
               && colors0 != null && colors1 != null
               && ch < colors0.length && ch < colors1.length
               && colors0[ch] != null && colors1[ch] != null
               && !colors0[ch].equals(colors1[ch])) {
            Color c = colors1[ch];
            out.add(String.format(Locale.US,
                  "change channel %d color to %d %d %d",
                  ch + 1, c.getRed(), c.getGreen(), c.getBlue()));
         }
      }
   }

   private static void computeTimePoint(int tp0, int tp1, List<String> out) {
      if (tp0 != tp1) {
         // Script language is 1-based.
         out.add(String.format(Locale.US, "change time to %d", tp1 + 1));
      }
   }

   // -----------------------------------------------------------------------
   // Thumbnail scaling
   // -----------------------------------------------------------------------

   private static BufferedImage scaleThumbnail(BufferedImage raw, int w, int h) {
      BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = result.createGraphics();
      if (raw == null) {
         g.setColor(new Color(60, 60, 60));
         g.fillRect(0, 0, w, h);
         g.setColor(Color.LIGHT_GRAY);
         g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
         g.drawString("No image", w / 2 - 28, h / 2 + 4);
      } else {
         double scaleX = (double) w / raw.getWidth();
         double scaleY = (double) h / raw.getHeight();
         double scale = Math.min(scaleX, scaleY);
         int sw = (int) (raw.getWidth() * scale);
         int sh = (int) (raw.getHeight() * scale);
         g.setColor(Color.BLACK);
         g.fillRect(0, 0, w, h);
         g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
               RenderingHints.VALUE_INTERPOLATION_BILINEAR);
         g.drawImage(raw, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
      }
      g.dispose();
      return result;
   }

   // -----------------------------------------------------------------------
   // Helpers
   // -----------------------------------------------------------------------

   private void updateButtonStates() {
      removeSelectedButton_.setEnabled(selectedIndex_ >= 0);
      generateButton_.setEnabled(keyframes_.size() >= 2);
   }
}

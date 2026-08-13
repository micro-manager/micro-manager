///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    University of California, San Francisco, 2026
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.internal.dialogs;

import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.MMROIManager;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.WindowPositioning;

/**
 * A dialog that lets the user type an exact camera ROI, in the spirit of ImageJ's
 * "Edit &gt; Selection &gt; Specify..." dialog.
 *
 * <p>The rectangle entered here is interpreted in absolute, full-chip coordinates and is
 * applied through {@link MMROIManager#setAbsoluteROI(Rectangle)}.
 */
public final class SpecifyCropDialog extends JDialog {
   private static final long serialVersionUID = 1L;

   private static final String LAST_X = "lastCropX";
   private static final String LAST_Y = "lastCropY";
   private static final String LAST_WIDTH = "lastCropWidth";
   private static final String LAST_HEIGHT = "lastCropHeight";
   private static final String CENTERED = "cropCentered";

   private final MMStudio studio_;
   private final JTextField xField_;
   private final JTextField yField_;
   private final JTextField widthField_;
   private final JTextField heightField_;
   private final JCheckBox centeredBox_;
   // Set while we update the x/y fields ourselves, so that our own edits do not
   // re-trigger the listeners that made them.
   private boolean adjusting_ = false;

   /**
    * Creates and shows the dialog.  Blocks until the user closes it.
    *
    * @param studio the omnipresent Studio object
    * @param parent window to center on; may be null
    */
   public SpecifyCropDialog(MMStudio studio, Window parent) {
      super(parent, "Specify Crop");
      studio_ = studio;

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            MMStudio.class.getResource("/org/micromanager/icons/microscope.gif")));
      setModal(true);
      setResizable(false);
      setLayout(new MigLayout("flowx, insets dialog"));

      Rectangle initial = getInitialRectangle();

      xField_ = new JTextField(Integer.toString(initial.x), 6);
      yField_ = new JTextField(Integer.toString(initial.y), 6);
      widthField_ = new JTextField(Integer.toString(initial.width), 6);
      heightField_ = new JTextField(Integer.toString(initial.height), 6);

      // Same order as a line in the crop presets file: x, y, width, height.
      add(new JLabel("X:"));
      add(xField_, "wrap");
      add(new JLabel("Y:"));
      add(yField_, "wrap");
      add(new JLabel("Width:"));
      add(widthField_, "wrap");
      add(new JLabel("Height:"));
      add(heightField_, "wrap");

      centeredBox_ = new JCheckBox("Centered");
      centeredBox_.setToolTipText(
            "Center the given width and height on the camera chip, "
                  + "filling in X and Y for you");
      centeredBox_.addActionListener(e -> {
         updateCenteredFields();
         xField_.setEditable(!centeredBox_.isSelected());
         yField_.setEditable(!centeredBox_.isSelected());
      });
      add(centeredBox_, "span 2, wrap");

      DocumentListener sizeListener = new DocumentListener() {
         @Override
         public void insertUpdate(DocumentEvent e) {
            updateCenteredFields();
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            updateCenteredFields();
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            updateCenteredFields();
         }
      };
      widthField_.getDocument().addDocumentListener(sizeListener);
      heightField_.getDocument().addDocumentListener(sizeListener);

      JButton okButton = new JButton("OK");
      okButton.addActionListener(e -> onOk());
      getRootPane().setDefaultButton(okButton);
      add(okButton, "tag ok, span 2, split");

      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(e -> dispose());
      add(cancelButton, "tag cancel, wrap");

      // Restore the checkbox last, so that its listener sees fully built fields.
      centeredBox_.setSelected(studio_.profile().getSettings(SpecifyCropDialog.class)
            .getBoolean(CENTERED, false));
      xField_.setEditable(!centeredBox_.isSelected());
      yField_.setEditable(!centeredBox_.isSelected());
      updateCenteredFields();

      pack();
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
      if (parent != null) {
         setLocationRelativeTo(parent);
      }
   }

   /**
    * Returns the rectangle to prefill the fields with: the ROI the camera is using now,
    * falling back on whatever was entered the last time this dialog was used, and finally
    * on the full chip.
    */
   private Rectangle getInitialRectangle() {
      try {
         Rectangle current = studio_.core().getROI();
         if (current != null && current.width > 0 && current.height > 0) {
            return current;
         }
      } catch (Exception e) {
         studio_.logs().logError(e, "Unable to read the current ROI");
      }

      Rectangle chip = studio_.roiManager().getFullChipBounds();
      int defaultWidth = chip == null ? 512 : chip.width;
      int defaultHeight = chip == null ? 512 : chip.height;
      return new Rectangle(
            studio_.profile().getSettings(SpecifyCropDialog.class).getInteger(LAST_X, 0),
            studio_.profile().getSettings(SpecifyCropDialog.class).getInteger(LAST_Y, 0),
            studio_.profile().getSettings(SpecifyCropDialog.class)
                  .getInteger(LAST_WIDTH, defaultWidth),
            studio_.profile().getSettings(SpecifyCropDialog.class)
                  .getInteger(LAST_HEIGHT, defaultHeight));
   }

   /**
    * When "Centered" is checked, recomputes x and y so that the requested width and
    * height sit in the middle of the chip.  Does nothing otherwise.
    */
   private void updateCenteredFields() {
      if (adjusting_ || !centeredBox_.isSelected()) {
         return;
      }
      Rectangle chip = studio_.roiManager().getFullChipBounds();
      if (chip == null) {
         return;
      }
      Integer width = parseFieldQuietly(widthField_);
      Integer height = parseFieldQuietly(heightField_);
      if (width == null || height == null) {
         // Mid-edit; leave x and y alone until the value makes sense again.
         return;
      }

      adjusting_ = true;
      try {
         xField_.setText(Integer.toString(Math.max(0, (chip.width - width) / 2)));
         yField_.setText(Integer.toString(Math.max(0, (chip.height - height) / 2)));
      } finally {
         adjusting_ = false;
      }
   }

   private static Integer parseFieldQuietly(JTextField field) {
      try {
         return Integer.valueOf(field.getText().trim());
      } catch (NumberFormatException e) {
         return null;
      }
   }

   /**
    * Validates the entered values and, if they are usable, applies them.  On bad input
    * the dialog stays open: silently substituting a value would send the camera an ROI
    * the user did not ask for.
    */
   private void onOk() {
      // Check the fields top to bottom, so that the first complaint is about the
      // topmost field the user can see.
      Integer x = parseField(xField_, "X");
      if (x == null) {
         return;
      }
      Integer y = parseField(yField_, "Y");
      if (y == null) {
         return;
      }
      Integer width = parseField(widthField_, "Width");
      if (width == null) {
         return;
      }
      Integer height = parseField(heightField_, "Height");
      if (height == null) {
         return;
      }

      if (width <= 0 || height <= 0) {
         showInputError("Width and height must both be greater than zero.");
         return;
      }
      if (x < 0 || y < 0) {
         showInputError("X and Y must not be negative.");
         return;
      }

      studio_.profile().getSettings(SpecifyCropDialog.class).putInteger(LAST_X, x);
      studio_.profile().getSettings(SpecifyCropDialog.class).putInteger(LAST_Y, y);
      studio_.profile().getSettings(SpecifyCropDialog.class).putInteger(LAST_WIDTH, width);
      studio_.profile().getSettings(SpecifyCropDialog.class).putInteger(LAST_HEIGHT, height);
      studio_.profile().getSettings(SpecifyCropDialog.class)
            .putBoolean(CENTERED, centeredBox_.isSelected());

      dispose();
      studio_.roiManager().setAbsoluteROI(new Rectangle(x, y, width, height));
   }

   private Integer parseField(JTextField field, String label) {
      Integer value = parseFieldQuietly(field);
      if (value == null) {
         showInputError(label + " must be a whole number.");
         field.requestFocusInWindow();
         field.selectAll();
      }
      return value;
   }

   private void showInputError(String message) {
      JOptionPane.showMessageDialog(this, message, "Invalid crop",
            JOptionPane.ERROR_MESSAGE);
   }
}

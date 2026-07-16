package org.micromanager.hcs;

import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;

/**
 * Modal dialog for entering user-visible plate metadata fields.
 *
 * <p>Collects four fields that the user can set to describe the physical plate:
 * <ul>
 *   <li><b>Name</b> — a human-readable label for this plate run (e.g. "Batch-42-A").
 *       Stored as {@code plateName} in OME metadata.</li>
 *   <li><b>Description</b> — free-text description of the plate or experiment.</li>
 *   <li><b>External Identifier</b> — barcode or LIMS ID that ties this plate to an
 *       external tracking system. Stored as {@code plateExternalIdentifier} in OME
 *       metadata. This is the primary traceability field.</li>
 *   <li><b>Status</b> — workflow annotation
 *       (e.g. "transfection done; imaging todo").</li>
 * </ul>
 *
 * <p>The plate's machine-readable {@code plateID} is auto-generated (UUID) separately
 * and is not shown here — it is not intended for user entry.
 */
class PlateInfoDialog extends JDialog {

   private final JTextField nameField_;
   private final JTextField descriptionField_;
   private final JTextField externalIdField_;
   private final JTextField statusField_;
   private boolean confirmed_ = false;

   /**
    * Creates the dialog pre-populated with the supplied values.
    *
    * @param owner       parent frame
    * @param name        current plate name
    * @param description current plate description
    * @param externalId  current plate external identifier (barcode / LIMS ID)
    * @param status      current plate status
    */
   PlateInfoDialog(Frame owner, String name, String description,
                   String externalId, String status) {
      super(owner, "Plate Info", true);

      nameField_        = new JTextField(name,        20);
      descriptionField_ = new JTextField(description, 20);
      externalIdField_  = new JTextField(externalId,  20);
      statusField_      = new JTextField(status,      20);

      nameField_.setToolTipText(
            "<html>Human-readable label for this plate run (e.g. \"Batch-42-A\").<br>"
            + "Stored as <i>plateName</i> in OME metadata.</html>");
      descriptionField_.setToolTipText("Free-text description of the plate or experiment.");
      externalIdField_.setToolTipText(
            "<html>Barcode or LIMS identifier for this plate.<br>"
            + "Stored as <i>plateExternalIdentifier</i> in OME metadata.<br>"
            + "Use this to tie the acquisition to an external tracking system.</html>");
      statusField_.setToolTipText(
            "Workflow status annotation (e.g. \"transfection done; imaging todo\").");

      JPanel panel = new JPanel(new MigLayout("wrap 2", "[right][grow,fill]"));
      panel.add(new JLabel("Name:"));
      panel.add(nameField_);
      panel.add(new JLabel("Description:"));
      panel.add(descriptionField_);
      panel.add(new JLabel("Ext. ID:"));
      panel.add(externalIdField_);
      panel.add(new JLabel("Status:"));
      panel.add(statusField_);

      JButton okButton = new JButton("OK");
      okButton.addActionListener(e -> {
         confirmed_ = true;
         dispose();
      });
      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(e -> dispose());

      panel.add(okButton, "span 2, split 2, tag ok");
      panel.add(cancelButton, "tag cancel");

      getRootPane().setDefaultButton(okButton);
      setContentPane(panel);
      pack();
      setLocationRelativeTo(owner);
   }

   /** Returns true if the user confirmed the dialog (clicked OK). */
   boolean wasConfirmed() {
      return confirmed_;
   }

   /** Returns the plate name entered by the user. */
   String getPlateName() {
      return nameField_.getText().trim();
   }

   /** Returns the plate description entered by the user. */
   String getPlateDescription() {
      return descriptionField_.getText().trim();
   }

   /**
    * Returns the external identifier (barcode / LIMS ID) entered by the user.
    *
    * <p>This is stored as {@code plateExternalIdentifier} in OME metadata and is
    * the primary field for tying the acquisition to an external tracking system.
    */
   String getPlateExternalIdentifier() {
      return externalIdField_.getText().trim();
   }

   /** Returns the workflow status annotation entered by the user. */
   String getPlateStatus() {
      return statusField_.getText().trim();
   }
}

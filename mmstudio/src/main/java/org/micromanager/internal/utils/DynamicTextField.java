/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.utils;

import org.apache.commons.lang3.event.EventListenerSupport;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * A JTextField with a single listener interface for value change.
 *
 * <p>The listener receives a call on every change. Enter key selects the text. The listener is
 * given a chance to validate the value on Enter key or loss of focus.
 *
 * @author mark
 */
public class DynamicTextField extends JTextField {
  /** Listener interface for DynamicTextField */
  public interface Listener {
    /**
     * Called when the text field text has changed.
     *
     * @param source the text field
     * @param shouldForceValidation true if Enter key was pressed or text field lost focus (listener
     *     should validate value and revert if invalid)
     */
    void textFieldValueChanged(DynamicTextField source, boolean shouldForceValidation);
  }

  private final EventListenerSupport<Listener> listeners_ =
      new EventListenerSupport(Listener.class, Listener.class.getClassLoader());
  private DocumentListener documentListener_;

  public DynamicTextField() {
    init();
  }

  public DynamicTextField(Document doc, String text, int columns) {
    super(doc, text, columns);
    init();
  }

  public DynamicTextField(int columns) {
    super(columns);
    init();
  }

  public DynamicTextField(String text) {
    super(text);
    init();
  }

  public DynamicTextField(String text, int columns) {
    super(text, columns);
    init();
  }

  public void addDynamicTextFieldListener(Listener listener) {
    listeners_.addListener(listener, true);
  }

  public void removeDynamicTextFieldListener(Listener listener) {
    listeners_.removeListener(listener);
  }

  @Override
  public void setDocument(Document doc) {
    // Maintain our document listener
    Document old = getDocument();
    if (old != null && documentListener_ != null) {
      old.removeDocumentListener(documentListener_);
    }
    super.setDocument(doc);
    if (doc != null && documentListener_ != null) {
      doc.addDocumentListener(documentListener_);
    }
  }

  private void init() {
    documentListener_ =
        new DocumentListener() {
          @Override
          public void insertUpdate(DocumentEvent e) {
            listeners_.fire().textFieldValueChanged(DynamicTextField.this, false);
          }

          @Override
          public void removeUpdate(DocumentEvent e) {
            listeners_.fire().textFieldValueChanged(DynamicTextField.this, false);
          }

          @Override
          public void changedUpdate(DocumentEvent e) {
            listeners_.fire().textFieldValueChanged(DynamicTextField.this, false);
          }
        };
    getDocument().addDocumentListener(documentListener_);

    addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            listeners_.fire().textFieldValueChanged(DynamicTextField.this, true);
            selectAll();
          }
        });

    addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            listeners_.fire().textFieldValueChanged(DynamicTextField.this, true);
          }
        });
  }
}

/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.ui.components;

import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * This is a {@code JTextField} that listens to every keystroke
 * and can register a method to be run on each keystroke.
 */
public class TextField extends JTextField {

   @FunctionalInterface
   public interface Listener {
      void run();
   }

   // the method that will be run
   private Listener method = () -> {
   };

   public TextField(final int size, final String text) {
      setText(text);
      setColumns(size);
      // listen to changes to the document
      getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void insertUpdate(DocumentEvent e) {
            update();
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            update();
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            update();
         }

         protected void update() {
            method.run();
         }
      });
   }

   public void registerDocumentListener(final Listener method) {
      this.method = method;
   }
}

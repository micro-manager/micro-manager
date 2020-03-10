/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.imagedisplay;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JPanel;

/**
 *
 * @author henrypinkard
 */
   public class FixedWidthJPanel extends JPanel {

      public FixedWidthJPanel() {
         super(new BorderLayout());
      }

      @Override
      public Dimension getPreferredSize() {
//            return new Dimension(40, super.getPreferredSize().height);
         return new Dimension(40, 40);
      }
   }

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import javax.swing.JFrame;

/**
 *
 * @author Henry
 */
public class SWCTester {
   
   public static void main(String[] args) {
     JFrame jf = new JFrame();
     SimpleWindowControls swc = new SimpleWindowControls(null);
     jf.add(swc);
     jf.pack();
     jf.setVisible(true);
     jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
   }
   
}

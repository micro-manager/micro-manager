///////////////////////////////////////////////////////////////////////////////
//FILE:          TestingPanel.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;

import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;

import static org.junit.Assert.*;

import org.micromanager.asidispim.api.*;

import net.miginfocom.swing.MigLayout;

/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public class TestingPanel extends ListeningJPanel {
   /**
    * 
    */
   public TestingPanel() {    
      super (MyStrings.PanelNames.TESTING.toString(), 
            new MigLayout(
              "fill", 
              "[center]",
              "[]"));
      
      // TODO improve the integration test implementation
      // challenge is that some of these need a very complete mock-up including
      //    loading a working hardware config which is non-trivial
      final JButton runIntegrationTests = new JButton("Run Integration Tests");
      runIntegrationTests.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            class TestThread extends Thread {
               TestThread(String threadName) {
                  super(threadName);
               }

               @Override
               public void run() {
                  try {
                     // there is a logical order to running these tests
                     testSavingNamePrefix();
                     testSavingDirectoryRoot();
                     testRunAcquisition();
                     testLastAcquisitionPathAndName();
                     MyDialogUtils.showError("all tests passed successfully");
                  } catch (Exception ex) {
                     MyDialogUtils.showError(ex);
                  } catch (Error r) {
                     MyDialogUtils.showError(r);
                  }
               }
            }
            TestThread acqt = new TestThread("Integration Tests");
            acqt.start(); 
         }
      });
      add(runIntegrationTests);
      
   }//constructor
   

   private void testRunAcquisition() throws ASIdiSPIMException, Error, InterruptedException {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      assertEquals(false, diSPIM.isAcquisitionRunning());
      long start = System.currentTimeMillis();
      long now = start;
      long timeout = 5000;
      diSPIM.runAcquisition();
      while(!diSPIM.isAcquisitionRunning() && ((now - start) < timeout)) {
         Thread.sleep(10);
         now = System.currentTimeMillis();
      }
      assertEquals(true, diSPIM.isAcquisitionRunning());
      diSPIM.stopAcquisition();
      start = System.currentTimeMillis();
      now = start;
      while(diSPIM.isAcquisitionRunning() && ((now - start) < timeout)) {
         Thread.sleep(10);
         now = System.currentTimeMillis();
      }
      assertEquals(false, diSPIM.isAcquisitionRunning());
      diSPIM.closeLastAcquisitionWindow();
   }
   
   private void testLastAcquisitionPathAndName() throws ASIdiSPIMException, Error, InterruptedException {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      assertEquals(false, diSPIM.isAcquisitionRunning());
      long start = System.currentTimeMillis();
      long now = start;
      long timeout = 5000;
      diSPIM.runAcquisition();
      while(!diSPIM.isAcquisitionRunning() && ((now - start) < timeout)) {
         Thread.sleep(10);
         now = System.currentTimeMillis();
      }
      assertEquals(true, diSPIM.isAcquisitionRunning());
      diSPIM.stopAcquisition();
      start = System.currentTimeMillis();
      now = start;
      while(diSPIM.isAcquisitionRunning() && ((now - start) < timeout)) {
         Thread.sleep(10);
         now = System.currentTimeMillis();
      }
      assertEquals(false, diSPIM.isAcquisitionRunning());
      diSPIM.closeLastAcquisitionWindow();
      String expectedPath = diSPIM.getSavingDirectoryRoot() + File.separator + diSPIM.getSavingNamePrefix();
      String actualPath = diSPIM.getLastAcquisitionPath();
      String expectedName = diSPIM.getSavingNamePrefix();
      String actualName = diSPIM.getLastAcquisitionName();
      assertEquals(true, actualPath.startsWith(expectedPath));
      assertEquals(true, actualName.startsWith(expectedName));
   }
   
   private void testSavingNamePrefix() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      String prefixOrig = diSPIM.getSavingNamePrefix();
      String prefixTest = "Jon";
      diSPIM.setSavingNamePrefix(prefixTest);
      assertEquals(prefixTest, diSPIM.getSavingNamePrefix());
      diSPIM.setSavingNamePrefix(prefixOrig);
   }
   
   private void testSavingDirectoryRoot() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      String rootOrig = diSPIM.getSavingDirectoryRoot();
      String rootTest = "C:\\Users\\Jon\\Desktop\\testing123";
      diSPIM.setSavingDirectoryRoot(rootTest);
      assertEquals(rootTest, diSPIM.getSavingDirectoryRoot());
      diSPIM.setSavingDirectoryRoot(rootOrig);
   }
   
   
}

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

import org.micromanager.asidispim.Data.Devices;
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
                     testSavingSeparateFile();
                     testSavingSaveWhileAcquiring();
                     testAcquisitionMode();
                     testRunAcquisition();
                     testLastAcquisitionPathAndName();
                     testTimepointsEnabled();
                     testNumTimepoints();
                     testTimepointInterval();
                     testMultiplePositionsEnabled();
                     testMultiplePositionDelayInterval();
                     testChannelsEnabled();
                     testChannelGroup();
                     testChannelEnabled();
                     testVolumeNumberOfSides();
                     testVolumeFirstSide();
                     testVolumeDelayBeforeSide();
                     testVolumeSlicesPerVolume();
                     testVolumeSliceStepSize();
                     testVolumeMinimizeSlicePeriod();
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
   
   private void testSavingSeparateFile() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      boolean settingOrig = diSPIM.getSavingSeparateFile();
      diSPIM.setSavingSeparateFile(true);
      assertEquals(true, diSPIM.getSavingSeparateFile());
      diSPIM.setSavingSeparateFile(false);
      assertEquals(false, diSPIM.getSavingSeparateFile());
      diSPIM.setSavingSeparateFile(true);
      assertEquals(true, diSPIM.getSavingSeparateFile());
      diSPIM.setSavingSeparateFile(settingOrig);
   }
   
   private void testSavingSaveWhileAcquiring() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      boolean settingOrig = diSPIM.getSavingSaveWhileAcquiring();
      diSPIM.setSavingSaveWhileAcquiring(true);
      assertEquals(true, diSPIM.getSavingSaveWhileAcquiring());
      diSPIM.setSavingSaveWhileAcquiring(false);
      assertEquals(false, diSPIM.getSavingSaveWhileAcquiring());
      diSPIM.setSavingSaveWhileAcquiring(true);
      assertEquals(true, diSPIM.getSavingSaveWhileAcquiring());
      diSPIM.setSavingSaveWhileAcquiring(settingOrig);
   }
   
   private void testAcquisitionMode() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      org.micromanager.asidispim.Data.AcquisitionModes.Keys settingOrig = diSPIM.getAcquisitionMode();
      org.micromanager.asidispim.Data.AcquisitionModes.Keys[] keys = 
            new org.micromanager.asidispim.Data.AcquisitionModes.Keys[] {
            org.micromanager.asidispim.Data.AcquisitionModes.Keys.NO_SCAN,
            org.micromanager.asidispim.Data.AcquisitionModes.Keys.PIEZO_SCAN_ONLY,
            org.micromanager.asidispim.Data.AcquisitionModes.Keys.PIEZO_SLICE_SCAN,
            org.micromanager.asidispim.Data.AcquisitionModes.Keys.SLICE_SCAN_ONLY,
            org.micromanager.asidispim.Data.AcquisitionModes.Keys.STAGE_SCAN,
            org.micromanager.asidispim.Data.AcquisitionModes.Keys.STAGE_SCAN_INTERLEAVED,
      };
      for (org.micromanager.asidispim.Data.AcquisitionModes.Keys key : keys) {
         diSPIM.setAcquisitionMode(key);
         assertEquals(key, diSPIM.getAcquisitionMode());
      }
      diSPIM.setAcquisitionMode(settingOrig);
   }
   
   private void testTimepointsEnabled() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      boolean settingOrig = diSPIM.getTimepointsEnabled();
      diSPIM.setTimepointsEnabled(true);
      assertEquals(true, diSPIM.getTimepointsEnabled());
      diSPIM.setTimepointsEnabled(false);
      assertEquals(false, diSPIM.getTimepointsEnabled());
      diSPIM.setTimepointsEnabled(true);
      assertEquals(true, diSPIM.getTimepointsEnabled());
      diSPIM.setTimepointsEnabled(settingOrig);
   }
   
   private void testNumTimepoints() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      int settingOrig = diSPIM.getTimepointsNumber();
      // throws exception if not between 1 and 32000
      try {
         diSPIM.setTimepointsNumber(0);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      try {
         diSPIM.setTimepointsNumber(32001);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      diSPIM.setTimepointsNumber(10);
      assertEquals(10, diSPIM.getTimepointsNumber());
      diSPIM.setTimepointsNumber(100);
      assertEquals(100, diSPIM.getTimepointsNumber());
      diSPIM.setTimepointsNumber(settingOrig);
   }
   
   private void testTimepointInterval() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      double settingOrig = diSPIM.getTimepointInterval();
      // throws exception if not between 0.1 and 32000
      try {
         diSPIM.setTimepointInterval(0d);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      try {
         diSPIM.setTimepointInterval(32001d);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      diSPIM.setTimepointInterval(10);
      assertEquals(10, diSPIM.getTimepointInterval(), 1e-6);
      diSPIM.setTimepointInterval(1);
      assertEquals(1, diSPIM.getTimepointInterval(), 1e-6);
      diSPIM.setTimepointInterval(settingOrig);
   }
   
   private void testMultiplePositionsEnabled() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      boolean settingOrig = diSPIM.getMultiplePositionsEnabled();
      diSPIM.setMultiplePositionsEnabled(true);
      assertEquals(true, diSPIM.getMultiplePositionsEnabled());
      diSPIM.setMultiplePositionsEnabled(false);
      assertEquals(false, diSPIM.getMultiplePositionsEnabled());
      diSPIM.setMultiplePositionsEnabled(true);
      assertEquals(true, diSPIM.getMultiplePositionsEnabled());
      diSPIM.setMultiplePositionsEnabled(settingOrig);
   }
   
   private void testMultiplePositionDelayInterval() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      double settingOrig = diSPIM.getMultiplePositionsDelay();
      // throws exception if not between 0 and 10000
      try {
         diSPIM.setMultiplePositionsDelay(-0.1);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      try {
         diSPIM.setMultiplePositionsDelay(10000.1);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      diSPIM.setMultiplePositionsDelay(10);
      assertEquals(10, diSPIM.getMultiplePositionsDelay(), 1e-6);
      diSPIM.setMultiplePositionsDelay(100);
      assertEquals(100, diSPIM.getMultiplePositionsDelay(), 1e-6);
      diSPIM.setMultiplePositionsDelay(settingOrig);
   }
   
   private void testChannelsEnabled() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      boolean settingOrig = diSPIM.getChannelsEnabled();
      diSPIM.setChannelsEnabled(true);
      assertEquals(true, diSPIM.getChannelsEnabled());
      diSPIM.setChannelsEnabled(false);
      assertEquals(false, diSPIM.getChannelsEnabled());
      diSPIM.setChannelsEnabled(true);
      assertEquals(true, diSPIM.getChannelsEnabled());
      diSPIM.setChannelsEnabled(settingOrig);
   }
   
   private void testChannelGroup() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      String settingOrig = diSPIM.getChannelGroup();
      String[] keys = diSPIM.getAvailableChannelGroups();
      for (String key : keys) {
         diSPIM.setChannelGroup(key);
         assertEquals(key, diSPIM.getChannelGroup());
      }
      try {
         diSPIM.setChannelGroup("aoeutest1234");
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      diSPIM.setChannelGroup(settingOrig);
   }
   
   // note that this adds rows to the table and there is no API method
   //   to remove them
   private void testChannelEnabled() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      String[] keys = diSPIM.getAvailableChannels();
      for (String key : keys) {
         boolean origSetting = diSPIM.getChannelEnabled(key);
         diSPIM.setChannelEnabled(key, true);
         assertEquals(true, diSPIM.getChannelEnabled(key));
         diSPIM.setChannelEnabled(key, false);
         assertEquals(false, diSPIM.getChannelEnabled(key));
         diSPIM.setChannelEnabled(key, true);
         assertEquals(true, diSPIM.getChannelEnabled(key));
         diSPIM.setChannelEnabled(key, origSetting);
      }
      try {
         diSPIM.getChannelEnabled("aoeutest1234");
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
   }
   
   private void testVolumeNumberOfSides() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      int settingOrig = diSPIM.getVolumeNumberOfSides();
      diSPIM.setVolumeNumberOfSides(1);
      assertEquals(1, diSPIM.getVolumeNumberOfSides());
      diSPIM.setVolumeNumberOfSides(2);
      assertEquals(2, diSPIM.getVolumeNumberOfSides());
      try {
         diSPIM.setVolumeNumberOfSides(0);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      try {
         diSPIM.setVolumeNumberOfSides(3);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      diSPIM.setVolumeNumberOfSides(settingOrig);
   }
   
   private void testVolumeFirstSide() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      Devices.Sides settingOrig = diSPIM.getVolumeFirstSide();
      diSPIM.setVolumeFirstSide(Devices.Sides.A);
      assertEquals(Devices.Sides.A, diSPIM.getVolumeFirstSide());
      diSPIM.setVolumeFirstSide(Devices.Sides.B);
      assertEquals(Devices.Sides.B, diSPIM.getVolumeFirstSide());
      try {
         diSPIM.setVolumeFirstSide(Devices.Sides.NONE);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      diSPIM.setVolumeFirstSide(settingOrig);
   }
   
   private void testVolumeDelayBeforeSide() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      double settingOrig = diSPIM.getVolumeDelayBeforeSide();
      // throws exception if not between 0 and 10000
      try {
         diSPIM.setVolumeDelayBeforeSide(-0.1);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      try {
         diSPIM.setVolumeDelayBeforeSide(10000.1);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      diSPIM.setVolumeDelayBeforeSide(10);
      assertEquals(10, diSPIM.getVolumeDelayBeforeSide(), 1e-6);
      diSPIM.setVolumeDelayBeforeSide(100);
      assertEquals(100, diSPIM.getVolumeDelayBeforeSide(), 1e-6);
      diSPIM.setVolumeDelayBeforeSide(settingOrig);
   }
   
   private void testVolumeSlicesPerVolume() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      int settingOrig = diSPIM.getVolumeSlicesPerVolume();
      // throws exception if not between 1 and 65000
      try {
         diSPIM.setVolumeSlicesPerVolume(0);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      try {
         diSPIM.setVolumeSlicesPerVolume(65001);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      diSPIM.setVolumeSlicesPerVolume(10);
      assertEquals(10, diSPIM.getVolumeSlicesPerVolume());
      diSPIM.setVolumeSlicesPerVolume(100);
      assertEquals(100, diSPIM.getVolumeSlicesPerVolume());
      diSPIM.setVolumeSlicesPerVolume(settingOrig);
   }
   
   private void testVolumeSliceStepSize() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      double settingOrig = diSPIM.getVolumeSliceStepSize();
      // throws exception if not between 0 and 10000
      try {
         diSPIM.setVolumeSliceStepSize(-0.1);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      try {
         diSPIM.setVolumeSliceStepSize(100.1);
         fail("didn't catch exception");
      } catch (ASIdiSPIMException ex) {
      }
      diSPIM.setVolumeSliceStepSize(10);
      assertEquals(10, diSPIM.getVolumeSliceStepSize(), 1e-6);
      diSPIM.setVolumeSliceStepSize(1);
      assertEquals(1, diSPIM.getVolumeSliceStepSize(), 1e-6);
      diSPIM.setVolumeSliceStepSize(settingOrig);
   }
   
   private void testVolumeMinimizeSlicePeriod() throws ASIdiSPIMException, Error {
      ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
      boolean settingOrig = diSPIM.getVolumeMinimizeSlicePeriod();
      diSPIM.setVolumeMinimizeSlicePeriod(true);
      assertEquals(true, diSPIM.getVolumeMinimizeSlicePeriod());
      diSPIM.setVolumeMinimizeSlicePeriod(false);
      assertEquals(false, diSPIM.getVolumeMinimizeSlicePeriod());
      diSPIM.setVolumeMinimizeSlicePeriod(true);
      assertEquals(true, diSPIM.getVolumeMinimizeSlicePeriod());
      diSPIM.setVolumeMinimizeSlicePeriod(settingOrig);
   }
   
}

///////////////////////////////////////////////////////////////////////////////
// FILE:          MMultiThreadTest.cpp 
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Device driver developer's kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   Command-line test program for MMCore and device drivers under
//                multiple-threads of execution
//
// AUTHOR:        Nenad Amodaj, http://nenad.amodaj.com
//
// COPYRIGHT:     University of California, San Francisco, 2012
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//                CVS: $Id: MMCoreTest.cpp 3262 2009-10-29 19:58:52Z karlh $


#include "../../MMCore/MMCore.h"
#include "../../MMDevice/ImageMetadata.h"

#ifdef WIN32
//#include <windows.h>
#endif
#include <iostream>
#include <iomanip>
#include <assert.h>
#include <string>

using namespace std;

// declaration of test methods
void TestDemoDevices(CMMCore& core);
void TestCameraStreaming(CMMCore& core);
void TestPixelSize(CMMCore& core);
void TestColorMode(CMMCore& core);
void TestHam(CMMCore& core);
void TestCameraLive(CMMCore& core);
void TestAF(CMMCore& core);

// main routine
int main(int argc, char* argv[])
{
   int retval = 0;

   if (argc != 2)
   {
      cout << "Invalid number of command-line parameters." << endl;
      cout << "Use: Test_MMCore <configuration file name>" << endl;
      return 1;
   }

   try {
      // Create CMMCore      object
      CMMCore core;

      // load system configuration
      core.loadSystemConfiguration(argv[1]);
      core.enableStderrLog(false); // supress console echo of log/debug messages
      std::cerr.flush();

      // print current device status
      // (this should work for any configuration)
      vector<string> devices = core.getLoadedDevices();
      for (size_t i=0; i<devices.size(); i++)
      {
         cout << devices[i] << endl;
         vector<string> props = core.getDevicePropertyNames(devices[i].c_str());
         for (size_t j=0; j<props.size(); j++)
         {
            string val = core.getProperty(devices[i].c_str(), props[j].c_str());
            cout << "    " << props[j] << "=" << val;
            if (core.hasPropertyLimits(devices[i].c_str(), props[j].c_str()))
            {
               cout << ", range: " << core.getPropertyLowerLimit(devices[i].c_str(), props[j].c_str())
                  << "-" << core.getPropertyUpperLimit(devices[i].c_str(), props[j].c_str());
            }
            cout << endl;
            std::cout.flush();
         }
      }

      cout << "Pixel size: " << core.getPixelSizeUm() << " um" << endl;

      // add any testing routines here...

      // TestDemoDevices is just an example for a testing rountine
      // It assumes that specific demo configuration is already loaded
      TestDemoDevices(core);
      TestAF(core);
      // TestColorMode(core);
      TestCameraLive(core);
      TestPixelSize(core);

      // clean-up before exiting
      core.unloadAllDevices();
   }
   catch (CMMError& err)
   {
      cout << "Exception: " << err.getMsg() << endl;
      cout << "Exiting now." << endl;
      retval = 1;
   }

   cout << (0==retval?"Test_MMCore ended OK.":"See errors above") << "\nPress Enter to terminate this program." << endl;
   std::cout.flush();
   std::cerr.flush();
   getchar();
   return retval;
}

/**
* Test routine for the MMConfig_Demo.cfg.
* Device names must match
*/
void TestDemoDevices(CMMCore& core)
{

   std::string  XYStageName = core.getXYStageDevice();
   const char* wheelName = "Emission";

   // Example 1: move filter wheel to state(position) 3
   // -------------------------------------------------
   core.setState(wheelName, 3);
   core.waitForDevice(wheelName);

   long state = core.getState(wheelName);
   cout << "State device " << wheelName << " in state " << state << endl;

   // Example 2: move filter wheel to specific label (must be previously defined)
   // ---------------------------------------------------------------------------
   core.setStateLabel(wheelName, "Chroma-HQ620");
   core.waitForDevice(wheelName);

   state = core.getState(wheelName);
   string stateLabel = core.getStateLabel(wheelName);
   cout << "State device " << wheelName << " in state " << state << ", labeled as " << stateLabel << endl;

   // Example 3: move multiple filters at once using one of the predefined configurations
   // -----------------------------------------------------------------------------------
   core.setConfig("Channel", "DAPI");
   core.waitForSystem();

   // print current status for all state devices
   vector<string> stateDevices = core.getLoadedDevicesOfType(MM::StateDevice);
   for (size_t i=0; i<stateDevices.size(); i++)
   {
      state = core.getState(stateDevices[i].c_str());
      stateLabel = core.getStateLabel(stateDevices[i].c_str());
      cout << "State device " << stateDevices[i] << " in state " << state << ", labeled as " << stateLabel << endl;
   }

   // Example 4: snap an image
   // ------------------------
   core.setExposure(100.0);
   core.setProperty("Camera", "PixelType", "8bit");
   core.snapImage();
   cout << "Image snapped." << endl;

   // Example 5: move XYStage
   // -----------------------
   core.setXYPosition(XYStageName.c_str(), 0.0, 0.0);
   core.waitForDevice(XYStageName.c_str());

   core.setXYPosition(XYStageName.c_str(), 10000.0, 10000.0);
   core.waitForDevice(XYStageName.c_str());

   double x,y;
   core.getXYPosition(XYStageName.c_str(), x, y);

   cout << "XY position = " << x << "," << y << endl;
}



/**
* Test routine for the 
*/
void TestCameraStreaming(CMMCore& core)
{
   const long numFrames = 20;
   const int memoryFootprintMB = 100;
   const double intervalMs = 300.0; // ms

   core.setCircularBufferMemoryFootprint(memoryFootprintMB);

   cout << "Buffer capacity: " << core.getBufferTotalCapacity() << endl;
   string camera = core.getCameraDevice();
   core.setExposure(200.0);

   // test normal mode
   core.snapImage();
   core.getImage();

   core.startSequenceAcquisition(numFrames, intervalMs, true);

   CDeviceUtils::SleepMs(5000);

   core.stopSequenceAcquisition();

   int count=0;
   while (core.deviceBusy(camera.c_str()))
   {
      Metadata md;
      core.getLastImageMD(0, 0, md);
      double interval = core.getBufferIntervalMs();
      printf("Displaying current image, %ld in que, %.0f ms interval.\n", core.getRemainingImageCount(), interval);
      MetadataSingleTag mdst = md.GetSingleTag(MM::g_Keyword_Elapsed_Time_ms);
      printf("Elapsed time %s, device %s\n", mdst.GetValue().c_str(), mdst.GetDevice().c_str());
   }
   printf("Camera finished with %.0f ms interval.\n", core.getBufferIntervalMs());
   core.setProperty(camera.c_str(), "ShutterMode", "Auto");



   cout << "Done! Free space =" << core.getBufferFreeCapacity() << endl;

   core.startSequenceAcquisition(numFrames, intervalMs, true);


   count=0;
   while (core.deviceBusy(camera.c_str()))
   {
      Metadata md;
      core.getLastImageMD(0, 0, md);
      double interval = core.getBufferIntervalMs();
      printf("Displaying current image, %ld in que, %.0f ms interval.\n", core.getRemainingImageCount(), interval);
      MetadataSingleTag mdst = md.GetSingleTag(MM::g_Keyword_Elapsed_Time_ms);
      printf("Elapsed time %s, device %s\n", mdst.GetValue().c_str(), mdst.GetDevice().c_str());
   }
   printf("Camera finished with %.0f ms interval.\n", core.getBufferIntervalMs());
   core.setProperty(camera.c_str(), "ShutterMode", "Auto");

}

void TestPixelSize(CMMCore& core)
{
   core.definePixelSizeConfig("Resolution10", "Objective", "State", "1");
   core.definePixelSizeConfig("Resolution20", "Objective", "State", "3");
   core.definePixelSizeConfig("Resolution40", "Objective", "State", "0");
   core.setPixelSizeUm("Resolution10", 1.0);
   core.setPixelSizeUm("Resolution20", 0.5);
   core.setPixelSizeUm("Resolution40", 0.25);

   core.setState("Objective", 2);
   cout << "Pixel size = " << core.getPixelSizeUm() << " um" << endl;

   core.setState("Objective", 1);
   cout << "Pixel size = " << core.getPixelSizeUm() << " um" << endl;

   core.setState("Objective", 3);
   cout << "Pixel size = " << core.getPixelSizeUm() << " um" << endl;

   core.setState("Objective", 0);
   cout << "Pixel size = " << core.getPixelSizeUm() << " um" << endl;

}

void TestColorMode(CMMCore& core)
{
   string camera = core.getCameraDevice();
   core.setProperty(camera.c_str(), "ColorMode", "RGB-32bit");
   core.snapImage();
   core.getImage();
   cout << "image: " << core.getImageWidth() << " X " << core.getImageHeight() << " X " << core.getNumberOfComponents() << endl;
}

/**
* Test routine for the MMConfig_Demo.cfg.
*/
void TestCameraLive(CMMCore& core)
{
   const int memoryFootprintMB = 100;

   core.setCircularBufferMemoryFootprint(memoryFootprintMB);

   cout << "Buffer capacity: " << core.getBufferTotalCapacity() << endl;
   string camera = core.getCameraDevice();
   //core.setProperty(camera.c_str(), "ShutterMode", "Open");
   core.setProperty(camera.c_str(), "Binning", "4");
   core.setExposure(200.0);

   // test normal mode
   core.snapImage();
   core.getImage();

   core.startContinuousSequenceAcquisition(0);

   int count=0;
   while (core.deviceBusy(camera.c_str()))
   {
      core.getLastImage();
      double interval = core.getBufferIntervalMs();
      printf("Displaying image %d, %ld in que, %.0f ms interval.\n", count+1, core.getRemainingImageCount(), interval);
      count++;
      if (count >= 100)
         core.stopSequenceAcquisition();
   }
   printf("Camera finished with %.0f ms interval.\n", core.getBufferIntervalMs());
   //core.setProperty(camera.c_str(), "ShutterMode", "Auto");

}

/**
* Test routine for the autofocus.
*/
void TestAF(CMMCore& core)
{
   std::cout << "Testing selected Autofocus device " << core.getAutoFocusDevice() << std::endl;
   core.fullFocus();
}


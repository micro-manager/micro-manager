///////////////////////////////////////////////////////////////////////////////
// FILE:          Test_MMCore.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Device driver developer's kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   Command-line test program for MMCore and device drivers.
//                This file is built for Win32 development and may require small
//                modifications to compile on Mac or Linux.
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/18/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
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
//                CVS: $Id$


#include "../MMCore/MMCore.h"
#define ACE_NTRACE 0
#define ACE_NDEBUG 0

#ifdef WIN32
   //#include <windows.h>
#endif
#include <iostream>
#include <iomanip>
#include <assert.h>
#include <string>
using namespace std;

#pragma warning(disable : 4312 4244)
#include "ace/Task.h"
#include <ace/Mutex.h>
#include <ace/Guard_T.h>
#include <ace/Log_Msg.h>
#pragma warning(default : 4312 4244)

// mutex
static ACE_Mutex g_lock;

// declaration of test methods
void TestDemoDevices(CMMCore& core);
void TestCameraStreaming(CMMCore& core);
void TestPixelSize(CMMCore& core);
void TestColorMode(CMMCore& core);
void TestHam(CMMCore& core);

/**
 * Creates MMCore object, loads configuration, prints the status and performs
 * a couple of basic tests.
 * 
 * Modify to exercise specific devices in more detail.
 */
int main(int argc, char* argv[])
{
   if (argc != 2)
   {
      cout << "Invalid number of command-line parameters." << endl;
      cout << "Usage: Test_MMCore <configuration file name>" << endl;
      return 1;
   }

   try {
      // Create CMMCore      object
      CMMCore core;

      // load system configuration
	   core.loadSystemConfiguration(argv[1]);
      core.enableStderrLog(false); // supress console echo of log/debug messages
   
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
        }
      }

      cout << "Pixel size: " << core.getPixelSizeUm() << " um" << endl;
	
      // add any testing routines here...

      // TestDemoDevices is just an example for a testing rountine
      // It assumes that specific demo configuration is already loaded
      //TestDemoDevices(core);
      //TestCameraStreaming(core);
      TestColorMode(core);
      //TestPixelSize(core);
      //TestHam(core);

      // clean-up before exiting
	   core.unloadAllDevices();
   }
   catch (CMMError& err)
   {
      cout << "Exception: " << err.getMsg() << endl;
      cout << "Exiting now." << endl;
      return 1;
   }

   cout << "Test_MMCore ended OK." << endl;
   return 0;
}

/**
 * Test routine for the MMConfig_Demo.cfg.
 * Device names must match
 */
void TestDemoDevices(CMMCore& core)
{
   const char* XYStageName = "XYStage";
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
   core.setXYPosition(XYStageName, 0.0, 0.0);
   core.waitForDevice(XYStageName);

   core.setXYPosition(XYStageName, 10000.0, 10000.0);
   core.waitForDevice(XYStageName);

   double x,y;
   core.getXYPosition(XYStageName, x, y);
   
   cout << "XY position = " << x << "," << y << endl;
}

class StreamTask : public ACE_Task_Base
{
public:
   StreamTask(CMMCore* pCore) : core_(pCore) {}

   virtual int svc (void)
   {
      int count = 0;
      ACE_Time_Value saveTime(0, 15000L); // us
      string camera = core_->getCameraDevice();
      while (core_->getRemainingImageCount() > 0 || core_->deviceBusy(camera.c_str()))
      {
         if (core_->getRemainingImageCount() > 0)
         {
            void* pBuf = core_->popNextImage();
            if (pBuf != 0)
            {
               // save image
               ACE_OS::sleep(saveTime);
               printf("Saved image: %d\n", count++);
            }
            else
            {
               // no images pending, so just skip
               printf("This shouldn't happen!\n");
               ACE_OS::sleep(1);
            }
         }
      }
      return 0;
   }

private:
   CMMCore* core_;
};

/**
 * Test routine for the MMConfig_Stream.cfg.
 */
void TestCameraStreaming(CMMCore& core)
{
   const long numFrames = 20;
   const int memoryFootprintMB = 100;
   const double intervalMs = 300.0; // ms
   ACE_Time_Value displayTime(0, 80000L); // us
   ACE_Time_Value restTime(5, 0L);


   core.setCircularBufferMemoryFootprint(memoryFootprintMB);

   cout << "Buffer capacity: " << core.getBufferTotalCapacity() << endl;
   string camera = core.getCameraDevice();
   //core.setProperty(camera.c_str(), "ShutterMode", "Open");
   //core.setProperty(camera.c_str(), "Binning", "2");
   core.setExposure(200.0);

   // test normal mode
   core.snapImage();
   core.getImage();

   core.startSequenceAcquisition(numFrames, intervalMs);

   StreamTask streamWriter(&core);
   int result = streamWriter.activate ();

   int count=0;
   while (core.deviceBusy(camera.c_str()))
   {
      core.getLastImage();
      double interval = core.getBufferIntervalMs();
      printf("Displaying current image, %ld in que, %.0f ms interval.\n", core.getRemainingImageCount(), interval);
      ACE_OS::sleep(displayTime);
   }
   printf("Camera finished with %.0f ms interval.\n", core.getBufferIntervalMs());
   core.setProperty(camera.c_str(), "ShutterMode", "Auto");

   
   streamWriter.wait ();

   cout << "Done! Free space =" << core.getBufferFreeCapacity() << endl;

   core.startSequenceAcquisition(numFrames, intervalMs);

   StreamTask streamWriter1(&core);
   result = streamWriter1.activate ();

   count=0;
   while (core.deviceBusy(camera.c_str()))
   {
      core.getLastImage();
      double interval = core.getBufferIntervalMs();
      printf("Displaying current image, %ld in que, %.0f ms interval.\n", core.getRemainingImageCount(), interval);
      ACE_OS::sleep(displayTime);
   }
   printf("Camera finished with %.0f ms interval.\n", core.getBufferIntervalMs());
   core.setProperty(camera.c_str(), "ShutterMode", "Auto");

   
   streamWriter1.wait ();
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
   core.getRGB32Image();
   cout << "image: " << core.getImageWidth() << " X " << core.getImageHeight() << " X " << core.getNumberOfChannels() << endl;
}

void TestHam(CMMCore& core)
{
   core.snapImage();
   core.getImage();
   core.setCameraDevice("CAM1");
   core.snapImage();
   core.getImage();
   cout << "CAM1 " << core.getImageWidth() << " X " << core.getImageHeight() << endl;
   core.setCameraDevice("CAM2");
   core.snapImage();
   core.getImage();
   cout << "CAM2 " << core.getImageWidth() << " X " << core.getImageHeight() << endl;
}

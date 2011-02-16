///////////////////////////////////////////////////////////////////////////////
// FILE:          Test_MMCore_devkit.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Device driver developer's kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   Command-line test program for MMCore and device drivers.
//                This file is built for Win32 development and may require small
//                modifications to compile on Mac or Linux.
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 10/01/2007
//
// COPYRIGHT:     University of California, San Francisco, 2007
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
//                CVS: $Id: Test_MMCore.cpp 475 2007-09-27 19:44:59Z nenad $


#include "../MMCore/MMCore.h"

#ifdef WIN32
   #include <windows.h>
#endif
#include <iostream>
#include <iomanip>
#include <assert.h>
#include <string>
#include <sstream>
using namespace std;


// declaration of test methods
//void TestDemoDevices(CMMCore& core);
//void TestPixelSize(CMMCore& core);

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
	
   // Redirecting stderr since requests to disable logging to console via
   // enableStderrLog is not well respected.
   FILE* Fstderr = freopen("stderr.out", "w", stderr);

   try {
	
      // Create CMMCore object
      CMMCore core;

      // load system configuration
	  core.loadSystemConfiguration(argv[1]);

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
            cout << "    " << props[j] << "=" << val << endl;
         }
      }
	  core.setExposure(100);

	  core.snapImage();
	  cout << "Pixel Size: " <<  core.getPixelSizeUm() << endl;  
	  //core.startContinuousSequenceAcquisition(10);
	  //Sleep(5000);
	  //core.stopSequenceAcquisition();
      // clean-up before exiting
	  core.unloadAllDevices();
   
	  cout << "Test_MMCore ended OK." << endl;
   }
   catch (CMMError& err)
   {
	  cout << "Begin Exception >"  << endl;
	  cout << err.getMsg() << endl;
	  cout << "End Exception" << endl;
	  cout << "Exiting now." << endl;
   }
   fclose(Fstderr);

   return 0;
}
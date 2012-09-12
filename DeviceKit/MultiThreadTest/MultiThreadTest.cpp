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

#include <iostream>
#include <iomanip>
#include <assert.h>
#include <string>

using namespace std;

class TestThread : public MMDeviceThreadBase
{
public:
   TestThread(CMMCore* c) : core(c) {}
   ~TestThread() {}

   int svc();

private:
   CMMCore* core;
};

int TestThread::svc()
{
   return 0;
}

// main routine
int main(int argc, char* argv[])
{
   int retval = 0;

   if (argc != 2)
   {
      cout << "Invalid number of command-line parameters." << endl;
      cout << "Use: MultiThreadTest <configuration file name>" << endl;
      return 1;
   }

   try {
      CMMCore core;

      // load system configuration
      core.loadSystemConfiguration(argv[1]);
      core.enableStderrLog(false);
 
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

      // clean-up before exiting
      core.unloadAllDevices();
   }
   catch (CMMError& err)
   {
      cout << "Exception: " << err.getMsg() << endl;
      cout << "Exiting now." << endl;
      return 2;
   }

   return retval;
}


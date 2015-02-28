#include "../../MMCore/MMCore.h"
#include "../Common/PropertyTypes.h"
#include "../Common/DeviceTypes.h"
#include <iostream>
#include <iomanip>
#include <assert.h>
#include <sstream>

using namespace std;

int main(int argc, char* argv[])
{
   // get module and device names
   if (argc < 3)
   {
      cout << "Error. Module and/or device name not specified!" << endl;
      cout << "ModuleTest <module_name> <device_name>" << endl;
      return 1;
   }
   else if (argc > 3)
   {
      cout << "Error. Too many parameters!" << endl;
      cout << "ModuleTest <module_name> <device_name>" << endl;
      return 1;
   }
   string moduleName(argv[1]);
   string deviceName(argv[2]);

   CMMCore core;
   core.enableStderrLog(true);
   core.enableDebugLog(true);
   string label("Device");
   try
   {
      // Initialize the device
      // ---------------------
      cout << "Loading " << deviceName << " from library " << moduleName << "..." << endl;
      core.loadDevice(label.c_str(), moduleName.c_str(), deviceName.c_str());
      cout << "Done." << endl;

      cout << "Initializing..." << endl;
      core.initializeAllDevices();
      cout << "Done." << endl;

      // Obtain device properties
      // ------------------------
      vector<string> props(core.getDevicePropertyNames(label.c_str()));
      for (unsigned i=0; i < props.size(); i++)
      {
         cout << props[i] << " (" << ::getPropertyTypeVerbose(core.getPropertyType(label.c_str(), props[i].c_str())) << ") = "
                          << core.getProperty(label.c_str(), props[i].c_str()) << endl;
      }

      // additional testing
      MM::DeviceType type = core.getDeviceType(label.c_str());

      if (type == MM::CameraDevice)
      {
         cout << "Testing camera specific functions:" << endl;
         core.setExposure(10.0);
         core.snapImage();
         core.getImage();
      }
      else if (type == MM::StateDevice)
      {
         cout << "Testing State Device specific functions:" << endl;
      }
   
      // unload the device
      // -----------------
      core.unloadAllDevices();
   }
   catch (CMMError& err)
   {
      cout << err.getMsg();
      return 1;
   }

   // declare success
   // ---------------
   cout << "Device " + deviceName + " PASSED" << endl;
	return 0;
}

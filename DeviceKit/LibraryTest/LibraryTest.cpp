// LibraryTest.cpp : Defines the entry point for the console application.
//
#include "../../MMCore/MMCore.h"
#include "../Common/DeviceTypes.h"
#include <iostream>
#include <iomanip>
#include <sstream>
#include <assert.h>

using namespace std;

int main(int argc, char* argv[])
{
   // get module name
   if (argc < 2)
   {
      cout << "Library (module) name not specified." << endl;
      cout << "LibraryTest <library_name>" << endl;
      cout << "Example: LibraryTest mylib" << endl;
      return 1;
   }
   else if (argc > 3)
   {
      cout << "Too many parameters. Only one required." << endl;
      return 1;
   }
   string moduleName(argv[1]);

   try
   {
      CMMCore core;
      core.enableStderrLog(false);
      vector<string> devices(core.getAvailableDevices(moduleName.c_str()));
      vector<string> descriptions(core.getAvailableDeviceDescriptions(moduleName.c_str()));
      vector<long> types(core.getAvailableDeviceTypes(moduleName.c_str()));

      cout << "Library: " << moduleName << endl;
      cout << "Number of devices: " << devices.size() << endl << endl;

      if (devices.size() != descriptions.size() || devices.size() != types.size())
      {
         cout << "Number of descriptions does not match number of devices." << endl;
         cout << "This should never happen and it points to build inconsistencies or major breakdown of the module API." << endl;
         return 3;
      }

      for (unsigned i=0; i<devices.size(); i++)
      {
         cout << devices[i] << ", " << descriptions[i] << ", type " << ::getDeviceTypeVerbose(MM::DeviceType(types[i])) << endl;
      }

      // attempt to load all devices
      cout << endl << "Loading all devices (without initialization)..." << endl;
      for (unsigned i=0; i<devices.size(); i++)
      {
         ostringstream label;
         label << "Device_" << i;
         core.loadDevice(label.str().c_str(), moduleName.c_str(), devices[i].c_str()); 
      }
      cout << "Done." << endl << endl;

      // report loaded devices
      cout << "Loaded devices:" << endl;
      vector<string> loadedDevices(core.getLoadedDevices());
      for (unsigned i=0; i<loadedDevices.size(); i++)
      {
         cout  << loadedDevices[i] << ", " << core.getDeviceName(loadedDevices[i].c_str()) << endl;
      }

      cout << endl << "Unloading all devices..." << endl;
      // everything went fine, so we can unload
      core.unloadAllDevices();
      cout << "Done." << endl << endl;
      cout << "LibraryTest PASSED" << endl;

   }
   catch (CMMError& err)
   {
      cout << err.getMsg() << endl;
      return 2;
   }

	return 0;
}

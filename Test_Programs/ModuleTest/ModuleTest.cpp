// ModuleTest.cpp : Defines the entry point for the console application.
//
#include "../../MMCore/PluginManager.h"
#include "../../MMCore/Error.h"
#include <iostream>
#include <iomanip>
#include <assert.h>

using namespace std;

const char* g_OK = "OK";
const char* g_Failed = "Failed";

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

   // load the device
   // ---------------
   CPluginManager pluginManager;
   MM::Device* pDevice(0);
   cout << "Attempting to load device " << deviceName << " from module " << moduleName << "...";
   try
   {
      pDevice = pluginManager.LoadDevice("Device", moduleName.c_str(), deviceName.c_str());
   }
   catch (CMMError& err)
   {
      cout << g_Failed << endl;
      cout << "Plug-in manager returned error code " << err.getCode() << "." << endl;
      return 1;
   }
   cout << g_OK << endl;

   // Verify device name
   // ------------------
   char buffer[MM::MaxStrLength];
   pDevice->GetName(buffer);
   cout << "Device " << buffer << " loaded." << endl;

   if (deviceName.compare(buffer) != 0)
      cout << "Warning: actual device name does not match requested one." << endl;

   // determine device type
   // ---------------------
   MM::DeviceType eType = pDevice->GetType();
   string type;
   switch (eType)
   {
      case MM::CameraDevice:
         type = "Camera";
      break;

      case MM::ShutterDevice:
         type = "Shutter";
      break;

      case MM::StageDevice:
         type = "Stage";
      break;

      case MM::StateDevice:
         type = "State device";
      break;

      default:
         type = "Unrecognized device";
      break;
   }

   cout << "Device type: " << type << endl; 

   // Initialize the device
   // ---------------------
   cout << "Attempting to initialize device ...";
   int nRet = pDevice->Initialize();
   if (nRet == MMERR_OK)
      cout << g_OK << endl;
   else
   {
      cout << g_Failed << endl;
      cout << "Device returned error code " << nRet << "." << endl;
      return 1;
   }

   // Obtain device properties
   // ------------------------
   unsigned numProps = pDevice->GetNumberOfProperties();
   cout << "Device supports " << numProps << " properties." << endl;
   if (numProps > 0)
      cout << "Property list: " << endl;

   for (unsigned i=0; i<numProps; i++)
   {
      bool bRet = pDevice->GetPropertyName(i, buffer);
      string propName(buffer);
      assert(bRet);
      nRet = pDevice->GetProperty(propName.c_str(), buffer);
      if (nRet != DEVICE_OK)
      {
         cout << "GetProperty() failed at " << propName << endl;
         return 1;
      }
      cout << propName << "=" << buffer << endl;

      unsigned numVals = pDevice->GetNumberOfPropertyValues(propName.c_str());
      for (unsigned j=0; j<numVals; j++)
      {
         bRet = pDevice->GetPropertyValueAt(propName.c_str(), j, buffer);
         assert(bRet);
         cout << "    " << buffer << endl;
      }
   }

   // additional testing for cameras
   // ------------------------------
   if (eType == MM::CameraDevice)
   {
      cout << "Testing camera specific functions:" << endl;
      MM::Camera* pCamera = static_cast<MM::Camera*> (pDevice);
      assert(pCamera);

      cout << "SnapImage()...";
      nRet = pCamera->SnapImage();
      if (DEVICE_OK != nRet)
      {
         cout << "SnapImage() failed. Error code " << nRet << " (" << setbase(16) << nRet << ")" << endl;
         return 1;
      }
      else
         cout << g_OK << endl;

      // modes
      const char* modeProp = "Mode";
      nRet = pCamera->GetProperty(modeProp, buffer);
      if (nRet == DEVICE_OK)
      {
         cout << "Testing modes..." << endl;
         unsigned numModes = pCamera->GetNumberOfPropertyValues(modeProp);
         for (unsigned j=0; j<numModes; j++)
         {
            bool bRet = pCamera->GetPropertyValueAt(modeProp, j, buffer);
            assert(bRet);
            string strMode(buffer);
            nRet = pCamera->SetProperty(modeProp, strMode.c_str());
            if (nRet != DEVICE_OK)
            {
               cout << "Mode " << strMode << " failed. Error code " << nRet << endl;
               return 1;
            }
            nRet = pCamera->SnapImage();
            if (DEVICE_OK != nRet)
            {
               cout << "SnapImage() failed. Error code " << nRet << endl;
               return 1;
            }

            unsigned width, height, depth;
            
            width = pCamera->GetImageWidth();
            height = pCamera->GetImageHeight();
            depth = pCamera->GetImageBytesPerPixel();

            nRet = pCamera->GetProperty(modeProp, buffer);
            assert(nRet == DEVICE_OK);

            cout << buffer << ", " << width << " X " << height << " X " << depth << endl; 
         }
      }
   }
   else if (eType == MM::StateDevice)
   {
      cout << "Testing State Device specific functions:" << endl;
      MM::State* pStateDevice = static_cast<MM::State*> (pDevice);
      assert(pStateDevice);

      long numPos = pStateDevice->GetNumberOfPositions();

      for (long i=0; i<numPos; i++)
      {
         nRet = pStateDevice->SetPosition(i);
         if (DEVICE_OK != nRet)
         {
            cout << "SetPosition() failed for " << i << ". Error code " << nRet << " (" << setbase(16) << nRet << ")" << endl;
            return 1;
         }

         long pos;
         nRet = pStateDevice->GetPosition(pos);
         if (DEVICE_OK != nRet)
         {
            cout << "GetPosition() failed for " << i << ". Error code " << nRet << " (" << setbase(16) << nRet << ")" << endl;
            return 1;
         }

         if (pos != i)
         {
            cout << "GetPosition returned " << pos << " after SetPosition " << i << endl;
            return 1;
         }
         
         nRet = pStateDevice->GetPositionLabel(i, buffer);
         if (DEVICE_OK != nRet)
         {
            cout << "GetPositionLabel() failed for " << i << ". Error code " << nRet << " (" << setbase(16) << nRet << ")" << endl;
            return 1;
         }
         
         cout << "Position " << i << " : " << buffer << " " << g_OK << endl;
      }      
  }
   
   // unload the device
   // -----------------
   cout << "Attempting to unload device " << deviceName << " from module " << moduleName << "...";
   try
   {
      pluginManager.UnloadDevice(pDevice);
   }
   catch (CMMError& err) 
   {
      cout << g_Failed << endl;
      cout << "Plug-in manager returned error code " << err.getCode() << "." << endl;
      return 1;
   }
   cout << g_OK << endl;

   // declare success
   // ---------------
   cout << "Device tests OK." << endl;
	return 0;
}

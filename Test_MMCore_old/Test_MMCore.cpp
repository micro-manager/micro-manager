///////////////////////////////////////////////////////////////////////////////
// FILE:       Test_MMCore.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  Test program
//-----------------------------------------------------------------------------
// DESCRIPTION: Test and exercise MMCore instrument control
// AUTHOR: Nenad Amodaj, 08/18/2005
// NOTES: 
// REVISIONS: 
// CVS: $Id$
//


#include "../MMCore/MMCore.h"
#include "../MMCore/Configuration.h"
#ifdef WIN32
#include <windows.h>
#endif
#include <iostream>
#include <iomanip>
#include <assert.h>

using namespace std;
#ifdef WIN32
void LoadZeissMTB(CMMCore& core);
#endif
void LoadDemoDevices(CMMCore& core);
void TestDemoDevices(CMMCore& core);
#ifdef WIN32
void TestZeissMTB(CMMCore& core);
void TestZeissMTBFocus(CMMCore& core);
void TestPVCAM(CMMCore& core);
void TestAndor(CMMCore& core);
void TestSP(CMMCore& core);
#endif
void TestHam(CMMCore& core);
#ifdef WIN32
void TestPCO(CMMCore& core);
void TestNikonTE2000(CMMCore& core);
void TestNikon(CMMCore& core);
void TestPrior(CMMCore& core);
void TestDTOL(CMMCore& core);
#endif

/**
 * Creates MMCore objects loads demo device and displays the status.
 */
int main(int /* argc */, char* /*argv[]*/)
{
   try {

      // Create MMCore     
      CMMCore core;

      // load demo devices
     //LoadDemoDevices(core);
	  //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_uberzeiss.cfg");
	  //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_PVCAM.cfg");
	  //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_Andor.cfg");
	  //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_StanfordPhotonics.cfg");
	  //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_NikonZ.cfg");
     //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_zeiss_wl2.cfg");
     //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_prior.cfg");
     //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_DTOL.cfg");
	  //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_NikonTE2000.cfg");  
	  //core.loadSystemConfiguration("C:/projects/MicroManage/bin/tirftest.cfg");  
	  //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_prior_1.cfg");
	  core.loadSystemConfiguration("C:/Program files/Micro-Manager/MMConfig_ascb_nikon.cfg");
     //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_uberzeiss.cfg");
	  //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_PCO.cfg");
	  //core.loadSystemConfiguration("C:/projects/MicroManage/bin/MMConfig_demo_ham.cfg");  
	  
     //LoadZeissMTB(core);
     core.enableStderrLog(false); // supress console echo of log/debug messages
   
      // print loaded equipment attributes
      vector<string> blks = core.getAvailablePropertyBlocks();
      cout << "Dumping property blocks: " << endl;
      for (size_t i=0; i<blks.size(); i++)
      {
         PropertyBlock blk = core.getPropertyBlockData(blks[i].c_str());
         cout << blks[i].c_str() << endl;
     
         for (size_t j=0; j<blk.size(); j++)
         {
            PropertyPair p = blk.getPair(j);
            cout << "   " << p.getPropertyName() << " = " << p.getPropertyValue() << endl;
         }
      }

      // print current device status
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
	
	  //TestDemoDevices(core);
	  //TestZeissMTBFocus(core);
     //TestPrior(core);
      //TestDTOL(core);
     //TestPVCAM(core);
     //TestAndor(core);
     //TestSP(core);
    // TestNikon(core);
     //TestHam(core);
     //TestHam(core);
	  //TestPCO(core);
     TestNikonTE2000(core);

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
 * Configuration routine for demo devices.
 */
void LoadDemoDevices(CMMCore& core)
{
   core.unloadAllDevices();

   // define available equipment attributes
   // -------------------------------------
   core.definePropertyBlock("Nikon 10X S Fluor", "Magnification", "10");
   core.definePropertyBlock("Zeiss 4X Plan Apo", "Magnification", "4");

   // load devices
   // ------------
   cout << "Loading devices..." << endl;
   core.loadDevice("Camera", "DemoCamera", "DCam");
   core.loadDevice("Emission", "DemoCamera", "DWheel");
   core.loadDevice("Excitation", "DemoCamera", "DWheel");
   core.loadDevice("Dichroic", "DemoCamera", "DWheel");
   core.loadDevice("Objective", "DemoCamera", "DObjective");
   core.loadDevice("X", "DemoCamera", "DStage");
   core.loadDevice("Y", "DemoCamera", "DStage");
   core.loadDevice("Z", "DemoCamera", "DStage");

   core.initializeAllDevices();

   // set labels for state devices
   // emission filter
   core.defineStateLabel("Emission", 0, "Chroma-D460");
   core.defineStateLabel("Emission", 1, "Chroma-HQ620");
   core.defineStateLabel("Emission", 2, "Chroma-HQ535");
   core.defineStateLabel("Emission", 3, "Chroma-HQ700");

   // excitation filter
   core.defineStateLabel("Excitation", 2, "Chroma-D360");
   core.defineStateLabel("Excitation", 3, "Chroma-HQ480");
   core.defineStateLabel("Excitation", 4, "Chroma-HQ570");
   core.defineStateLabel("Excitation", 5, "Chroma-HQ620");

   // excitation dichroic
   core.defineStateLabel("Dichroic", 0, "400DCLP");
   core.defineStateLabel("Dichroic", 1, "Q505LP");
   core.defineStateLabel("Dichroic", 2, "Q585LP");

   // objective
   core.defineStateLabel("Objective", 1, "Nikon 10X S Fluor");
   core.defineStateLabel("Objective", 3, "Nikon 20X Plan Fluor ELWD");
   core.defineStateLabel("Objective", 5, "Zeiss 4X Plan Apo");

   // define settings
   core.defineConfig("Channel", "FITC", "Emission", "State", "2");
   core.defineConfig("Channel", "FITC", "Excitation", "State", "3");
   core.defineConfig("Channel", "FITC", "Dichroic", "State", "1");

   core.defineConfig("Channel", "DAPI", "Emission", "State", "1");
   core.defineConfig("Channel", "DAPI", "Excitation", "State", "2");
   core.defineConfig("Channel", "DAPI", "Dichroic", "State", "0");

   core.defineConfig("Channel", "Rhodamine", "Emission", "State", "3");
   core.defineConfig("Channel", "Rhodamine", "Excitation", "State", "4");
   core.defineConfig("Channel", "Rhodamine", "Dichroic", "State", "2");

   // set initial imaging mode
   core.setProperty("Camera", "Exposure", "55");
   core.setProperty("Objective", "Label", "Nikon 10X S Fluor");
   core.setConfig("Channel", "DAPI");
}

void TestDemoDevices(CMMCore& core)
{
      // Example 1: move filter wheel to state(position) 3
      // -------------------------------------------------
      const char* wheelName = "Emission";
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
}

/**
 * Configuration and test routine for Zeiss MTB.
 */
#ifdef WIN32
void LoadZeissMTB(CMMCore& core)
{
   core.unloadAllDevices();

   // load devices
   // ------------
   cout << "Loading devices..." << endl;

   core.loadDevice("REFLECTOR", "ZeissMTB", "Reflector");
   core.loadDevice("Z", "ZeissMTB", "Focus");
   core.loadDevice("SHUTTER", "ZeissMTB", "Shutter");
   core.loadDevice("OBJECTIVE", "ZeissMTB", "Objective");
   core.loadDevice("LAMP", "ZeissMTB", "Halogen");
   core.initializeAllDevices();

}
#endif

#ifdef WIN32
void TestZeissMTB(CMMCore& core)
{
   core.setProperty("Stand", "LightManager", "0");

   // exercise the system
   const char* channel = "FilterCube";
   const char* focus = "Z";
   cout << "Exercising the filter wheel..." << endl;
   vector<string> labels = core.getAllowedPropertyValues(channel, "Label");
   for (size_t i=0; i<labels.size(); i++)
   {
      cout << "Setting label " << labels[i] << endl;
      core.setProperty(channel, "Label", labels[i].c_str());
      core.waitForSystem();
      string newLabel = core.getProperty(channel, "Label");
      cout << "Label " << newLabel << " set." << endl;
   }

   // test for the reflector devices
   long state = 0;
   cout << "Testing state " << state << "..." << endl;
   core.setState(channel, state);
   core.waitForSystem();
   long pos = core.getState(channel);
   string label = core.getStateLabel(channel);
   cout << "state=" << pos << ", label=" << label << endl;
   
   state = 1;
   cout << "Testing state " << state << "..." << endl;
   core.setState(channel, state);
   core.waitForSystem();//("REFLECTOR");
   pos = core.getState(channel);
   label = core.getStateLabel(channel);
   cout << "state=" << pos << ", label=" << label << endl;

   // test the focus stage
   cout << "Testing focus..." << endl;
   cout << "z = " << core.getPosition(focus) << endl;
   core.setPosition(focus, core.getPosition(focus) + 0.7);
   core.waitForDevice(focus);
   cout << "new z = " << core.getPosition(focus) << endl;

   // test the lamp
   cout << "Testing lamp..." << endl;
   cout << "Initial state = " << core.getProperty("HalogenLamp", "OnOff") << endl;
   core.setProperty("HalogenLamp", "OnOff", "1");
   //core.sleep(500);
   cout << "State after ON = " << core.getProperty("HalogenLamp", "OnOff") << endl;
   core.setProperty("HalogenLamp", "OnOff", "0");
   cout << "State after OFF = " << core.getProperty("HalogenLamp", "OnOff") << endl;

   // test channel configs
   const char* channelGroup = "Channel";
   cout << "Testing channel configurations..." << endl;
   core.setProperty("Shutter", "State", "1");
   vector<string> configs = core.getAvailableConfigs(channelGroup);
   for (size_t i=0; i<configs.size(); i++)
   {
      core.setConfig(channelGroup, configs[i].c_str());
      //core.waitForConfig(channelGroup, configs[i].c_str());
      core.waitForSystem();
      cout << "Channel configuration applied: " << core.getCurrentConfig(channelGroup) << endl;
      core.sleep(2000);
   }
}
#endif

#ifdef WIN32
void TestPVCAM(CMMCore& core)
{
   unsigned height = core.getImageHeight();
   unsigned width = core.getImageWidth();

   //cout << "Setting bin size 4..." << endl;
   //core.setProperty("Camera", "Binning", "4");
   //height = core.getImageHeight();
   //width = core.getImageWidth();
   //core.snapImage();

   //cout << "Setting bin size 2..." << endl;
   //core.setProperty("Camera", "Binning", "2");
   //height = core.getImageHeight();
   //width = core.getImageWidth();
   //core.snapImage();

   cout << "Setting bin size 1..." << endl;
   core.setProperty("Camera", "Binning", "1");
   height = core.getImageHeight();
   width = core.getImageWidth();
   core.setExposure(1000.0);

   unsigned char* pBuf = new unsigned char[core.getImageBufferSize()];
   long startT = GetTickCount();
   core.snapImage();
   long endT = GetTickCount() - startT;
   cout << "snapImage() took " << endT << " ms" << endl;
   startT = GetTickCount();
   void* pCamBuf = core.getImage();
   memcpy(pBuf, pCamBuf, core.getImageBufferSize());
   endT = GetTickCount() - startT;
   cout << "getImage() took " << endT << " ms" << endl;
   delete[] pBuf;

   // gain tests
   cout << "Gain=" << core.getProperty("Camera", "Gain") << endl;
   cout << "MultiplierGain=" << core.getProperty("Camera", "MultiplierGain") << endl;
}
#endif

void TestHam(CMMCore& core)
{
   const char* camera = "Camera";

   unsigned height = core.getImageHeight();
   unsigned width = core.getImageWidth();

   //cout << "Setting bin size 4..." << endl;
   //core.setProperty(camera, "Binning", "4");
   //height = core.getImageHeight();
   //width = core.getImageWidth();
   //core.snapImage();
   //core.getImage();

   //cout << "Setting bin size 2..." << endl;
   //core.setProperty(camera, "Binning", "2");
   //height = core.getImageHeight();
   //width = core.getImageWidth();
   //core.snapImage();
   //core.getImage();

   core.setProperty(camera, "ScanMode", "1");
   cout << "Readout time (s): " << core.getProperty(camera, "ReadoutTime") << endl;

   cout << "Setting bin size 1..." << endl;
   core.setProperty(camera, "Binning", "1");
   core.setAutoShutter(false);
   height = core.getImageHeight();
   width = core.getImageWidth();
   core.setExposure(1000);

   unsigned char* pBuf = new unsigned char[core.getImageBufferSize()];
   //long startT = GetTickCount();
   core.snapImage();
   //long endT = GetTickCount() - startT;
   //cout << "snapImage() took " << endT << " ms" << endl;
   //startT = GetTickCount();
   void* pCamBuf = core.getImage();
   //endT = GetTickCount() - startT;
   memcpy(pBuf, pCamBuf, core.getImageBufferSize());
   //cout << "getImage() took " << endT << " ms" << endl;
   delete[] pBuf;

  // cout << "Setting ROI..." << endl;
  //// core.setROI(101, 267, 333, 674);
  // core.setROI(100, 100, 100, 100);
  // unsigned x, y, xSize, ySize;
  // core.getROI(x, y, xSize, ySize);
  // cout << "ROI set to:" << x << ", " << y << ", " << xSize << ", " << ySize << endl;

   //cout << "Setting the scan mode..." << endl;
   //core.setProperty(camera, "ScanMode", "1");
   //cout << "Scan mode: " << core.getProperty(camera, "ScanMode") << endl;
   ////cout << "Offset: " << core.getProperty(camera, "Offset") << endl;
   //cout << "Readout time (s): " << core.getProperty(camera, "ReadoutTime") << endl;
   //core.setProperty(camera, "ScanMode", "2");
   //cout << "Scan mode: " << core.getProperty(camera, "ScanMode") << endl;
   //cout << "Readout time (s): " << core.getProperty(camera, "ReadoutTime") << endl;

}

void TestPCO(CMMCore& core)
{
   unsigned height = core.getImageHeight();
   unsigned width = core.getImageWidth();

   //cout << "Setting bin size 4..." << endl;
   //core.setProperty("Camera", "Binning", "4");
   //height = core.getImageHeight();
   //width = core.getImageWidth();
   //core.snapImage();

   //cout << "Setting bin size 2..." << endl;
   //core.setProperty("Camera", "Binning", "2");
   //height = core.getImageHeight();
   //width = core.getImageWidth();
   //core.snapImage();

   cout << "Setting bin size 1..." << endl;
   core.setProperty("CAM", "Binning", "1");

   cout << "Setting ROI..." << endl;
   core.setROI(101, 267, 333, 674);
  // core.setROI(100, 100, 100, 100);
  unsigned x, y, xSize, ySize;
  core.getROI(x, y, xSize, ySize);
  cout << "ROI set to:" << x << ", " << y << ", " << xSize << ", " << ySize << endl;

   height = core.getImageHeight();
   width = core.getImageWidth();
   core.setExposure(1);

   unsigned char* pBuf = new unsigned char[core.getImageBufferSize()];
   long startT = GetTickCount();
   core.snapImage();
   long endT = GetTickCount() - startT;
   cout << "snapImage() took " << endT << " ms" << endl;
   startT = GetTickCount();
   void* pCamBuf = core.getImage();
   memcpy(pBuf, pCamBuf, core.getImageBufferSize());
   endT = GetTickCount() - startT;
   cout << "getImage() took " << endT << " ms" << endl;
   delete[] pBuf;

   cout << "Clearing ROI..." << endl;
   core.clearROI();
  core.getROI(x, y, xSize, ySize);
  cout << "ROI set to:" << x << ", " << y << ", " << xSize << ", " << ySize << endl;

   height = core.getImageHeight();
   width = core.getImageWidth();
   core.setExposure(1);

   pBuf = new unsigned char[core.getImageBufferSize()];
   startT = GetTickCount();
   core.snapImage();
   endT = GetTickCount() - startT;
   cout << "snapImage() took " << endT << " ms" << endl;
   startT = GetTickCount();
   pCamBuf = core.getImage();
   memcpy(pBuf, pCamBuf, core.getImageBufferSize());
   endT = GetTickCount() - startT;
   cout << "getImage() took " << endT << " ms" << endl;
   delete[] pBuf;

}

void TestNikonTE2000(CMMCore& core)
{
   const char* hubName = "HUB";
   const char* objName = "OBJ";
   const char* focusName = "Z";
   const char* filterName = "FILTER";
   const char* pathName = "PATH";
   const char* lampName = "LAMP";

   string ver = core.getProperty(hubName, "Version");
   cout << "Version: " << ver << endl;
   string model = core.getProperty(hubName, "ModelType");
   cout << "ModelType: " << model << endl;

   // lamp
   string onOff = core.getProperty(lampName, "OnOff");
   core.setProperty(lampName, "OnOff", "1");

   // objective
   int state = core.getState(objName);
   cout << "Obj state: " << state << endl;

   // focus
   //core.setProperty(focusName, "Position", "1000.0");
   //double pos = core.getPosition(focusName);
   //cout << "Focus position: " << pos << endl;
   //core.setPosition(focusName, 1200.0);
   //pos = core.getPosition(focusName);
   //cout << "Focus position: " << pos << endl;

   state = core.getState(filterName);
   core.setState(filterName, 4);
   core.waitForDevice(filterName);
   state = core.getState(filterName);
   cout << "Filter state: " << state << endl;
   core.setState(filterName, 2);
   core.waitForDevice(filterName);
   state = core.getState(filterName);
   cout << "Filter state: " << state << endl;

   //state = core.getState(pathName);
   //cout << "Path state: " << state << endl;

   //core.setState(pathName, 3);
   //state = core.getState(pathName);
   //cout << "Path state: " << state << endl;

}
void TestZeissMTBFocus(CMMCore& core)
{
   const char* focus = "Z";
   core.setProperty("Stand", "LightManager", "0");

   // test the focus stage
   cout << "Testing focus..." << endl;
   cout << "z = " << core.getPosition(focus) << endl;
   core.setPosition(focus, core.getPosition(focus) + 0.7);
   core.waitForDevice(focus);
   cout << "new z = " << core.getPosition(focus) << endl;

   core.setPosition(focus, 6700.0);
   core.waitForDevice(focus);
   cout << "z = " << core.getPosition(focus) << endl;
   core.setPosition(focus, core.getPosition(focus) + 0.7);
   core.waitForDevice(focus);
   cout << "new z = " << core.getPosition(focus) << endl;


}

void TestPrior(CMMCore& core)
{
   //const char* shutter1 = "Fluo-Shutter";
   //const char* shutter2 = "Trans-Shutter";

   //cout << "Shutter 1:" << endl;
   //cout << "State: " << core.getProperty(shutter1, "State") << endl;
   ////core.setProperty(shutter1, "State", "1");
   //core.setShutterOpen(true);
   //cout << core.getProperty(shutter1, "State") << endl;
   ////core.setProperty(shutter1, "State", "0");
   //core.setShutterOpen(false);
   //cout << core.getProperty(shutter1, "State") << endl;

   //cout << "Shutter 2:" << endl;
   //cout << "State: " << core.getProperty(shutter2, "State") << endl;
   //core.setProperty(shutter2, "State", "1");
   //cout << core.getProperty(shutter2, "State") << endl;
   //core.setProperty(shutter2, "State", "0");
   //cout << core.getProperty(shutter2, "State") << endl;

   core.setXYPosition("XY", 0.0, 0.0);
   core.waitForDevice("XY");

   core.setXYPosition("XY", 10000.0, 10000.0);
   core.waitForDevice("XY");

   double x,y;
   core.getXYPosition("XY", x, y);
   
   cout << "XY position = " << x << "," << y << endl;
}

void TestDTOL(CMMCore& core)
{
   const char* shutter = "DTOL-Shutter";
   const char* sw = "DTOL-Switch";
   const char* dac = "DTOL-DAC";

   cout << "Switch" << endl;
   core.setState(sw, 255);
   cout << "State: " << core.getState(sw) << endl;

   cout << "Shutter" << endl;
   core.setShutterOpen(true);
   cout << core.getShutterOpen() << endl;
   core.setShutterOpen(false);
   cout << core.getShutterOpen() << endl;

   cout << "DAC" << endl;
   core.setProperty(dac, "Volts", "4.5");
}

void TestAndor(CMMCore& core)
{
   unsigned height = core.getImageHeight();
   unsigned width = core.getImageWidth();

   //cout << "Setting bin size 4..." << endl;
   //core.setProperty("Camera", "Binning", "4");
   //height = core.getImageHeight();
   //width = core.getImageWidth();
   //core.snapImage();
   //core.getImage();

   const double exposure = 500.0;
   cout << "Setting bin size 2..." << endl;
   core.setProperty("Camera", "Binning", "2");
   height = core.getImageHeight();
   width = core.getImageWidth();
   core.snapImage();
   core.getImage();

   cout << "Setting bin size 1..." << endl;
   core.setProperty("Camera", "Binning", "1");
   height = core.getImageHeight();
   width = core.getImageWidth();
   core.setExposure(exposure);

   cout << "Setting ROI..." << endl;
   core.setROI(4, 4, 64, 64);
   height = core.getImageHeight();
   width = core.getImageWidth();
   core.snapImage();
   core.getImage();

   unsigned char* pBuf = new unsigned char[core.getImageBufferSize()];
   long startT = GetTickCount();
   core.snapImage();
   long endT = GetTickCount() - startT;
   cout << "snapImage() took " << endT << " ms, with exp = " << exposure << " ms" << endl;
   startT = GetTickCount();
   void* pCamBuf = core.getImage();
   memcpy(pBuf, pCamBuf, core.getImageBufferSize());
   endT = GetTickCount() - startT;
   cout << "getImage() took " << endT << " ms" << endl;
   delete[] pBuf;

   core.setProperty("Camera", "ShutterMode", "Open");
   core.snapImage();
   core.getImage();

   core.setProperty("Camera", "ShutterMode", "Auto");
   core.snapImage();
   core.getImage();
}

void TestSP(CMMCore& core)
{
   unsigned height = core.getImageHeight();
   unsigned width = core.getImageWidth();

   const double exposure = 500.0;
   core.setExposure(exposure);

   unsigned char* pBuf = new unsigned char[core.getImageBufferSize()];
   long startT = GetTickCount();
   core.snapImage();
   long endT = GetTickCount() - startT;
   cout << "snapImage() took " << endT << " ms, with exp = " << exposure << " ms" << endl;
   startT = GetTickCount();
   void* pCamBuf = core.getImage();
   memcpy(pBuf, pCamBuf, core.getImageBufferSize());
   endT = GetTickCount() - startT;
   cout << "getImage() took " << endT << " ms" << endl;
   delete[] pBuf;
}

void TestNikon(CMMCore& core)
{
   //const char* devName = "Z";
   //double z = core.getPosition(devName);
   //core.setPosition(devName, 100.0);

   const char* devName = "TIRFShutter";
   core.setProperty(devName, "State", "1");
   string prop = core.getProperty(devName, "State");
}

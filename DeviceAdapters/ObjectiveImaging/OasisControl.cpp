///////////////////////////////////////////////////////////////////////////////
// FILE:          OasisControl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Oasis Controller (Objective Imaging)
//
// AUTHOR:        Egor Zindy, egor.zindy@manchester.ac.uk
//                mostly based on NiMotionControl.cpp by
//                Brian Ashcroft, ashcroft@leidenuniv.nl
//
// COPYRIGHT:     University of Manchester, 2014 (OasisControl.cpp)
//                Leiden University, Leiden, 2009 (NiMotionControl.cpp)
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
// vim: set autoindent tabstop=3 softtabstop=3 shiftwidth=3 expandtab textwidth=78:

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf
#endif

#include "OasisControl.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
//#include "ZStage.h"

using namespace std;

// External names used used by the rest of the system
// to load particular device from the "DemoCamera.dll" library

const char* g_XYStageDeviceName = "Oasis_XYStage";
//const char* g_ZStageDeviceName = "Oasis_ZStage";

// windows DLL entry code
#ifdef WIN32
   BOOL APIENTRY DllMain( HANDLE /*hModule*/,
                          DWORD  ul_reason_for_call,
                          LPVOID /*lpReserved*/
                   )
   {
      switch (ul_reason_for_call)
      {
      case DLL_PROCESS_ATTACH:
      case DLL_THREAD_ATTACH:
      case DLL_THREAD_DETACH:
      case DLL_PROCESS_DETACH:
         break;
      }
       return TRUE;
   }
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   int nRet = DEVICE_OK;

   if (oasisInitialized == false)
   {
      // Set the hardware mode to use the controller
      OI_SetHardwareMode( OI_OASIS );

      // Open the controller
      nRet = OI_Open();

      // if the open failed, exit
      if (nRet == OI_OK)
         oasisInitialized = true;

   }

   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "Oasis XY stage");
   //RegisterDevice(g_ZStageDeviceName, MM::StageDevice, "Oasis Z or single axis stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_XYStageDeviceName) == 0)
   {
      // create stage
      return new OasisXYStage();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}



OasisXYStage::OasisXYStage() :
   stepSize_um_(0.08),
   posX_um_(0.0),
   posY_um_(0.0),
   busy_(false),
   initialized_(false),

   lowerLimitX_um_(0.0),
   upperLimitX_um_(20000.0),
   lowerLimitY_um_(0.0),
   upperLimitY_um_(20000.0),
   BoardID(0)
{
   InitializeDefaultErrorMessages();

   if (oasisInitialized == false)
   {
      // Set the hardware mode to use the controller
      OI_SetHardwareMode( OI_OASIS );

      // Open the controller
      int nRet = OI_Open();

      // if the open failed, exit
      if (nRet == OI_OK)
         oasisInitialized = true;

      LogInit();
      tmpMessage << "OI_Open() - Hardware initialized!";
      LogIt();
   }
}

OasisXYStage::~OasisXYStage()
{
   Shutdown();
}

void OasisXYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}

int OasisXYStage::Initialize()
{
   int pnNumber=0;
   int nRet = DEVICE_OK;

   if (oasisInitialized == false)
   {
      // Set the hardware mode to use the controller
      OI_SetHardwareMode( OI_OASIS );

      // Open the controller
      nRet = OI_Open();

      // if the open failed, exit
      if (nRet == OI_OK)
         oasisInitialized = true;

      LogInit();
      tmpMessage << "OI_Open() - Hardware initialized!";
      LogIt();
   }

   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------

   // Name
   nRet = CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);
   if (nRet != DEVICE_OK)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Oasis controller to control an XY stage", MM::String, true);
   if (nRet != DEVICE_OK)
      return nRet;

   //Board ID
   CPropertyAction* pAct = new CPropertyAction (this, &OasisXYStage::OnBoardID);
   nRet = CreateProperty("Board ID","0",MM::Integer,false, pAct);
   assert(nRet == DEVICE_OK);

   nRet = DEVICE_OK;
   OI_CountCards(&pnNumber);

   for (int i = 0; i < pnNumber; i++)
   {
      nRet += AddAllowedValue("Board ID", CDeviceUtils::ConvertToString(i));
   }
   BoardID = 0;

   assert(nRet == DEVICE_OK);
   if (nRet != DEVICE_OK)
      return nRet;

   //Initializing the board;
   /*
   unsigned short lpXStatus, lpYStatus;
   OI_SelectCard(BoardID);
   OI_ReadStatusXY(&lpXStatus, &lpYStatus);

   LogInit();
   tmpMessage << "OI_ReadStatusXY() - lpXStatus=" << lpXStatus << ", lpYStatus=" << lpYStatus;
   LogIt();

   if ((lpXStatus & S_INITIALIZED)==0 || (lpYStatus & S_INITIALIZED)==0)
   {
      nRet = MessageBox(HWND_DESKTOP,
         L"Please ensure that the microscope objectives and condensor are well clear of the stage and that nothing will obstruct the stage over the full range of its travel.",
         L"The Oasis stage must be initialised",
         MB_OK | MB_ICONWARNING | MB_SYSTEMMODAL | MB_SETFOREGROUND);

      OI_InitializeXY();
   }
   */

   initialized_ = true;

   return DEVICE_OK;
}

int OasisXYStage::OnBoardID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)BoardID);
   }
   else if (eAct == MM::AfterSet)
   {
      long ID;
      pProp->Get(ID);
      BoardID = (int)ID;
   }

   return DEVICE_OK;
}

int OasisXYStage::SetPositionUm(double x, double y)
{
   OI_SelectCard(BoardID);
   OI_MoveToXY(x, y, 1);

   return DEVICE_OK;
}

int OasisXYStage::SetPositionSteps(long x, long y)
{
   OI_SelectCard(BoardID);
   OI_MoveToXY_Abs(x, y, 1);

   return DEVICE_OK;
}

int OasisXYStage::Home()
{
   double xMin, yMin, xMax, yMax;

   //Selecting the right Oasis board
   OI_SelectCard(BoardID);

   //This will home the Oasis stage
   OI_InitializeXY();

   //Centering the stage
   GetLimitsUm(xMin, xMax, yMin, yMax);
   return SetPositionUm((xMin+xMax)/2.,(yMin+yMax)/2.);
}

int OasisXYStage::SetOrigin()
{
   double xMin, yMin;

   OI_SelectCard(BoardID);
   OI_SetOriginXY();

   GetPositionUm(xMin, yMin);
   lowerLimitX_um_ = xMin;
   lowerLimitY_um_ = yMin;

   return DEVICE_OK;
}

bool OasisXYStage::Busy()
{
   BOOL isBusy = false;
   int pnAxisCount = 0;

   OI_SelectCard(BoardID);
   OI_GetCardAxisCount (BoardID, &pnAxisCount);
   for (int i=0; i<pnAxisCount;i++)
   {
      OI_ReadAxisMoving(i,&isBusy);
      if (isBusy)
         break;
   }
   return isBusy?true:false;
}

int OasisXYStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }

   if (oasisInitialized)
   {
      OI_Close();
      oasisInitialized = false;
   }

   return DEVICE_OK;
}

int OasisXYStage::GetPositionUm(double& x, double& y)
{
   double pdX, pdY;
   int nRet;

   OI_SelectCard(BoardID);
   nRet = OI_ReadXY(&pdX, &pdY);
   x = pdX;
   y = pdY;

   posX_um_ = x;
   posY_um_ = y;

   return nRet;
}

int OasisXYStage::GetPositionSteps(long& x, long& y)
{
   long plX, plY;
   int nRet;

   OI_SelectCard(BoardID);
   nRet = OI_ReadXY_Abs(&plX, &plY);
   x = plX;
   y = plY;

   return nRet;
}

int OasisXYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
   BOOL pbInit;

   OI_SelectCard(BoardID);
   OI_GetFullTravelXY(&lowerLimitX_um_, &upperLimitX_um_, &lowerLimitY_um_, &upperLimitY_um_ ,&pbInit);

   xMin = lowerLimitX_um_;
   xMax = upperLimitX_um_;
   yMin = lowerLimitY_um_;
   yMax = upperLimitY_um_;

   return DEVICE_OK;
}

int OasisXYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
   BOOL pbInit;
   unsigned long xmi,xma,ymi,yma;

   OI_SelectCard(BoardID);
   OI_GetFullTravelXY(&lowerLimitX_um_, &upperLimitX_um_, &lowerLimitY_um_, &upperLimitY_um_ ,&pbInit);

   //converting x and y
   OI_MicronsToAbsoluteX(lowerLimitX_um_,&xmi);
   OI_MicronsToAbsoluteX(upperLimitX_um_,&xma);
   OI_MicronsToAbsoluteY(lowerLimitY_um_,&ymi);
   OI_MicronsToAbsoluteY(upperLimitY_um_,&yma);

   xMin = (long)xmi;
   xMax = (long)xma;
   yMin = (long)ymi;
   yMax = (long)yma;

   return DEVICE_OK;
}

int OasisXYStage::Stop()
{
   OI_SelectCard(BoardID);
   OI_HaltXY();

   return DEVICE_OK;
}

void OasisXYStage::LogInit()
{
   tmpMessage = std::stringstream();
   tmpMessage << "OasisXYStage-" << BoardID << " ";
}

void OasisXYStage::LogIt()
{
   LogMessage(tmpMessage.str().c_str());
   LogInit();
}

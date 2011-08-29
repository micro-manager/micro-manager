///////////////////////////////////////////////////////////////////////////////
// FILE:       mcu28.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Zeiss CAN bus controller, see Zeiss CAN bus documentation
//                XYStage with MCU28
//                
// AUTHOR:        Nico Stuurman, 1/16/2006 - 5/14/2006
//                automatic device detection by Karl Hoover
//                mcu28, Nenad Amodaj, 2011
//
// COPYRIGHT:     University of California, San Francisco, 2007
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  


#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "ZeissCAN.h"
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>

extern ZeissHub g_hub;
extern const char* g_ZeissXYStage;

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// XYStage mcu28
///////////////////////////////////////////////////////////////////////////////
XYStage::XYStage() :
   initialized_ (false),
   stepSizeUm_(0.2) // assuming fixed step size at 0.2um
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the XYStage to work");
   SetErrorText(ERR_NO_XY_DRIVE, "No XY stage with MCU28 found in this microscope");
}

XYStage::~XYStage()
{
   Shutdown();
}

void XYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissXYStage);
}

int XYStage::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // set property list
   // ----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_ZeissXYStage, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "XY Stage", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = GetXYFirmwareVersion();
   if (ret != DEVICE_OK)
      return ERR_NO_XY_DRIVE;

   // Firmware version
   ret = CreateProperty("XY firmware", xyFirmware_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // X and Y positions
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnX);
   ret = CreateProperty("X_um", "0.0", MM::Float, false, pAct);
   if (DEVICE_OK != ret)
      return ret;

   // X and Y positions
   pAct = new CPropertyAction (this, &XYStage::OnY);
   ret = CreateProperty("Y_um", "0.0", MM::Float, false, pAct);
   if (DEVICE_OK != ret)
      return ret;

   // just get position once at the start to initialize cached values
   double x,y;
   GetPositionUm(x, y);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int XYStage::Shutdown()
{
   initialized_ = false;

   return DEVICE_OK;
}

bool XYStage::Busy()
{
   // TODO: figure out how to get a busy signal on MF firmware
   if (firmware_ == "MF")
      return false;

   const char * commandX = "NPXm1";
   const char * commandY = "NPYm1";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  commandX);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("ExecuteCommand failed in XYStage::Busy");
      return false; // error, so say we're not busy
   }

   // first two chars should read 'PN'
   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("GetAnswer failed in XYStage::Busy");
      return false; // error, so say we're not busy
   }

   // Note: if the controller reports that the motors are moving or settling, we'll consider the z-drive to be busy
   if (response.substr(0,2) == "PN") 
   {
      unsigned long status = strtol(response.substr(2,1).c_str(), NULL, 16);
      status &= 0x01;
      if (status != 0)
         return true;
   }

   // Repeat for Y
   ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  commandY);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("ExecuteCommand failed in XYStage::Busy");
      return false; // error, so say we're not busy
   }

   // first two chars should read 'PN'
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("GetAnswer failed in XYStage::Busy");
      return false; // error, so say we're not busy
   }

   // Note: if the controller reports that the motors are moving or settling, we'll consider the z-drive to be busy
   if (response.substr(0,2) == "PN") 
   {
      unsigned long status = strtol(response.substr(2,1).c_str(), NULL, 16);
      status &= 0x01;
      if (status != 0)
         return true;
   }

   return false;
}

int XYStage::SetPositionSteps(long stepsX, long stepsY)
{
   // the hard part is to get the formatting of the string right.
   // it is a hex number from 800000 .. 7FFFFF, where everything larger than 800000 is a negative number!?
   // We can speed up communication by skipping leading 0s, but that makes the following more complicated:
   char tmp[98];
   // convert the steps into a twos-complement 6bit number
   if (stepsX < 0)
      stepsX = stepsX + 0xffffff + 1;
   snprintf(tmp, 9, "%08lX", stepsX);
   string tmp2 = tmp;
   ostringstream cmdX;
   cmdX << "NPXT" << tmp2.substr(2,6).c_str();
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmdX.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // do the same for YAxis
   if (stepsY < 0)
      stepsY = stepsY + 0xffffff + 1;
   snprintf(tmp, 9, "%08lX", stepsY);
   tmp2 = tmp;
   ostringstream cmdY;
   cmdY << "NPYT" << tmp2.substr(2,6).c_str();
   ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmdY.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * Requests current z postion from the controller.  This function does the actual communication
 */
int XYStage::GetPositionSteps(long& stepsX, long& stepsY)
{
   const char* cmdX ="NPXp" ;
   const char* cmdY ="NPYp" ;

   // retrieve x axis
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmdX);
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;

   if (response.substr(0,2) == "PN") 
   {
      stepsX = strtol(response.substr(2).c_str(), NULL, 16);
   }
   else  
      return ERR_UNEXPECTED_ANSWER;

   // To 'translate' 'negative' numbers according to the Zeiss schema (there must be a more elegant way of doing this:
   long sign = strtol(response.substr(2,1).c_str(), NULL, 16);
   if (sign > 7)  // negative numbers
   {
      stepsX = stepsX - 0xFFFFFF - 1;
   }

   // retrieve y axis
   ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmdY);
   if (ret != DEVICE_OK)
      return ret;

   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;

   if (response.substr(0,2) == "PN") 
   {
      stepsY = strtol(response.substr(2).c_str(), NULL, 16);
   }
   else  
      return ERR_UNEXPECTED_ANSWER;

   // To 'translate' 'negative' numbers according to the Zeiss schema (there must be a more elegant way of doing this:
   sign = strtol(response.substr(2,1).c_str(), NULL, 16);
   if (sign > 7)  // negative numbers
   {
      stepsY = stepsY - 0xFFFFFF - 1;
   }

   return DEVICE_OK;
}

int XYStage::SetOrigin()
{
   const char* cmdX ="NPXP0" ;
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmdX);
   if (ret != DEVICE_OK)
      return ret;

   const char* cmdY ="NPYP0" ;
   ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmdY);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}
int XYStage::GetLimitsUm(double&, double&, double&, double&)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::GetStepLimits(long&, long&, long&, long&)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

double XYStage::GetStepSizeXUm()
{
   return stepSizeUm_;
}

double XYStage::GetStepSizeYUm()
{
   return stepSizeUm_;
}

int XYStage::Home()
{
   return DEVICE_OK;
}

int XYStage::Stop()
{
   const char* cmdX ="NPXS" ;
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmdX);
   if (ret != DEVICE_OK)
      return ret;

   const char* cmdY ="NPYS" ;
   ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmdY);
   if (ret != DEVICE_OK)
      return ret;

    return DEVICE_OK;
}

// TODO: this doesn't work for MCU28
int XYStage::GetXYFirmwareVersion()
{
   // get firmware info
   const char * command = "NPTv0";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
   if (ret != DEVICE_OK)
      return ret;

   // first two chars should read 'PF'
   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;
   if (response.substr(0,2).compare("PN") == 0) 
   {
      xyFirmware_ = response.substr(2);
      firmware_ = response.substr(2,2);
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int XYStage::OnX(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) 
   {
      pProp->Set(GetCachedXUm());
   } 
   else if (eAct == MM::AfterSet) 
   {
      double xUm;
      pProp->Get(xUm);
      SetPositionUm(xUm, GetCachedYUm());
   }

   return DEVICE_OK;
}

int XYStage::OnY(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) 
   {
      pProp->Set(GetCachedYUm());
   } 
   else if (eAct == MM::AfterSet) 
   {
      double yUm;
      pProp->Get(yUm);
      SetPositionUm(GetCachedYUm(), yUm);
   }

   return DEVICE_OK;
}

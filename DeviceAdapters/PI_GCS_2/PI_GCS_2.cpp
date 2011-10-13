///////////////////////////////////////////////////////////////////////////////
// FILE:          PI_GCS_DLL.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PI GCS DLL Controller Driver
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/28/2006
//                Steffen Rau, s.rau@pi.ws, 10/03/2008
// COPYRIGHT:     University of California, San Francisco, 2006
//                Physik Instrumente (PI) GmbH & Co. KG, 2008
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
// CVS:           $Id: PI_GCS_2.cpp,v 1.13, 2011-07-26 11:24:54Z, Steffen Rau$
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#else
#endif

// this adapter can use PI modules to communicate with older firmware
// versions or with interface not supported by micro-manager
// e.g. the C-843 controller is a PCI board needing a special driver,
// and the E-710 controller need a translation layer to understand
// These additional modules are provided on the PI product CDs and are available
// for Windows platforms, some of these libraries are also available for Linux
// No modules are available for MAC OS X, so in this code the "DLL" controller
// calling these modules is disabled for MAC OS X by using the preprocessor constand "__APPLE__"

#include "PI_GCS_2.h"
#ifndef __APPLE__
#include "PIGCSControllerDLL.h"
#endif
#include "PIGCSControllerCom.h"
#include "PIZStage_DLL.h"
#include "PIXYStage_DLL.h"
#include "../../MMDevice/ModuleInterface.h"
#include <algorithm>
#include <locale.h>

bool ci_equal(char ch1, char ch2)
{
	return tolower((unsigned char)ch1) == tolower((unsigned char)ch2);
}

size_t ci_find(const std::string& str1, const std::string& str2)
{
	std::string::const_iterator pos = std::search(str1.begin(), str1.end(), str2.begin(), str2.end(), ci_equal);
	if (pos == str1. end ( ))
		return std::string::npos;
	else
		return pos - str1. begin ( );
}

bool GetValue(const std::string& sMessage, long& lval)
{
	std::string svalue = ExtractValue(sMessage);
   char *pend;
   const char* szValue = svalue.c_str();
   long lValue = strtol(szValue, &pend, 0);
   
   // return true only if scan was stopped by spaces, linefeed or the terminating NUL and if the
   // string was not empty to start with
   if (pend != szValue)
   {
      while( *pend!='\0' && (*pend==' '||*pend=='\n')) pend++;
      if (*pend=='\0')
      {
         lval = lValue;
         return true;
      }
   }
   return false;
}

bool GetValue(const std::string& sMessage, double& dval)
{
   std::string svalue = ExtractValue(sMessage);
   printf("GetValue(\"%s\", double)\n", sMessage.c_str());
#ifdef WIN32
   _locale_t loc = _create_locale(LC_ALL, "eng");
   char point = '.';
#else
   std::locale loc("");
   char point = std::use_facet<std::numpunct<char> >(loc).decimal_point();
#endif

   for(size_t p=0; p<svalue.length(); p++)
   {
     if (svalue[p] == '.' || svalue[p] == ',')
       svalue[p] = point;
   }

   char *pend;
   const char* szValue = svalue.c_str();

#ifdef WIN32
   double dValue =_strtod_l(szValue, &pend, loc);
#else
   double dValue = strtod(szValue, &pend);
#endif

   // return true only if scan was stopped by spaces, linefeed or the terminating NUL and if the
   // string was not empty to start with
   if (pend != szValue)
   {
      while( *pend!='\0' && (*pend==' '||*pend=='\n')) pend++;
      if (*pend=='\0')
      {
         dval = dValue;
         return true;
      }
   }
   return false;
}

std::string ExtractValue(const std::string& sMessage)
{
	std::string value(sMessage);
   // value is after last '=', if any '=' is found
   size_t p = value.find_last_of('=');
   if ( p != std::string::npos )
       value.erase(0,p+1);
   
   // trim whitspaces from right ...
   p = value.find_last_not_of(" \t\r\n");
   if (p != std::string::npos)
       value.erase(++p);
   
   // ... and left
   p = value.find_first_not_of(" \n\t\r");
   if (p == std::string::npos)
      return "";
   
   value.erase(0,p);
   return value;
}



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(PIZStage::DeviceName_, "PI GCS Z-stage");
   AddAvailableDeviceName(PIXYStage::DeviceName_, "PI GCS XY-stage");
#ifndef __APPLE__
   AddAvailableDeviceName(PIGCSControllerDLLDevice::DeviceName_, "PI GCS DLL Controller");
#endif
   AddAvailableDeviceName(PIGCSControllerComDevice::DeviceName_, "PI GCS Controller");

#ifndef __APPLE__
   AddAvailableDeviceName("C-843", "PI C-843 Controller");
#endif
   AddAvailableDeviceName("C-663.11", "PI C-663.11 Controller");
   AddAvailableDeviceName("C-863.11", "PI C-863.11 Controller");
   AddAvailableDeviceName("C-867", "PI C-867 Controller");
   AddAvailableDeviceName("E-517/E-545", "PI E-517/E-545 Controller");
#ifndef __APPLE__
   AddAvailableDeviceName("E-710", "PI E-710 Controller");
#endif
   AddAvailableDeviceName("E-712", "PI E-712 Controller");
   AddAvailableDeviceName("E-753", "PI E-753 Controller");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, PIZStage::DeviceName_) == 0)
   {
      PIZStage* s = new PIZStage();
      return s;
   }
   if (strcmp(deviceName, PIXYStage::DeviceName_) == 0)
   {
      PIXYStage* s = new PIXYStage();
      return s;
   }

#ifndef __APPLE__
   if (strcmp(deviceName, PIGCSControllerDLLDevice::DeviceName_) == 0)
   {
      PIGCSControllerDLLDevice* s = new PIGCSControllerDLLDevice();
      s->CreateProperties();
      return s;
   }
#endif

   if (strcmp(deviceName, PIGCSControllerComDevice::DeviceName_) == 0)
   {
      PIGCSControllerComDevice* s = new PIGCSControllerComDevice();
      s->CreateProperties();
      return s;
   }

   if (	(strcmp(deviceName, "C-867") == 0)
	||	(strcmp(deviceName, "C-663.11") == 0)
	||	(strcmp(deviceName, "C-863.11") == 0)	)
   {
      PIGCSControllerComDevice* s = new PIGCSControllerComDevice();
      s->SetFactor_UmToDefaultUnit(0.001);
      s->CreateProperties();
      return s;
   }

   if (strcmp(deviceName, "E-517/E-545") == 0)
   {
      PIGCSControllerComDevice* s = new PIGCSControllerComDevice();
      s->SetFactor_UmToDefaultUnit(1.0);
      s->CreateProperties();
      return s;
   }

#ifndef __APPLE__
   if (strcmp(deviceName, "E-710") == 0)
   {
      PIGCSControllerDLLDevice* s = new PIGCSControllerDLLDevice();
      s->SetDLL("E7XX_GCS_DLL.dll");
      s->SetInterface("RS-232", "");
      s->ShowInterfaceProperties(true);
      s->CreateProperties();
      return s;
   }

   if (strcmp(deviceName, "C-843") == 0)
   {
      PIGCSControllerDLLDevice* s = new PIGCSControllerDLLDevice();
      s->SetDLL("C843_GCS_DLL.dll");
      s->SetInterface("PCI", "1");
      s->ShowInterfaceProperties(false);
      s->CreateProperties();
      return s;
   }
#endif  

   if( (strcmp(deviceName, "E-712") == 0)
    || (strcmp(deviceName, "E-753") == 0) )
   {
      PIGCSControllerComDevice* s = new PIGCSControllerComDevice();
      s->SetFactor_UmToDefaultUnit(1.0);
      s->CreateProperties();
      return s;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}
 

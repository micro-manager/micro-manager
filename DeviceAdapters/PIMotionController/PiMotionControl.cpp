///////////////////////////////////////////////////////////////////////////////
// FILE:          PIMotionControl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The example implementation of the demo camera.
//                Simulates generic digital camera and associated automated
//                microscope devices and enables testing of the rest of the
//                system without the need to connect to the actual hardware. 
//         
// AUTHOR:        Brian Ashcroft, ashcroft@physics.leidenuniv.nl
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/08/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
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


#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "PIMotionControl.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
using namespace std;

// External names used used by the rest of the system
// to load particular device from the "DemoCamera.dll" library

const char* g_StageDeviceName = "PI_Mercury_Stage";


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
 
   AddAvailableDeviceName(g_StageDeviceName, "PI Mercury Step stage");
   
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_StageDeviceName) == 0)
   {
      // create stage
      return new CPIMotionStage();
   }
  

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// CPIMotionStage implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

CPIMotionStage::CPIMotionStage() : 
   stepSize_um_(0.025),
   pos_um_(0.0),
   busy_(false),
   initialized_(false),
   lowerLimit_(-25000.0),
   upperLimit_(25000.0),
   COMPort(1),
   Baud(9600),
   DevAxis(1)
{
   InitializeDefaultErrorMessages();
}

CPIMotionStage::~CPIMotionStage()
{
   Shutdown();
}

void CPIMotionStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

int CPIMotionStage::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

  
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Pi Mercury and Mecury Step Driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Position
   // --------
   /*pAct = new CPropertyAction (this, &CPIMotionStage::OnPosition);
   ret = CreateProperty(MM::g_Keyword_Position, "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;*/

   CPropertyAction*  pAct = new CPropertyAction (this, &CPIMotionStage::OnAxis);
   int nRet;
   nRet = CreateProperty("Axis Number", "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> binValues;
   binValues.push_back("1");
   binValues.push_back("2");
   binValues.push_back("3");
   binValues.push_back("4");
   binValues.push_back("5");
   binValues.push_back("6");
   binValues.push_back("7");
   binValues.push_back("8");
   binValues.push_back("9");
   binValues.push_back("10");
   binValues.push_back("11");
   binValues.push_back("12");
   binValues.push_back("13");
   binValues.push_back("14");
   binValues.push_back("15");
   binValues.push_back("16");
   nRet = SetAllowedValues("Axis Number", binValues);
   if (nRet != DEVICE_OK)
      return nRet;


   pAct = new CPropertyAction (this, &CPIMotionStage::OnCOM);
   
   nRet = CreateProperty("COM Number", "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> binValuesC;
   binValuesC.push_back("1");
   binValuesC.push_back("2");
   binValuesC.push_back("3");
   binValuesC.push_back("4");
   binValuesC.push_back("5");
   binValuesC.push_back("6");
   binValuesC.push_back("7");
   binValuesC.push_back("8");
   binValuesC.push_back("9");
   binValuesC.push_back("10");
   binValuesC.push_back("11");
   binValuesC.push_back("12");
   binValuesC.push_back("13");
   binValuesC.push_back("14");
   binValuesC.push_back("15");
   binValuesC.push_back("16");
   nRet = SetAllowedValues("COM Number", binValuesC);
   if (nRet != DEVICE_OK)
      return nRet;



   pAct = new CPropertyAction (this, &CPIMotionStage::OnSetVelocity);
   nRet = CreateProperty("Move Velocity","10000",MM::Integer,false, pAct);
   assert(nRet == DEVICE_OK);

   if (nRet != DEVICE_OK)
      return nRet;

   pAct = new CPropertyAction (this, &CPIMotionStage::OnSetAcceleration);
   nRet = CreateProperty("Move Acceleration","150000",MM::Integer,false, pAct);
   assert(nRet == DEVICE_OK);

   if (nRet != DEVICE_OK)
      return nRet;


    pAct = new CPropertyAction (this, &CPIMotionStage::OnSetBaud);
   nRet = CreateProperty("Baud Rate","9600",MM::Integer,false, pAct);
   assert(nRet == DEVICE_OK);

   if (nRet != DEVICE_OK)
      return nRet;

   vector<string> binValuesC1;
   binValuesC1.push_back("2400");
   binValuesC1.push_back("4800");
   binValuesC1.push_back("9600");
   binValuesC1.push_back("19200");
   binValuesC1.push_back("38400");
   binValuesC1.push_back("57600");
   binValuesC1.push_back("115200");
   nRet = SetAllowedValues("Baud Rate", binValuesC1);
   if (nRet != DEVICE_OK)
      return nRet;



   pAct = new CPropertyAction (this, &CPIMotionStage::OnSetStepSize);
   nRet = CreateProperty("Step Size","0.025",MM::Float ,false, pAct);
   assert(nRet == DEVICE_OK);


   if (nRet != DEVICE_OK)
      return nRet;


   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   MMC_COM_close();

   ret=MMC_COM_open(COMPort,Baud);
   if (ret != DEVICE_OK)
      return ret;

   ret=MMC_setDevice(DevAxis);
   if (ret != DEVICE_OK)
      return ret;

   ret=MMC_sendCommand("DC800");
   if (ret != DEVICE_OK)
      return ret;

   ret=MMC_sendCommand("SV25000");
   if (ret != DEVICE_OK)
      return ret;

   ret=MMC_sendCommand("SA150000");
   if (ret != DEVICE_OK)
      return ret;

   	int steps= MMC_getPos();	
	pos_um_=((double)(steps)/stepSize_um_);
    

   initialized_ = true;

   return DEVICE_OK;
}

int CPIMotionStage::GetPositionSteps(long& steps)
{
	steps=(long) MMC_getPos();	
	//return (Steps);
	return (0);
}
int CPIMotionStage::GetPositionUm(double& pos)
{
	int steps= MMC_getPos();	
	pos=((double)(steps)*stepSize_um_);
    pos_um_=pos;
	return (0);
}// {pos = pos_um_; return DEVICE_OK;}

int CPIMotionStage::Shutdown()
{
   MMC_COM_close();
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int CPIMotionStage::SetPositionSteps(long steps)
{
    int ret;
    ret=MMC_setDevice(DevAxis);
	std::string command("MA");
    command.append(CDeviceUtils::ConvertToString(steps));

    return MMC_sendCommand((char *)command.c_str()); //"MR10000");//(const char*)command.c_str());
	//return (0);
}
int CPIMotionStage::SetPositionUm(double pos) 
{

	double d=(pos/stepSize_um_);
	int posZ=(int)d;
    if (pos > upperLimit_ || lowerLimit_ > pos)
    {
         //SetPositionSteps(posZ);
         return ERR_UNKNOWN_POSITION;
    }
	pos_um_ = pos;
	return (SetPositionSteps(posZ));//
}
///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CPIMotionStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      double pos;
      pProp->Get(pos);
	  return (SetPositionUm(pos)) ;
   }

   return DEVICE_OK;
}
   int CPIMotionStage::OnAxis(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
	   if (eAct == MM::BeforeGet)
	   {
		  // nothing to do, let the caller use cached property
	   }
	   else if (eAct == MM::AfterSet)
	   {
		  long axis;
		  pProp->Get(axis);
		  DevAxis=axis;
		  MMC_setDevice(axis);
		  return (0);
	      
	   }

	   return DEVICE_OK;
   }
   char * ConvertConstString(std::string  strSome)
   {
		size_t len = strSome.length();
		char* tmp = new char [ len + 1 ];
		strcpy( tmp, strSome.c_str() );
		return tmp;
   }
   int CPIMotionStage::OnSetVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
	   if (eAct == MM::BeforeGet)
	   {
		  // nothing to do, let the caller use cached property
	   }
	   else if (eAct == MM::AfterSet)
	   {
		  long axis;
		  pProp->Get(axis);

		  std::string command("SV");
		  command.append(CDeviceUtils::ConvertToString(axis));

	      int ret;
          ret=MMC_setDevice(DevAxis);
		  //ret=MMC_sendCommand( const_cast< char* > ( command.c_str() ));
		  char * tmp=ConvertConstString(command.c_str());
		  ret=MMC_sendCommand(tmp);
		  delete [] tmp;
		  return (ret);
	      
	   }

	   return DEVICE_OK;
   }
   int CPIMotionStage::OnSetStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
	   if (eAct == MM::BeforeGet)
	   {
		  // nothing to do, let the caller use cached property
	   }
	   else if (eAct == MM::AfterSet)
	   {
		  double stepSize;
		  pProp->Get(stepSize);
		  stepSize_um_=stepSize;		      
	   }

	   return DEVICE_OK;

   }
   int CPIMotionStage::OnSetBaud(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
	   if (eAct == MM::BeforeGet)
	   {
		  // nothing to do, let the caller use cached property
	   }
	   else if (eAct == MM::AfterSet)
	   {
		  long baud;
		  pProp->Get(baud);
          Baud=baud;
	      int ret;

		  MMC_COM_close();
          ret=MMC_COM_open(COMPort,Baud);
          if (ret != DEVICE_OK)
               return ret;

          ret=MMC_setDevice(DevAxis);
		  return (ret);
	      
	   }

	   return DEVICE_OK;

   }
   int CPIMotionStage::OnCOM(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
	   if (eAct == MM::BeforeGet)
	   {
		  // nothing to do, let the caller use cached property
	   }
	   else if (eAct == MM::AfterSet)
	   {
		  long com;
		  pProp->Get(com);
          COMPort=com;
	      int ret;

		  MMC_COM_close();
          ret=MMC_COM_open(COMPort,Baud);
          if (ret != DEVICE_OK)
               return ret;

          ret=MMC_setDevice(DevAxis);
		  return (ret);
	      
	   }

	   return DEVICE_OK;
   }

   
   int CPIMotionStage::OnSetAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
   {
	   if (eAct == MM::BeforeGet)
	   {
		  // nothing to do, let the caller use cached property
	   }
	   else if (eAct == MM::AfterSet)
	   {
		  long accel;
		  pProp->Get(accel);

		  std::string command("SA");
		  command.append(CDeviceUtils::ConvertToString(accel));

	      int ret;
          ret=MMC_setDevice(DevAxis);
		  //ret=MMC_sendCommand( const_cast< char* > ( command.c_str() ));
		  char * tmp=ConvertConstString(command.c_str());
		  ret=MMC_sendCommand(tmp);
		  delete [] tmp;
		  return (ret);
	      
	   }

	   return DEVICE_OK;
   }

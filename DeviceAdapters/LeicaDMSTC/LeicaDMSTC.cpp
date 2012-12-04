///////////////////////////////////////////////////////////////////////////////
// FILE:          LeicaDMSTC.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   LeicaDMSTC XY stage adapter.
//                                                                                     
// AUTHOR:        G. Esteban Fernandez, 27-Aug-2012
//                Based on LeicaDMR adapter by Nico Stuurman.
//				  Update 31-Oct-2012:  commented out lines 266-271
//
// COPYRIGHT:     2012, Children's Hospital Los Angeles
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

#ifdef WIN32
   //#include <windows.h>
   #define snprintf _snprintf 
#endif

#include "LeicaDMSTC.h"
#include "LeicaDMSTCHub.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"

using namespace std;

// Device strings
const char* g_LeicaDMSTCHub = "Leica DMSTC stage hub";
const char* g_LeicaDMSTCXYDrive = "Leica DMSTC XY stage";

// global hub object, very important!
LeicaDMSTCHub g_hub;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_LeicaDMSTCHub,"LeicaDMSTC Controller");
   AddAvailableDeviceName(g_LeicaDMSTCXYDrive, "XY Drive");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_LeicaDMSTCHub) == 0) {
      return new Hub();
   } else if (strcmp(deviceName, g_LeicaDMSTCXYDrive) == 0 ) {
	   return new XYStage();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// LeicaDMSTC Hub
///////////////////////////////////////////////////////////////////////////////

Hub::Hub() :
   initialized_(false),
   port_("Undefined")
{
   InitializeDefaultErrorMessages();

   // custom error messages
   SetErrorText(ERR_COMMAND_CANNOT_EXECUTE, "Command cannot be executed");
   SetErrorText(ERR_NO_ANSWER, "No answer received.  Is the Leica stage connected to the correct serial port and switched on?");
   SetErrorText(ERR_NOT_CONNECTED, "No answer received.  Is the Leica stage connected to the correct serial port and switched on?");

   // create pre-initialization properties
   // ------------------------------------

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

Hub::~Hub()
{
   Shutdown();
}

void Hub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_LeicaDMSTCHub);
}

bool Hub::Busy()
{
   return false;
}

int Hub::Initialize()
{
   int ret;
   if (!g_hub.Initialized()) {
      ret = g_hub.Initialize(*this, *GetCoreCallback());
      if (ret != DEVICE_OK)
         return ret;
   }

   // set property list
   // -----------------
   
   // Name
   ret = CreateProperty(MM::g_Keyword_Name, g_LeicaDMSTCHub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "LeicaDMSTC controller", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Version
   std::string version = g_hub.Version();
   ret = CreateProperty("Firmware version", version.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

	ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   
   return DEVICE_OK;
}

int Hub::Shutdown()
{
   if (initialized_) {
      initialized_ = false;
      g_hub.DeInitialize();
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         //return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
      g_hub.SetPort(port_.c_str());
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// LeicaDMSTC XYStage 
///////////////////////////////////////////////////////////////////////////////
XYStage::XYStage () :
	CXYStageBase<XYStage>(),
	busy_ (false),
	initialized_ (false),
	stepSize_um_(10),	

name_ (g_LeicaDMSTCXYDrive)

{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_DEVICE_NOT_FOUND, "No XY-Drive found in this microscope");
   SetErrorText(ERR_PORT_NOT_SET, "No serial port found.  Did you include the Leica DMSTC stage and set its serial port property?");
}

XYStage::~XYStage ()
{
   Shutdown();
}

//send two consecutive GetPosition commands to test if stage is moving/busy
bool XYStage::Busy()
{
	long posX1, posX2, posY1, posY2;
	
	int ret;

	ret = GetPositionSteps(posX1, posY1);
	if(ret != DEVICE_OK)
		return true;
	ret = GetPositionSteps(posX2, posY2);
	if(ret != DEVICE_OK)
		return true;

	if((posX1 == posX2) || (posY1 == posY2))
		return false;
	else
		return true;
}

void XYStage::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int XYStage::Initialize()
{
	int ret;
	if (!g_hub.Initialized()) {
		ret = g_hub.Initialize(*this, *GetCoreCallback());
		if (ret != DEVICE_OK)
			return ret;
	}

	//SET_ACK parameter set to 1 to echo non-response commands, for error checking in hub SetCommand() functions
	ret = g_hub.SetAcknowledge(*this, *GetCoreCallback(), 1);
	if (ret != DEVICE_OK)
		return ret;

	//move to origin (top left; x,y = 0,0) to reset position grid 
	ret = g_hub.ResetStage(*this, *GetCoreCallback());
	if (ret != DEVICE_OK)
		return ret;

	//wait while stage is moving to origin
	while(Busy()){
	}

	////////////////////////////////////////////////////////////////////////////
	//THIS IS NOT NECESSARY FOR NORMAL OPERATION IN Micro-Manager.
	//COMMENTED OUT TO MINIMIZE POTENTIALLY DANGEROUS & UNNEEDED STAGE MOVEMENT:
	//
	//Move to bottom right to define x,y limits, otherwise the commands
	//GET_LIMIT_X1/Y1 and GET_LIMIT_X2/Y2 (020 - 023) give three
	//question marks as return values
	//
	//ret = g_hub.InitRange(*this, *GetCoreCallback());
	//if (ret != DEVICE_OK)
	//	return ret;
	//
	//while(Busy()){  
	//}
	////////////////////////////////////////////////////////////////////////////

	ret = GetStepLimits(lowerLimitX_, upperLimitX_, lowerLimitY_, upperLimitY_);
	if (ret != DEVICE_OK)
		return ret;

/*	CPropertyAction* pAct;

	// ACCELERATION
	pAct = new CPropertyAction(this, &XYStage::OnAcceleration);
	ret = CreateProperty("Acceleration", "1", MM::Integer, false, pAct);
	if (ret != DEVICE_OK)
		return ret;
	SetPropertyLimits("Acceleration", 5, 200000); //5-200,000 um/sec^2

	// SPEED X
	pAct = new CPropertyAction(this, &XYStage::OnSpeedX);
	ret = CreateProperty("XY stage speed X", "1", MM::Integer, false, pAct);
	if (ret != DEVICE_OK)
		return ret;
	SetPropertyLimits("XY stage speed X", 5, 30000);  //30,000 um/sec

	// SPEED Y
	pAct = new CPropertyAction(this, &XYStage::OnSpeedY);
	ret = CreateProperty("XY stage speed Y", "1", MM::Integer, false, pAct);
	if (ret != DEVICE_OK)
		return ret;
	SetPropertyLimits("XY stage speed Y", 5, 30000);  //30,000 um/sec

	// EMERGENCY STOP
	pAct = new CPropertyAction (this, &XYStage::OnStop);
	ret = CreateProperty("XY stage EMERGENCY STOP", "Off", MM::String, false, pAct); 
	if (ret != DEVICE_OK) 
		return ret; 
	AddAllowedValue("XY stage EMERGENCY STOP", "Off");
	AddAllowedValue("XY stage EMERGENCY STOP", "APPLY NOW!");
*/
	// Do not implement a position property as it can lead to trouble!

	// Set timer for the Busy signal, or we'll get a time-out the first time we check the state of the shutter, for good measure, go back 5s into the past
	changedTime_ = GetCurrentMMTime() - 5000000; 

	initialized_ = true;

	return DEVICE_OK;
}

int XYStage::Shutdown()
{
	if (initialized_) initialized_ = false;
	return DEVICE_OK;
}



int XYStage::SetPositionUm(double xUm, double yUm)
{
	long xSteps = (long) (xUm / stepSize_um_);
	long ySteps = (long) (yUm / stepSize_um_);

	return SetPositionSteps(xSteps, ySteps);
}

int XYStage::SetRelativePositionUm(double xUm, double yUm)
{
	long xSteps = (long) (xUm / stepSize_um_);
	long ySteps = (long) (yUm / stepSize_um_);

	return SetRelativePositionSteps(xSteps, ySteps);
}

int XYStage::SetAdapterOriginUm(double xUm, double yUm)
{
	originXSteps_ = (long) (xUm / stepSize_um_);
	originYSteps_ = (long) (yUm / stepSize_um_);

	return DEVICE_OK;
}

int XYStage::GetPositionUm(double& positionX, double& positionY)
{
	long positionStepsX;
	long positionStepsY;
	int ret = GetPositionSteps(positionStepsX, positionStepsY);
	if (ret != DEVICE_OK)
		return ret;

	positionX = (double) positionStepsX * stepSize_um_;
	positionY = (double) positionStepsY * stepSize_um_;

	return DEVICE_OK;
}

int XYStage::GetLimitsUm(double& xMinPosUm, double& xMaxPosUm, double& yMinPosUm, double& yMaxPosUm)
{
	double xMin, yMin, xMax, yMax;

	xMin = lowerLimitX_ * stepSize_um_;
	yMin = lowerLimitY_ * stepSize_um_;
	xMax = upperLimitX_ * stepSize_um_;
	yMax = upperLimitY_ * stepSize_um_;

	
	xMinPosUm = xMin;
	yMinPosUm = yMin;
	xMaxPosUm = xMax;
	yMaxPosUm = yMax;

	return DEVICE_OK;
}

int XYStage::SetPositionSteps(long positionX, long positionY)
{
	return g_hub.SetXYAbs(*this, *GetCoreCallback(), positionX, positionY);
}

int XYStage::GetPositionSteps(long& positionX, long& positionY)
{
	return g_hub.GetXYAbs(*this, *GetCoreCallback(), positionX, positionY);
}

int XYStage::SetRelativePositionSteps(long positionX, long positionY)
{
	return g_hub.SetXYRel(*this, *GetCoreCallback(), positionX, positionY);
}

int XYStage::Home(){
	int ret = g_hub.SetXYAbs(*this, *GetCoreCallback(),0 ,0);

	if (ret != DEVICE_OK)
		return ret;
	return DEVICE_OK;
}

int XYStage::Stop(){
	int ret = g_hub.StopXY(*this, *GetCoreCallback());

	if (ret != DEVICE_OK)
		return ret;
	return DEVICE_OK;
}

int XYStage::SetOrigin() 
{
	long xStep, yStep;

	int ret = GetPositionSteps(xStep, yStep);
	
	if (ret != DEVICE_OK)
		return ret;

	originXSteps_ = xStep;
	originYSteps_ = yStep;

	return DEVICE_OK;
}

int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
	long xMinPosStep, yMinPosStep, xMaxPosStep, yMaxPosStep;
	g_hub.GetXYLowerThreshold(*this, *GetCoreCallback(), xMinPosStep, yMinPosStep);

	g_hub.GetXYUpperThreshold(*this, *GetCoreCallback(), xMaxPosStep, yMaxPosStep);

	xMin = xMinPosStep;
	yMin = yMinPosStep;
	xMax = xMaxPosStep;
	yMax = yMaxPosStep;

	return DEVICE_OK;
}

double XYStage::GetStepSizeXUm()
{
	double step = g_hub.GetStepSize(*this, *GetCoreCallback());
	stepSize_um_ = step;
	return step;
}

double XYStage::GetStepSizeYUm()
{
	double step = g_hub.GetStepSize(*this, *GetCoreCallback());
	stepSize_um_ = step;
	return step;
}


/*
 * Assume that the parameter is in um/sec!
 
int XYStage::Move(double speedX, double speedY)
{
   int speedNumberX;
   int speedNumberY;

   double minSpeedX = 5;
   double minSpeedY = 5;
   double maxSpeedX = 30000;
   double maxSpeedY = 30000;

   speedNumberX = (int) (speedX /maxSpeedX * 255);
   speedNumberY = (int) (speedY /maxSpeedY * 255);

   if (speedNumberX > 255)
      speedNumberX = 255;
   if (speedNumberX < -255)
      speedNumberX = -255;

   if (speedNumberY > 255)
      speedNumberY = 255;
   if (speedNumberY < -255)
      speedNumberY = -255;

   return g_hub.MoveXYConst(*this, *GetCoreCallback(), speedNumberX, speedNumberY);
}
*/


/*
int XYStage::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet){
		int acc;
		int ret = g_hub.GetAcceleration(*this, *GetCoreCallback(), acc);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((long)acc);
	}
	else if (eAct == MM::AfterSet)
	{  
		long acc;
		pProp->Get(acc);
		int ret = g_hub.SetAcceleration(*this, *GetCoreCallback(), (int) acc);
		if (ret != DEVICE_OK)
			return ret;
	}

	return DEVICE_OK;                                          
}

int XYStage::OnSpeedX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)   {
		int speed;
		int ret = g_hub.GetSpeedX(*this, *GetCoreCallback(), speed);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((long)speed);
	}
	else if (eAct == MM::AfterSet)                             
	{  
		long speed;
		pProp->Get(speed);
		int ret = g_hub.SetSpeedX(*this, *GetCoreCallback(), (int) speed);
		if (ret != DEVICE_OK)
			return ret;
		ret = g_hub.SetSpeedX(*this, *GetCoreCallback(), (int) speed);
		if (ret != DEVICE_OK)
			return ret;
	}

	return DEVICE_OK;                                          
}

int XYStage::OnSpeedY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)   {
		int speed;
		int ret = g_hub.GetSpeedY(*this, *GetCoreCallback(), speed);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((long)speed);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      long speed;
      pProp->Get(speed);
      int ret = g_hub.SetSpeedY(*this, *GetCoreCallback(), (int) speed);
      if (ret != DEVICE_OK)
         return ret;
      ret = g_hub.SetSpeedY(*this, *GetCoreCallback(), (int) speed);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;                                          
}

int XYStage::OnStop(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet){
		int ret = g_hub.StopXY(*this, *GetCoreCallback());
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(1.0);
	}
	else if (eAct == MM::AfterSet)

	{
		long state;
		pProp->Get(state);
	}

	return DEVICE_OK;
}
*/
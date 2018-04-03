///////////////////////////////////////////////////////////////////////////////
// FILE:          WieneckeSinske.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Wienecke & Sinske Stage Controller Driver
//                XY Stage
//             
//
// AUTHOR:        S3L GmbH, info@s3l.de, www.s3l.de,  11/21/2017
// COPYRIGHT:     S3L GmbH, Rosdorf, 2017
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
//

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "WieneckeSinske.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/DeviceBase.h"

#include <sstream>



///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

const char* g_XYStageDeviceDeviceName = "XYStage Piezo Controller";

///////////////////////////////////////////////////////////////////////////////

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_XYStageDeviceDeviceName, MM::XYStageDevice,  "Wienecke & Sinske WSB PiezoDrive CAN");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
	if (deviceName == 0)                                                      
		return 0;

	if (strcmp(deviceName, g_XYStageDeviceDeviceName) == 0)
	{
		XYStageDevice* pXYStageDevice = new XYStageDevice();
		return pXYStageDevice;
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
	delete pDevice;                                                           
}







///////////////////////////////////////////////////////////////////////////////
// CAN29Axis
//
int CAN29Axis::Initialize()
{
	int ret = StartMonitoring();
	if(ret != DEVICE_OK)
		return ret;

	// get initial positions
	ret = GetStatusCmd(actStatus_);
	if(ret != DEVICE_OK)
		return ret;

	ret = GetPositionCmd(actPosition_);
	if(ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int CAN29Axis::UnInitialize()
{
	StopMonitoring();
	Unlock();
	return DEVICE_OK;
}


int CAN29Axis::ReceiveMessageHandler(Message& msg)
{
	if(    (msg.CanSrc == canAddress_)								// from my CAN address?
		&& (msg.CmdNr == CMDNR_AXIS)								// with my command number
		&& (msg.Data.size() > 1 ) && (msg.Data[0] == devID_))		// with my deviceID
	{
		switch(msg.CmdCls)
		{
		case 0x07:   // events
			switch(msg.SubID)
			{
			case 0x01:		    // status
				MessageTools::GetULong(msg.Data, 1, actStatus_);
				isBusy_ = (actStatus_& 0x04) > 0;
				break;
			case 0x02:			// position
				MessageTools::GetLong(msg.Data, 1, actPosition_);
				break;
			}
			break;

		case 0x08:	 // answers
			break;

		case 0x09:   // completions
			switch(msg.SubID)
			{
			case 0x02:			// set position
			case 0x03:			// set relative position
				isBusy_ = false;
				break;

			case 0x18:			// find upper hardware stop
			case 0x19:			// find lower hardware stop
				Unlock();
				isBusy_ = false;
				break;
			}
			break;
		}
	}

	return DEVICE_OK;
}



/*
* Gets the application name
*/
int CAN29Axis::GetApplicationName(std::string& applName)
{	
	unsigned char dta[] = {0};   
	Message answer;
	int ret = can29_->SendRead(Message(canAddress_, CAN_PC,  0x18, CMDNR_SYSTEM, PROCID, 0x09, dta, 0), answer);  
	if(ret == DEVICE_OK)
		ret = MessageTools::GetString(answer.Data, 0, applName);
	return ret;
}

/*
* Gets the status
*/
int CAN29Axis::GetStatusCmd(CAN29ULong& status)
{	
	unsigned char dta[] = {devID_};   
	Message answer;
	int ret = can29_->SendRead(Message(canAddress_, CAN_PC,  0x18, CMDNR_AXIS, PROCID, 0x01, dta, 0), answer);  
	if(ret == DEVICE_OK)
	{
		ret =  MessageTools::GetULong(answer.Data, 1, status);
		isBusy_ = (status& 0x04) > 0;
	}
	return ret;
}


/*
* Checks if Axis is present, by cheking application name and status
*/
int CAN29Axis::GetPresent(bool& present)
{
	present = false;

	std::string name;
	int ret = GetApplicationName(name);
	if(ret != DEVICE_OK)
		return ret;

	CAN29ULong status;
	ret = GetStatus(status);
	if(ret != DEVICE_OK)
		return ret;

	// correct device and  status = "Device motorized" ? 
	present = (name == "WSB PiezoDrive CAN") &&  ((status & 0x4000) > 0);

	return ret;
}


/*
* GetPosition in nm (steps)
*/
int CAN29Axis::GetPositionCmd(CAN29Long& position)
{
	unsigned char dta[] = {devID_};   
	Message answer;
	int ret = can29_->SendRead(Message(canAddress_, CAN_PC,  0x18, CMDNR_AXIS, PROCID, 0x02, dta, sizeof(dta)), answer);  
	if(ret == DEVICE_OK)
		ret = MessageTools::GetLong(answer.Data, 1, position);

	return ret;
}


/*
* SetPosition in nm (steps)
*/
int CAN29Axis::SetPosition(CAN29Long position, CAN29Byte movemode)
{	
	unsigned char dta[2+CAN29LongSize] = {devID_, movemode};   
	long tmp = htonl(position);
	memcpy(dta+2, &tmp, CAN29LongSize); 

	int ret = can29_->Send(Message(canAddress_, CAN_PC,  0x19, CMDNR_AXIS, PROCID, 0x02, dta, sizeof(dta)));  
	if(ret != DEVICE_OK)
		return ret;

	isBusy_ = true;
	return ret;
}

/*
* SetRelativePosition in nm (steps)
*/
int CAN29Axis::SetRelativePosition(CAN29Long position, CAN29Byte movemode)
{	
	unsigned char dta[2+CAN29LongSize] = {devID_, movemode};   
	long tmp = htonl(position);
	memcpy(dta+2, &tmp, CAN29LongSize); 

	int ret = can29_->Send(Message(canAddress_, CAN_PC,  0x19, CMDNR_AXIS, PROCID, 0x03, dta, sizeof(dta)));  
	if(ret != DEVICE_OK)
		return ret;

	isBusy_ = true;
	return ret;
}


/*
* Stops all movements
*/
int CAN29Axis::Stop()
{	
	unsigned char dta[] = {devID_, moveMode_};   

	return can29_->Send(Message(canAddress_, CAN_PC,  0x1B, CMDNR_AXIS, PROCID, 0x05, dta, sizeof(dta)));  
}

/*
* Locks the component
*/
int CAN29Axis::Lock()
{	
	unsigned char dta[] = {devID_, 1};   

	return can29_->Send(Message(canAddress_, CAN_PC,  0x1B, CMDNR_AXIS, PROCID, 0x61, dta, sizeof(dta)));  
}

/*
* Unlocks the component
*/
int CAN29Axis::Unlock()
{	
	unsigned char dta[] = {devID_, 0};   

	return can29_->Send(Message(canAddress_, CAN_PC,  0x1B, CMDNR_AXIS, PROCID, 0x61, dta, sizeof(dta)));  
}




/*
* Get position of lower hardware stop in nm (steps)
*/
int CAN29Axis::GetLowerHardwareStop(CAN29Long& position)
{
	unsigned char dta[] = {devID_};   
	Message answer;
	int ret = can29_->SendRead(Message(canAddress_, CAN_PC,  0x18, CMDNR_AXIS, PROCID, 0x19, dta, sizeof(dta)), answer);  
	if(ret == DEVICE_OK)
		ret = MessageTools::GetLong(answer.Data, 1, position);

	return ret;
}

/*
* Get position of upper hardware stop in nm (steps)
*/
int CAN29Axis::GetUpperHardwareStop(CAN29Long& position)
{
	unsigned char dta[] = {devID_};   
	Message answer;
	int ret = can29_->SendRead(Message(canAddress_, CAN_PC,  0x18, CMDNR_AXIS, PROCID, 0x18, dta, sizeof(dta)), answer);  
	if(ret == DEVICE_OK)
		ret = MessageTools::GetLong(answer.Data, 1, position);

	return ret;
}

/*
* Finds position of lower hardware stop
*/
int CAN29Axis::FindLowerHardwareStop()
{
	// lock component
	int ret = Lock();
	if(ret != DEVICE_OK)
		return ret;

	// find hardware stop
	unsigned char dta[] = {devID_};   
	ret = can29_->Send(Message(canAddress_, CAN_PC,  0x19, CMDNR_AXIS, PROCID, 0x19, dta, sizeof(dta)));  
	if(ret != DEVICE_OK)
		return ret;

	// set busy manually
	isBusy_ = true;
	return ret;
}

/*
* Finds position of upper hardware stop
*/
int CAN29Axis::FindUpperHardwareStop()
{
	// lock component
	int ret = Lock();
	if(ret != DEVICE_OK)
		return ret;

	// find hardware stop
	unsigned char dta[] = {devID_};   
	ret = can29_->Send(Message(canAddress_, CAN_PC,  0x19, CMDNR_AXIS, PROCID, 0x18, dta, sizeof(dta)));  
	if(ret != DEVICE_OK)
		return ret;

	// set busy manually
	isBusy_ = true;
	return ret;
}


/*
* Starts position and status monitoring
*/
int CAN29Axis::StartMonitoring()
{
	unsigned char dta[] = {devID_, 0x12, 0x00, 100, CAN_PC, 0xBB};   

	return can29_->Send(Message(canAddress_, CAN_PC,  0x1B, CMDNR_AXIS, PROCID, 0x1F, dta, sizeof(dta)));  

}

/*
* Stops position and status monitoring
*/
int CAN29Axis::StopMonitoring()
{
	unsigned char dta[] = {devID_, 0x00, 0x00, 100, CAN_PC, 0xBB};   

	return can29_->Send(Message(canAddress_, CAN_PC,  0x1B, CMDNR_AXIS, PROCID, 0x1F, dta, sizeof(dta)));  
}

/*
* Sets velocity for position moves in nm/s 
*/
int CAN29Axis::SetTrajectoryVelocity(CAN29Long velocity)
{
	unsigned char dta[1+CAN29LongSize] = {devID_,};   
	long tmp = htonl(velocity);
	memcpy(dta+1, &tmp, CAN29LongSize); 

	Message answer;
	int ret = can29_->SendRead(Message(canAddress_, CAN_PC,  0x19, CMDNR_AXIS, PROCID, 0x21, dta, sizeof(dta)), answer);  
	return ret;
}

/*
* Sets acceleration for position moves in nm/s² 
*/
int CAN29Axis::SetTrajectoryAcceleration(CAN29Long acceleration)
{
	unsigned char dta[1+CAN29LongSize] = {devID_,};   
	long tmp = htonl(acceleration);
	memcpy(dta+1, &tmp, CAN29LongSize); 

	Message answer;
	int ret = can29_->SendRead(Message(canAddress_, CAN_PC,  0x19, CMDNR_AXIS, PROCID, 0x22, dta, sizeof(dta)), answer);  
	return ret;
}

/*
* Gets velocity for position moves in nm/s 
*/
int CAN29Axis::GetTrajectoryVelocity(CAN29Long& velocity)
{
	unsigned char dta[] = {devID_};   
	Message answer;
	int ret = can29_->SendRead(Message(canAddress_, CAN_PC,  0x18, CMDNR_AXIS, PROCID, 0x21, dta, sizeof(dta)), answer);  
	if(ret == DEVICE_OK)
		ret = MessageTools::GetLong(answer.Data, 1, velocity);

	return ret;
}

/*
* Gets acceleration for position moves in nm/s² 
*/
int CAN29Axis::GetTrajectoryAcceleration(CAN29Long& acceleration)
{
	unsigned char dta[] = {devID_};   
	Message answer;
	int ret = can29_->SendRead(Message(canAddress_, CAN_PC,  0x18, CMDNR_AXIS, PROCID, 0x22, dta, sizeof(dta)), answer);  
	if(ret == DEVICE_OK)
		ret = MessageTools::GetLong(answer.Data, 1, acceleration);

	return ret;
}









///////////////////////////////////////////////////////////////////////////////
// XYStageDeviceDevice
//
XYStageDevice::XYStageDevice (): 
CXYStageBase<XYStageDevice>(),
	initialized_ (false),
	stepSize_um_(0.001),
	can29_(),
	xAxis_(CAN_XAXIS, 0, &can29_),
	yAxis_(CAN_YAXIS, 0, &can29_),
	velocity_(0)
{ 

	InitializeDefaultErrorMessages();

	// create pre-initialization properties
	// ------------------------------------

	// Name
	CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceDeviceName, MM::String, true);

	// Description                                                            
	CreateProperty(MM::g_Keyword_Description, "Controller for piezo stages", MM::String, true);

	// Port                                                                   
	CPropertyAction* pAct = new CPropertyAction (this, &XYStageDevice::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

XYStageDevice::~XYStageDevice() 
{
	xAxis_.UnInitialize();
	yAxis_.UnInitialize();

	Shutdown();
}



bool XYStageDevice::Busy()
{	
	return xAxis_.IsBusy() || yAxis_.IsBusy();
}

void XYStageDevice::GetName (char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceDeviceName);
}


int XYStageDevice::Initialize()
{
	if (!can29_.portInitialized_)
		return ERR_DEVICE_NOT_ACTIVE;

	can29_.Initialize(this, GetCoreCallback());

	xAxis_.Initialize();
	yAxis_.Initialize();


	// check if this Axis exists:
	bool presentX, presentY;
	// TODO: check both stages
	int ret = yAxis_.GetPresent(presentY);
	if (ret != DEVICE_OK)
		return ret;
	ret =  yAxis_.GetPresent(presentX);
	if (ret != DEVICE_OK)
		return ret;
	if (!(presentX && presentY))
		return ERR_MODULE_NOT_FOUND;


	// set property list
	// ----------------
	// Trajectory Velocity and Acceleration:
	CPropertyAction* pAct = new CPropertyAction(this, &XYStageDevice::OnTrajectoryVelocity);
	ret = CreateProperty("Velocity (micron/s)", "0", MM::Float, false, pAct);
	if (ret != DEVICE_OK)
		return ret;
	ret = SetPropertyLimits("Velocity (micron/s)", 0, 100000);
	if (ret != DEVICE_OK)
		return ret;
	

	pAct = new CPropertyAction(this, &XYStageDevice::OnTrajectoryAcceleration);
	ret = CreateProperty("Acceleration (micron/s^2)", "0", MM::Float, false, pAct);
	if (ret != DEVICE_OK)
		return ret;
	ret = SetPropertyLimits("Acceleration (micron/s^2)", 0, 500000);
	if (ret != DEVICE_OK)
		return ret;
	
	initialized_ = true;

	return DEVICE_OK;
}

int XYStageDevice::Shutdown()
{
	if (initialized_) initialized_ = false;
	return DEVICE_OK;
}


int XYStageDevice::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax) 
{
	long xMi, xMa, yMi, yMa;
	GetStepLimits(xMi, xMa, yMi, yMa);
	xMin = xMi * stepSize_um_;
	yMin = yMi * stepSize_um_;
	xMax = xMa * stepSize_um_;
	yMax = yMa * stepSize_um_;

	return DEVICE_OK;
}

int XYStageDevice::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax) 
{	
	xAxis_.GetLowerHardwareStop((CAN29Long&)xMin);
	xAxis_.GetUpperHardwareStop((CAN29Long&)xMax);
	yAxis_.GetLowerHardwareStop((CAN29Long&)yMin);
	yAxis_.GetUpperHardwareStop((CAN29Long&)yMax);

	return DEVICE_OK;
}


int XYStageDevice::SetPositionSteps(long xSteps, long ySteps)
{	
	int ret = xAxis_.SetPosition((CAN29Long)xSteps, velocity_);
	if (ret != DEVICE_OK)
		return ret;

	ret = yAxis_.SetPosition((CAN29Long)ySteps, velocity_);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int XYStageDevice::SetRelativePositionSteps(long xSteps, long ySteps)
{	
	int ret = xAxis_.SetRelativePosition((CAN29Long)xSteps, velocity_);
	if (ret != DEVICE_OK)
		return ret;

	ret = yAxis_.SetRelativePosition((CAN29Long)ySteps, velocity_);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}


int XYStageDevice::GetPositionSteps(long& xSteps, long& ySteps)
{
	int ret = xAxis_.GetPosition((CAN29Long&)xSteps);
	if (ret != DEVICE_OK)
		return ret;

	ret = yAxis_.GetPosition((CAN29Long&)ySteps);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int XYStageDevice::Home()
{
	int ret = xAxis_.FindLowerHardwareStop();
	if (ret != DEVICE_OK)
	return ret;

	ret = yAxis_.FindLowerHardwareStop();
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int XYStageDevice::Stop()
{
	int ret = xAxis_.Stop();
	if (ret != DEVICE_OK)
		return ret;

	ret = yAxis_.Stop();
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}

int XYStageDevice::SetOrigin()
{
	return SetAdapterOriginUm(0.0, 0.0);
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int XYStageDevice::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(can29_.port_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		if (initialized_)
		{
			// revert
			pProp->Set(can29_.port_.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}

		//pProp->Get( port_);
		pProp->Get( can29_.port_);
		can29_.portInitialized_ = true;

	}

	return DEVICE_OK;
}



int XYStageDevice::OnTrajectoryVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) 
	{
		// we are lazy and only check the x axis
		CAN29Long velocity;
		int ret = xAxis_.GetTrajectoryVelocity(velocity);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set( (float) (velocity/1000.0) );
	} 
	else if (eAct == MM::AfterSet) 
	{
		double tmp;
		pProp->Get(tmp);
		CAN29Long velocity = (CAN29Long) (tmp * 1000.0);
		int ret = xAxis_.SetTrajectoryVelocity(velocity);
		if (ret != DEVICE_OK)
			return ret;
		ret = yAxis_.SetTrajectoryVelocity(velocity);
		if (ret != DEVICE_OK)
			return ret;
	}
	return DEVICE_OK;

}

int XYStageDevice::OnTrajectoryAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) 
	{
		// we are lazy and only check the x axis
		CAN29Long accel;
		int ret = xAxis_.GetTrajectoryAcceleration(accel);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set( (float) (accel / 1000.0) );
	} 
	else if (eAct == MM::AfterSet) 
	{
		double tmp;
		pProp->Get(tmp);
		CAN29Long accel = (long) (tmp * 1000.0);
		int ret = xAxis_.SetTrajectoryAcceleration(accel);
		if (ret != DEVICE_OK)
			return ret;
		ret = yAxis_.SetTrajectoryAcceleration(accel);
		if (ret != DEVICE_OK)
			return ret;
	}
	return DEVICE_OK;
}



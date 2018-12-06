//////////////////////////////////////////////////////////////////////////////////////
// FILE:          ChuoSeiki_MD_XYZ.h												//
// PROJECT:       Micro-Manager														//
// SUBSYSTEM:     DeviceAdapters													//
//----------------------------------------------------------------------------------//
// DESCRIPTION:   ChuoSeiki_MD5000 device adapter.									//
//																					//
// AUTHOR:        Duong Quang Anh, duong-anh@chuo.co.jp, 2018,						//
//																					//
// COPYRIGHT:     Chuo Precision Industrial Co. Ltd., Tokyo, Japan, 2018			//
//																					//
// LICENSE:       This library is free software; you can redistribute it and/or		//
//                modify it under the terms of the GNU Lesser General Public		//
//                License as published by the Free Software Foundation.				//
//																					//
//                You should have received a copy of the GNU Lesser General Public	//
//                License along with the source distribution; if not, write to		//
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,	//
//                Boston, MA  02111-1307  USA										//
//																					//
//                This file is distributed in the hope that it will be useful,		//
//                but WITHOUT ANY WARRANTY; without even the implied warranty		//
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.			//
//																					//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR							//
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,					//
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.			//
//////////////////////////////////////////////////////////////////////////////////////

# pragma once

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
#include <map>


// Define error code ........................

#define ERR_DEBUG		20001
#define ERR_PORT_CHANGE_FORBIDDEN		10001
#define ERR_UNRECOGNIZED_ANSWER			10002
#define ERR_NO_ANSWER					10003
#define ERR_NO_CONTROLLER				10004
#define ERR_HOMING						10005
#define ERR_TIMEOUT						10006


//Define MD5000D Error Code (0x00h -> 0x53h ref. "command list" page 43) ............

//#define ERR_RESPONSE_CODE_00			0x00	//	The command is normaly executed (no Error)
#define ERR_02_OPERATION_REFUSED_BY_PROGRAM_STOP		10102 //	0x02	//	The operation is refused due to program stop
#define ERR_03_NOT_ACCEPT_COMMAND						10103 //	0x03	//	The command execution cannot be accepted
#define ERR_04_OPERATION_REFUSED_BY_MOTOR_ROTATION		10104 //	0x04	//	The operation is refused due to motor rotation
#define ERR_06_PARAMETER_ERROR							10106 //	0x06	//	Parameter error
#define ERR_07_OPERATION_REFUSED_BY_MOTOR_STOP			10107 //	0x07	//	The operation is refused due to motor stop
#define ERR_08_OPERATION_REFUSED_BY_PROGRAM_RUNNING		10108 //	0x08	//	The operation is refused due to program running
#define ERR_0B_FAIL_TO_READ_DATA						10111 //	0x0B	//	Failed to read Data
#define ERR_0C_CANT_FIND_REGISTERED_PROGRAM				10112 //	0x0C	//	The registered program cannot be found
#define ERR_0D_NO_RESPONSE								10113 //	0x0D	//	No response
#define ERR_0E_SPEED_CAN_NOT_SET_DURING_ACCEL			10114 //	0x0E	//	Speed cannot be set during motor rotation in S-curve accel/decel
#define ERR_0F_MOTOR_EXCITATION_OFF						10115 //	0x0F	//	Motor excitation off
#define ERR_50_STEP_OUT_ERROR							10150 //	0x50	//	Step out error occurs
#define ERR_51_STOP_SIGNAL_INPUT						10151 //	0x51	//	Stop signal is being inputted
#define ERR_52_STOP_SIGNAL_INPUT						10152 //	0x52	//	Stop signal is being inputted
#define ERR_53_NOT_CONSTANT_MODE_IN_SPEED_SETTING		10153 //	0x53	//	Mode is not Constant in Speed setting in interpolation driving

// clear port
int	clearPort(MM::Device& device, MM::Core& core, const char* port);


//-----------------------------------------------------------------------------
//........... Class1: Single stage controller..................................
//-----------------------------------------------------------------------------

class MD_SingleStage:
	public CStageBase<MD_SingleStage>		// reference: Cstagebase
{
public:

// construct, destroy
	MD_SingleStage();
	~MD_SingleStage();
	
// Device API++

   int Initialize();
   int Shutdown();
   bool Busy(); 
   void GetName(char* pszName) const;

// Interface port setting
	int OnSelectPort_1S (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnConfigPort_1S (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnResetPort_1S (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTransmissionDelay_1S (MM::PropertyBase* pProp, MM::ActionType eAct);

// Interface stage setting
	int OnStepSize	(MM::PropertyBase* pPropBase, MM::ActionType eActType);
	int OnSpeed		(MM::PropertyBase* pPropBase, MM::ActionType eActType);
	int OnAccelTime	(MM::PropertyBase* pPropBase, MM::ActionType eActType);
	int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnControllerAxis(MM::PropertyBase* pProp, MM::ActionType eAct);
	//	int OnAutofocus(MM::PropertyBase* pProp, MM::ActionType eAct);
   
// Stage Control functions
	int SetPositionUm(double pos);
	int SetRelativePositionSteps(long steps);
	int GetPositionUm(double& pos);
	int SetPositionSteps(long steps);
	int GetPositionSteps(long& steps);
	int SetOrigin();
	int GetLimits(double& , double& )	{return DEVICE_UNSUPPORTED_COMMAND;}
	int GetStepLimits(long& , long& )	{return DEVICE_UNSUPPORTED_COMMAND;}
	double GetStepSizeUm()	{return stepSize_um_; }

// Checking device functions
	int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
	bool IsContinuousFocusDrive() const {return DEVICE_OK;}
	bool SupportsDeviceDetection(void);
	MM::DeviceDetectionStatus DetectDevice(void);

private:

// check device functions
	int		ConfirmVersion();
	void	QueryPeripheralInventory();
	int		GetNumberOfDiscoverableDevices			();
	void	GetDiscoverableDevice					(int peripheralNumber, char* peripheralName, unsigned int maxNameLength);
	int		GetDiscoverableDevicePropertiesNumber	(int peripheralNumber);
	void	GetDiscoverableDeviceProperty			(int peripheralNumber, short propertyNumber ,char* propertyName, char* propValue, unsigned int maxValueLength);

// portname for 1 stage controller
	std::string portName_1S;
	int transmissionDelay_;

	std::vector<std::string> discoverableDevices_;
	std::vector<short> inventoryDeviceAddresses_;
	std::vector<char> inventoryDeviceIDs_;

// busy, confirm answer error, return error code
	int WaitForBusy(long waitTime);
	int ConfirmAnswer(std::string answer);

// send command to controller, read answer
	int ExecuteCommand(const std::string& cmd);
	int ReadMessage(std::string& sMessage);

	//	int Autofocus(long param);

	bool bOnHome;
	bool bOnLimitStop;
	double stepSize_um_;

	double speed_mm_;

	double position_um_;

	long speed_step_;

	long accelTime_pattern_;
	
	bool onBusy_;
	MM::TimeoutMs* timerOut_;

	bool initializationStatus_;
	double answerTimeoutMs_;

	std::string controllerAxis_;
};


//-----------------------------------------------------------------------------
//........... Class2: Two stages controller....................................
//-----------------------------------------------------------------------------

class MD_TwoStages: 	
public CXYStageBase<MD_TwoStages>
{
public:
	MD_TwoStages() ;
	~MD_TwoStages() ;

// Device API
// ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();


 // Interface for port setting
	int OnSelectPort_2S (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnConfigPort_2S (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnResetPort_2S (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTransmissionDelay_2S (MM::PropertyBase* pProp, MM::ActionType eAct);

// Interface for stage setting

	int OnStepSizeX		(MM::PropertyBase* pPropBase, MM::ActionType eActType);
	int OnStepSizeY		(MM::PropertyBase* pPropBase, MM::ActionType eActType);
	int OnSpeedX		(MM::PropertyBase* pPropBase, MM::ActionType eActType);
	int OnAccelTimeX	(MM::PropertyBase* pPropBase, MM::ActionType eActType);
	int OnSpeedY		(MM::PropertyBase* pPropBase, MM::ActionType eActType);
	int OnAccelTimeY	(MM::PropertyBase* pPropBase, MM::ActionType eActType);

protected:
// check controller
	int ConfirmVersion();
	void QueryPeripheralInventory();

// Stage control function
	int SetPositionSteps(long x, long y);
	int GetPositionSteps(long& x, long& y);
	int SetRelativePositionSteps(long x, long y);
	int Home();				// { return DEVICE_OK; }
	int Stop();				// { return DEVICE_OK; }

	int SetOrigin();		// { return DEVICE_OK; }

	// Stage limits
	int GetLimits(double& /*lower*/, double& /*upper*/)	{ return DEVICE_UNSUPPORTED_COMMAND; }

	int GetLimitsUm(double& /*xMin*/, double& /*xMax*/, double& /*yMin*/, double& /*yMax*/)	{ return DEVICE_UNSUPPORTED_COMMAND; }

	int GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/){return DEVICE_UNSUPPORTED_COMMAND; 	}

	double GetStepSizeXUm() { return stepSize_umX_; }
	double GetStepSizeYUm() { return stepSize_umY_; }

	int IsXYStageSequenceable(bool& isSequenceable) const	{isSequenceable = false; return DEVICE_OK;	}

private:

	std::string portName_2S;

	int WaitForBusy(long waitTime);
	int ConfirmAnswer(std::string answer);

// send command to controller, read answer
	int ExecuteCommand(const std::string& cmd);
	int ReadMessage(std::string& sMessage);

	bool bOnHome;
	bool bOnLimitStop;

	double stepSize_umX_;
	double stepSize_umY_;

	double speed_mmX_;
	double speed_mmY_;

	double position_umX_;
	double position_umY_;

	long speed_stepX_;
	long speed_stepY_;

	long accelTime_patternX_;
	long accelTime_patternY_;
	
	bool onBusy_;
	MM::TimeoutMs* timerOut_;

	bool initializationStatus_;
	double answerTimeoutMs_;

	std::string controllerAxisX_;
	std::string controllerAxisY_;

};


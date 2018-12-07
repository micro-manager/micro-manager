
////////////////////////////////////////////////////////////////////////////////////
// FILE:          ChuoSeiki_QT_XYZStage.h											
// PROJECT:       Micro-Manager														
// SUBSYSTEM:     DeviceAdapters													
//----------------------------------------------------------------------------------
// DESCRIPTION:   ChuoSeiki_QT_Controller device adapter.							
//																					
// AUTHOR:        Okumura Daisuke, d-okumura@chuo.co.jp								
//				  Duong Quang Anh, duong-anh@chuo.co.jp								
//																					
// COPYRIGHT:     CHUO Precision Industrial Co. Ltd., Tokyo, Japan, 2018			
//																					
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


#ifndef _CHUOSEIKIXYZSTAGE_H_
#define _CHUOSEIKIXYZSTAGE_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN		10001
#define ERR_UNRECOGNIZED_ANSWER			10002
#define ERR_NO_ANSWER					10003
#define ERR_NO_CONTROLLER				10004
#define ERR_HOMING						10005
#define ERR_TIMEOUT						10006

//Controller Error Code Read Manual
#define ERR_CONTROLER_0					10100	//Controller Error Code: Not In Command Recive Mode
#define ERR_CONTROLER_1					10101	//Controller Error Code: Command Syntax Error
#define ERR_CONTROLER_2					10102	//Controller Error Code: Command Set Value Overflow Error
#define ERR_CONTROLER_3					10103	//Controller Error Code: Command Set Axis Error
#define ERR_CONTROLER_4					10104	//Controller Error Code: Command Char Over
#define ERR_CONTROLER_5					10105	//Controller Error Code: Stop command Error
#define ERR_CONTROLER_6					10106	//Controller Error Code: Limit Detect 
#define ERR_CONTROLER_7					10107	//Controller Error Code: Alarm Stop
#define ERR_CONTROLER_8					10108	//Controller Error Code: Com Error/Driver Error
#define ERR_CONTROLER_9					10109	//Controller Error Code: Other Errors

int clearPort(MM::Device& device, MM::Core& core, const char* port);
int changeCommandLevel(MM::Device& device, MM::Core& core, const char* commandLevel, const char* port);
const int nrShuttersPerDevice = 3;
const int nrWheelsPerDevice = 2;
const int nrDevicesPerController = 5;


class ChuoSeikiXYStage : public CXYStageBase<ChuoSeikiXYStage>
{
public:
	ChuoSeikiXYStage(void);
	~ChuoSeikiXYStage(void);

	bool Busy();

	void GetName(char* pszName) const;

	int Initialize();
	int Shutdown();

// Interface port setting
	int OnSelectPort_XY (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnConfigPort_XY (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnResetPort_XY (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTransmissionDelay_XY (MM::PropertyBase* pProp, MM::ActionType eAct);

// Interface stage setting
	int OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStepSizeY(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnSpeedXHigh(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSpeedXLow(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAccelTimeX(MM::PropertyBase* pProp, MM::ActionType eAct);
   
	int OnSpeedYHigh(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSpeedYLow(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAccelTimeY(MM::PropertyBase* pProp, MM::ActionType eAct);

// Stage Control functions   
	virtual int SetPositionSteps(long x, long y);
	virtual int GetPositionSteps(long& x, long& y);
	virtual int SetRelativePositionSteps(long x, long y);
	virtual int Home();// { return DEVICE_OK; }
	virtual int Stop();// { return DEVICE_OK; }

	virtual int SetOrigin();// { return DEVICE_OK; }

	virtual int GetLimits(double& /*lower*/, double& /*upper*/)
	{ return DEVICE_UNSUPPORTED_COMMAND; }

	virtual int GetLimitsUm(double& /*xMin*/, double& /*xMax*/, double& /*yMin*/, double& /*yMax*/)
	{ return DEVICE_UNSUPPORTED_COMMAND; }

	virtual int GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
	{ return DEVICE_UNSUPPORTED_COMMAND; }
	double GetStepSizeXUm() { return stepSize_umX_; }
	double GetStepSizeYUm() { return stepSize_umY_; }

	int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

	int WaitForBusy(long Time);
	int ConfirmAnswer(std::string ans);

	// action interface
	// ----------------
//	int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

// Checking device functions
	int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
	bool IsContinuousFocusDrive() const {return DEVICE_OK;}
	bool SupportsDeviceDetection(void) {return DEVICE_OK;}
	MM::DeviceDetectionStatus DetectDevice(void);

private:

	// check device functions
	int		ConfirmComm();
	void	QueryPeripheralInventory();
	int		GetNumberOfDiscoverableDevices			();
	void	GetDiscoverableDevice					(int peripheralNumber, char* peripheralName, unsigned int maxNameLength);
	int		GetDiscoverableDevicePropertiesNumber	(int peripheralNumber);
	void	GetDiscoverableDeviceProperty			(int peripheralNumber, short propertyNumber ,char* propertyName, char* propValue, unsigned int maxValueLength);

// portname for 1 stage controller
	std::string portName_XY;
	int transmissionDelay_;

	std::vector<std::string> discoverableDevices_;
	std::vector<short> inventoryDeviceAddresses_;
	std::vector<char> inventoryDeviceIDs_;

// control variables
	bool bHomeing;
	bool bLimitStop;

	double stepSize_umX_;
	double stepSize_umY_;

	double speedHigh_mmX_;
	double speedLow_mmX_;

	long speedHigh_stepX_;
	long speedLow_stepX_;

	long accelTimeX_;

	double speedHigh_mmY_;
	double speedLow_mmY_;

	long speedHigh_stepY_;
	long speedLow_stepY_;

	long accelTimeY_;


	double posX_um_;
	double posY_um_;
	bool busy_;
	MM::TimeoutMs* timeOutTimer_;

	bool initialized_;

	std::string controllerAxisX_;
	std::string controllerAxisY_;

};

class ChuoSeikiZStage : public CStageBase<ChuoSeikiZStage>
{
public:
	ChuoSeikiZStage(void);
	~ChuoSeikiZStage(void);

	bool Busy();

	void GetName(char* pszName) const;

	int Initialize();
	int Shutdown();

// Interface port setting
	int OnSelectPort_Z (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnConfigPort_Z (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnResetPort_Z (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTransmissionDelay_Z (MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnStepSizeZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSpeedZHigh(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSpeedZLow(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAccelTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct);


//	virtual double GetStepSize() {return stepSize_um_;}
	virtual int SetPositionSteps(long z);
	virtual int GetPositionSteps(long& z);
	virtual int SetRelativePositionSteps(long z);
//	virtual int Home();// { return DEVICE_OK; }
//	virtual int Stop();// { return DEVICE_OK; }

	virtual int SetOrigin();// { return DEVICE_OK; }

	virtual int GetLimits(double& /*lower*/, double& /*upper*/)
	{ return DEVICE_UNSUPPORTED_COMMAND; }

	int GetPositionUm(double& pos);//{	return DEVICE_OK;	}
	int SetPositionUm(double pos);//{	return DEVICE_OK;	}

	virtual int GetStepLimits(long& /*xMin*/, long& /*xMax*/)
	{ return DEVICE_UNSUPPORTED_COMMAND; }
	double GetStepSizeUm() { return stepSize_umZ_; }

	int WaitForBusy(long Time);
	int ConfirmAnswer(std::string ans);

	// action interface
	// ----------------
	int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnControllerAxis(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAutofocus(MM::PropertyBase* pProp, MM::ActionType eAct);

	// Checking device functions
	int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
	bool IsContinuousFocusDrive() const {return DEVICE_OK;}
	bool SupportsDeviceDetection(void){return DEVICE_OK;}
	MM::DeviceDetectionStatus DetectDevice(void);

private:

	// check device functions
	int		ConfirmComm();
	void	QueryPeripheralInventory();
	int		GetNumberOfDiscoverableDevices			();
	void	GetDiscoverableDevice					(int peripheralNumber, char* peripheralName, unsigned int maxNameLength);
	int		GetDiscoverableDevicePropertiesNumber	(int peripheralNumber);
	void	GetDiscoverableDeviceProperty			(int peripheralNumber, short propertyNumber ,char* propertyName, char* propValue, unsigned int maxValueLength);

// portname for 1 stage controller
	std::string portName_Z;
	int transmissionDelay_;

	std::vector<std::string> discoverableDevices_;
	std::vector<short> inventoryDeviceAddresses_;
	std::vector<char> inventoryDeviceIDs_;

	int ExecuteCommand(const std::string& cmd, std::string& response);
	int Autofocus(long param);

	bool bHomeing;
	bool bLimitStop;

	std::string port_;

	double stepSize_umZ_;

	double speedHigh_mmZ_;
	double speedLow_mmZ_;

	long speedHigh_stepZ_;
	long speedLow_stepZ_;

	long accelTimeZ_;


	double posZ_um_;
	bool busy_;
	MM::TimeoutMs* timeOutTimer_;

	bool initialized_;
	double answerTimeoutMs_;

	std::string controllerAxisZ_;



};

#endif //_CHUOSEIKIXYZSTAGE_H_

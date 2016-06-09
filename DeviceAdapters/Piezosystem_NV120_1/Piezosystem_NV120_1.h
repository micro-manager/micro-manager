//////////////////////////////////////////////////////////////////////////////
// FILE:          Piezosystem_NV120_1.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implementation of the Piezosystem device adapter.
//                
//                
// AUTHOR:        Chris Belter, cbelter@piezojena.com 4/09/13.  XYStage and ZStage by Chris Belter
//
// COPYRIGHT:     Piezosystem Jena, Germany, 2013
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
//

#ifndef _PIEZOSYSTEM_NV120_1_H_
#define _PIEZOSYSTEM_NV120_1_H_
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION         10002
#define ERR_INVALID_SPEED            10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_SET_POSITION_FAILED      10005
#define ERR_INVALID_STEP_SIZE        10006
#define ERR_LOW_LEVEL_MODE_FAILED    10007
#define ERR_INVALID_MODE             10008
#define ERR_INVALID_ID               10009
#define ERR_UNRECOGNIZED_ANSWER      10010
#define ERR_INVALID_SHUTTER_STATE    10011
#define ERR_INVALID_SHUTTER_NUMBER   10012
#define ERR_INVALID_COMMAND_LEVEL    10013
#define ERR_MODULE_NOT_FOUND         10014
#define ERR_INVALID_WHEEL_NUMBER     10015
#define ERR_INVALID_WHEEL_POSITION   10016
#define ERR_NO_ANSWER                10017
#define ERR_WHEEL_HOME_FAILED        10018
#define ERR_WHEEL_POSITION_FAILED    10019
#define ERR_SHUTTER_COMMAND_FAILED   10020
#define ERR_COMMAND_FAILED           10021
#define ERR_INVALID_DEVICE_NUMBER    10023
#define ERR_DEVICE_CHANGE_NOT_ALLOWED 10024
#define ERR_SHUTTER_USED             10025
#define ERR_WHEEL_USED               10026
#define ERR_NO_CONTROLLER            10027
#define ERR_ONLY_OPEN_LOOP           10028
#define ERR_REMOTE						 10029


#define STATUS_ACTORNAME				1
#define STATUS_ACTORSERNO				2
#define STATUS_COORDINATE				4
#define STATUS_LOOP						8
#define STATUS_MEASURE					16		//0=without ; 1=with
#define STATUS_MONWPA0					32
#define STATUS_MONWPA1					64
#define STATUS_MONWPA2					96
#define STATUS_REMOTE					128
#define STATUS_ACTOR						32768	//Actor is ready

// MMCore name of serial port
std::string port_ = "";

class Stage : public CStageBase<Stage>
{
public:
   Stage(int nr);   
   ~Stage();
   std::string name_;
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
  int SetPositionUm(double pos);
  int GetPositionUm(double& pos);
//  int SetRelativePositionUm(double pos);
//  int GetRelativePositionUm(double& pos);
  int SetPositionSteps(long steps){pos_=steps*1.0;	 return DEVICE_UNSUPPORTED_COMMAND; }
  int GetPositionSteps(long& steps){steps=(long)floor(pos_); return DEVICE_UNSUPPORTED_COMMAND; }
//  int SetRelativePositionSteps(long steps);
//  int GetRelativePositionSteps(long& steps);
  int SetOrigin();
  int GetLimits(double& min, double& max);
  bool SupportsDeviceDetection(void);
  MM::DeviceDetectionStatus DetectDevice(void);
	int GetDevice(std::string& dev);
	int GetActorname(std::string& name);
	int GetAxisname(std::string& name);
	int GetLimitValues();
   int GetVersion();
	int GetStatus(int& stat);
	int GetLoop(bool& loop);
	int GetLight(int& brightness);
   // action interface
   // ----------------
	int OnPort(MM::PropertyBase* pProp, MM::ActionType pAct);
	int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;} 
	bool IsContinuousFocusDrive() const {return false;}
	int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLoop(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRemote(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStat(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMinV(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMaxV(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMinUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMaxUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnActorname(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAxisname(MM::PropertyBase* pProp, MM::ActionType eAct); 
	int OnMon(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSoftstart(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEncmode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEnctime(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEnclim(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEncexp(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEncstol(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnEncstcl(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLight(MM::PropertyBase* pProp, MM::ActionType eAct);
private:
   int SendCommand(const char* cmd,std::string &result);     
   int GetCommandValue(const char* c,double& d);
   int GetCommandValue(const char* c,int& i);
   int SetCommandValue(const char* c,double fkt);
   //int SetCommandValue(char* c,int fkt);
	bool initialized_;
	std::string dev_;
	std::string acname_;
	std::string axisname_;
	std::string ver_;
	std::string sdate_;
	std::string serno_;
	int stat_;
	int light_;
   bool loop_;
	bool remote_;
	double voltage_;
	bool fenable_;
   double min_V_;
   double max_V_;
   double min_um_;
   double max_um_;
	//channel
	double pos_;		//position value in micron

	int nr_; 
};

#endif //_PIEZOSYSTEM_NV120_1_H_

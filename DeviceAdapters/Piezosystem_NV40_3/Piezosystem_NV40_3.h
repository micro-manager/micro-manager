///////////////////////////////////////////////////////////////////////////////
// FILE:          Piezosystem_NV40_3.h
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

#ifndef _PIEZOSYSTEM_NV40_3_H_
#define _PIEZOSYSTEM_NV40_3_H_
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

//Errorflags
#define ERROR_ACTUATOR				1		//0x0001 actuator is not pluged
#define ERROR_SHORT_CIRCUIT		2		//0x0002 actuator short-circuit
#define ERROR_UDL						4096	//0x1000 underload
#define ERROR_OVL						8192	//0x2000 overflow
#define ERROR_WRONG_ACTUATOR		16384	//0x4000
#define ERROR_HEAT					32768 //0x8000 temperture to high

//Statusflags
#define STATUS_ACTORNAME				1	  //0x0001	 = bit1
#define STATUS_ACTORSERNO				2	  //0x0002	 =	bit2
#define STATUS_COORDINATE				4	  //0x0004	 = bit3
#define STATUS_LOOP						8	  //0x0008	 = bit4
#define STATUS_MEASURE					16	  //0x0010   = bit5    0=without ; 1=with
#define STATUS_MONWPA0					32	  //0x0020	 = bit6 + !bit7
#define STATUS_MONWPA1					64	  //0x0040	 =	!bit6 + bit7
#define STATUS_MONWPA2					96	  //0x0060	 =	bit6 + bit7
#define STATUS_REMOTE					128	//0x0080
#define STATUS_ACTOR						32768	//0x8000 //Actor is ready

// MMCore name of serial port
std::string port_ = "";

bool remoteCh_[3];
int clearPort(MM::Device& device, MM::Core& core, const char* port);

class Hub :   public CGenericBase<Hub>	 // public HubBase<Hub>
{
   public:
      Hub(const char* c);
      ~Hub();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();

      void GetName(char* pszName) const;
      bool Busy();
		int GetLight(int& brightness);
		int GetVersion();
		int GetStatus(int ch,int& stat);
	   int GetLoop(int ch,bool& loop);
		int GetLimits(double& min, double& max);
		int GetLimitsValues();
		int GetRemoteValues();

      // action interface
      // ---------------
      int OnPort (MM::PropertyBase* pProp, MM::ActionType eAct);

      // device discovery
      bool SupportsDeviceDetection(void);
      MM::DeviceDetectionStatus DetectDevice(void);
		int OnEncmode(MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnEnctime(MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnEnclim(MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnEncexp(MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnEncstol(MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnEncstcl(MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnLight(MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnSoftstart(MM::PropertyBase* pProp, MM::ActionType eAct);
		int GetCommandValue(const char* c,int ch,double& d);
		int GetCommandValue(const char* c,int ch,int& i);
		int SetCommandValue(const char* c,int ch,double fkt);
		int SetCommandValue(const char* c,int ch,int fkt);
   private:      
		int SendCommand(const char* cmd,std::string &result);
		int ErrorMessage(int error);
		int GetCommandValue(const char* c,double& d);
		int GetCommandValue(const char* c,int& i);
		int SetCommandValue(const char* c,double fkt);
		int SetCommandValue(const char* c,int fkt);
		int GetDevice(std::string& device);
		std::string device_;
		std::string sdate_;
		std::string serno_;
		std::string ver_;
		double min_V_;
		double max_V_;
		const char* name_;
		bool fready_;
		bool initialized_;
		int bright_;
	  
};
Hub* hub;

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
	int GetDevice(std::string& dev);
	int GetActorname(std::string& name);
	int GetAxisname(std::string& name);
	int GetLimitValues();
   int GetVersion();
	int GetStatus(int& stat);
	int GetLoop(bool& loop);
	
   // action interface
   // ----------------
	//int OnPort(MM::PropertyBase* pProp, MM::ActionType pAct);
	int OnChannel(MM::PropertyBase* pProp, MM::ActionType pAct);
	int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;} 
	bool IsContinuousFocusDrive() const {return false;}
	int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLoop(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRemote(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStat(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMinV(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMaxV(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMinUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMaxUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnActorname(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAxisname(MM::PropertyBase* pProp, MM::ActionType eAct); 
	int OnMon(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSoftstart(MM::PropertyBase* pProp, MM::ActionType eAct);	
private:
   int SendCommand(const char* cmd,std::string &result);  
	double answerTimeoutMs_;
	bool initialized_;
	std::string acname_;
	std::string axisname_; 		
	std::string serno_;
	bool fenable_;	
   bool loop_;
	bool remote_;	
	int stat_;
   double min_V_;
   double max_V_;
   double min_um_;
   double max_um_; 
	int monwpa_;
	int nr_;
	double pos_;	//position value in micron	 
	double voltage_; 
};

class XYStage : public CXYStageBase<XYStage> 
{
public:
   XYStage();    
   XYStage(int chx, int chy);   
   ~XYStage();
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy(){ return false;}

    // XYStage API
   // -----------
 
  int SetPositionUm(double x, double y);
  int GetPositionUm(double& x, double& y);
  int SetRelativePositionUm(double dx, double dy);
  int SetPositionSteps(long x, long y);
  int GetPositionSteps(long& x, long& y);
  int SetRelativePositionSteps(long x, long y);  
  int SetOrigin(); 
  int Home();
  int Stop();
  
  int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
  int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
  double GetStepSizeXUm() {return stepSizeUm_;}
  double GetStepSizeYUm() {return stepSizeUm_;}
  int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_UNSUPPORTED_COMMAND; }
  // action interface
  // ----------------
  int OnChannelX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnChannelY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnStatX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnStatY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnLoopX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnLoopY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnRemoteX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnRemoteY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnMonX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnMonY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPositionX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPositionY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSoftstartX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSoftstartY(MM::PropertyBase* pProp, MM::ActionType eAct);
private:
	int GetLimitValues();
   bool initialized_; 
	double stepSizeUm_;
	bool x_fenable_;
	bool y_fenable_;
	long xStep_;
	long yStep_;
	int xstat_;
	int ystat_;
	bool x_loop_;
	bool y_loop_;
	bool x_remote_;	
	bool y_remote_;
	double min_V_;
   double max_V_;
	double x_min_um_;
   double x_max_um_; 
	double y_min_um_;
   double y_max_um_; 
	int x_monwpa_;
	int y_monwpa_;
	double xpos_;	//position x value in micron	
	double ypos_;	//position y value in micron	
	double xvoltage_; 	 
	double yvoltage_; 
   int xChannel_;
   int yChannel_;
	
};

#endif //_PIEZOSYSTEM_NV40_3_H_

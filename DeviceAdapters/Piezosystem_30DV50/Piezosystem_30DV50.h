// 
///////////////////////////////////////////////////////////////////////////////
// FILE:          Piezosystem_30DV50.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implementation of the Piezosystem device adapter.
//                
//                
// AUTHOR:        Chris Belter, 15/07/13.  ZStage and Shutter by Chris Belter
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

#ifndef _PIEZOSYSTEM_DDRIVE_H_
#define _PIEZOSYSTEM_DDRIVE_H_

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

#define ERR_I2C				1
#define ERR_RESERVE_1       2
#define ERR_TEMP            4
#define ERR_OVL				8
#define ERR_UDL				16
#define ERR_COMM_DSP		32
//BIT6-14 are reserve
#define ERR_EVD_NOT_READY	32768

//status register (table 11)
#define ACTUATOR_PLUGGED					1		//Bit0

#define AKTUATOR_STRAIN_GAUGE_MEASURE		2		//Bit1+!Bit2
#define AKTUATOR_CAPACITIVE_MEASURE			4		 //!Bit1+Bit2
#define AKTUATOR_INDUCTIVE_MEASURE			6		//Bit1+Bit2

#define OPEN_LOOP_SYSTEM					16		//Bit4   0=Close_loop; 1=open_loop 
#define PIEZO_VOLTAGE_ENABLE				64		//Bit6
#define CLOSE_LOOP							128		//Bit7
//status generator  
#define GENERATOR_OFF_MASK					3584	//!Bit9 + !Bit10 + !Bit11
#define GENERATOR_SINE						512		//Bit9 + !Bit10 + !Bit11
#define GENERATOR_TRIANGLE					1024	//!Bit9 + Bit10 + Bit11
#define GENERATOR_RECTANGLE					1536	//Bit9 + Bit10 + Bit11
#define GENERATOR_NOISE						2048	//!Bit9 + !Bit10 + Bit11
#define GENERATOR_SWEEP						2560	//Bit9 + !Bit10 + Bit11

#define NOTCH_FILTER_ON						4096	//Bit12
#define LOW_PASS_FILTER_ON					8192	//Bit13

// MMCore name of serial port
std::string port_ = "";

int clearPort(MM::Device& device, MM::Core& core, const char* port);
//int getResult(MM::Device& device, MM::Core& core, const char* port);
char** splitString(char* string,char* delimiter);
//int findString(char* str1, std::string findstr);


class Stage : public CStageBase<Stage> 
{

public:
   Stage();
	Stage(int channel);
   ~Stage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
	int GetVersion(std::string& version);
	bool SupportsDeviceDetection(void);
    MM::DeviceDetectionStatus DetectDevice(void);
   // Stage API
   // ---------
  int SetPositionUm(double pos);
  int GetPositionUm(double& pos);
//  int SetRelativePositionUm(double pos);
//  int GetRelativePositionUm(double& pos);
//  int SetRelativePositionSteps(long steps);
//  int GetRelativePositionSteps(long& steps);


  int SetPositionSteps(long steps){	stepSizeUm_= steps; return DEVICE_UNSUPPORTED_COMMAND; }
  int GetPositionSteps(long& steps){steps = (long) stepSizeUm_; return DEVICE_UNSUPPORTED_COMMAND; }
  int SetOrigin();
  int GetLimits(double& min, double& max);

  bool IsContinuousFocusDrive() const {return false;}
  int GetStatus(int &s);
  int GetChannel(int& channel);
  int SetChannel(int channel);
  int GetAxis(int& id);
  int GetActuatorName(char* id);
  int GetKtemp(double& ktemp);
  int GetRohm(int& rohm);
  int GetRgver(int& rgver);
  int GetFenable(bool& fenable);
  int SetFenable(bool fenable);
  int GetSr(double& sr);
  int SetSr(double sr);
  int GetModon(bool& modon);
  int SetModon(bool modon);
  int GetMonsrc(int& monsrc);
  int SetMonsrc(int monsrc);
  int GetLoop(bool& loop);
  int SetLoop(bool loop);
  int GetKp(double& kp);
  int SetKp(double kp);
  int GetKi(double& ki);
  int SetKi(double ki);
  int GetKd(double& kd);
  int SetKd(double kd);
  int GetNotchon(bool& notch);
  int SetNotchon(bool notch);
  int GetNotchf(int& notchf);
  int SetNotchf(int notchf);
  int GetNotchb(int& notchb);
  int SetNotchb(int notchb);
  int GetLpon(bool& lp);
  int SetLpon(bool lp);
  int GetLpf(int& lpf);
  int SetLpf(int lpf);

  int GetGfkt(int& fkt);
  int SetGfkt(int fkt);
  int GetSine(); 
  //sine
  int GetGasin(double& fkt);
  int SetGasin(double fkt);
  int GetGosin(double& fkt);
  int SetGosin(double fkt);
  int GetGfsin(double& fkt);
  int SetGfsin(double fkt);
  //triangle
  int GetGatri(double& fkt);
  int SetGatri(double fkt);
  int GetGotri(double& fkt);
  int SetGotri(double fkt);
  int GetGftri(double& fkt);
  int SetGftri(double fkt);
  int GetGstri(double& fkt);
  int SetGstri(double fkt);
  //rectangle
  int GetGarec(double& fkt);
  int SetGarec(double fkt);
  int GetGorec(double& fkt);
  int SetGorec(double fkt);
  int GetGfrec(double& fkt);
  int SetGfrec(double fkt);
  int GetGsrec(double& fkt);
  int SetGsrec(double fkt);
  //noise
  int GetGanoi(double& fkt);
  int SetGanoi(double fkt);
  int GetGonoi(double& fkt);
  int SetGonoi(double fkt);
  //Sweep
  int GetGaswe(double& fkt);
  int SetGaswe(double fkt);
  int GetGoswe(double& fkt);
  int SetGoswe(double fkt);
  int GetGtswe(double& fkt);
  int SetGtswe(double fkt);

  int GetScanType(int& sct);
  int SetScanType(int sct);
  int GetScan(bool& ss);
  int SetScan(bool ss);
  int GetTrgss(double& trgss);
  int SetTrgss(double trgss);
  int GetTrgse(double& trgse);
  int SetTrgse(double trgse);
  int GetTrgsi(double& trgsi);
  int SetTrgsi(double trgsi);
  int GetTrglen(int& trglen);
  int SetTrglen(int trglen);
  int GetTrgedge(int& trgedge);
  int SetTrgedge(int trgedge);

   // action interface
   // ----------------
   int OnID(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType pAct);
   int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}   
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStat(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnModulInput(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMonitor(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSoftstart(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSlewRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLoop(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMinV(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxV(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMinUm(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxUm(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNotch(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNotchFreq(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNotchBand(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLowpass(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLowpassFreq(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPidP(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPidI(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPidD(MM::PropertyBase* pProp, MM::ActionType eAct);
   int SetPidDefault();
   int OnGenerate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSinAmp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSinOff(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSinFreq(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriAmp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriOff(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriFreq(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriSym(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRecAmp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRecOff(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRecFreq(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRecSym(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNoiAmp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNoiOff(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSweAmp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSweOff(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSweTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScan(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerStart(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerEnd(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerInterval(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerType(MM::PropertyBase* pProp, MM::ActionType eAct);

private:

   int SendCommand(const char* cmd,std::string &result);
   int SendServiceCommand(const char* cmd,std::string& result);  
   int GetDouble(const char * cmd,double& value );
   int GetCommandValue(const char* c,double& d);
   int GetCommandValue(const char* c,int& d);
   int SetCommandValue(const char* c,double fkt);
   int SetCommandValue(const char* c,int fkt);
   int GetLimitsValues();

   bool initialized_;
	MM::MMTime changedTime_;
   double stepSizeUm_;
   double answerTimeoutMs_;
   std::string id_;
   char* ac_name_; 
	int channel_;
   int stat_;
   double voltage_;
   double min_V_;
   double max_V_;
   double min_um_;
   double max_um_;
	//channel
	double pos_;		//position value in micron
	double ktemp_;		//ktemp in degree celsius
	int rohm_;		//operation time of actuator sine shiping
	int rgver_;			//versions number of loop-controller
	bool fenable_;		//actuator soft start (false=disable ; true=enable)
	double sr_;			//slew rate 

	bool modon_;		//modulation input MOD plug
	int monsrc_;		//monitor output
						//0 = position on close loop
						//1 = command value
						//2 = controller output voltate
						//3 = closed loop deviation
						//4 = absolute close loop deviation
						//5 = actuator voltage
						//6 = position in open loop

	//PID-controler
	bool loop_;			//open loop(false); close loop(true) 
	double kp_;			//proportional term
	double ki_;			//integral term
	double kd_;			//diferential term

	//notch filter (Kerbfilter)
	bool notchon_;		//notch filter off=false; on=true	
	int notchf_;		//notch filter frequency 0-20000 Hz
	int notchb_;		//bandwidth 0-20000 Hz
	bool lpon_;			//low pass filter
	int lpf_;		//low pass cut frequency 0-20000

	int gfkt_;			//internal funktion generator (table 12)
						//0 = off
						//1 = sine
						//2 = triangle
						//3 = rectangle
						//4 = noise
						//5 = sweep
						////6 = Close Loop
						////7 = OPen Loop
	//sine
	double gasin_;		//generator aplitute sine 0-100 %
	double gosin_;		//aplitute offset sine 0-100 %
	double gfsin_;		//generator frequenz sine 0-9999.9 HZ
	//triangle
	double gatri_;		//generator aplitute trianle 0-100 %
	double gotri_;		//aplitute offset tringle 0-100 %
	double gftri_;		//generator frequenz tri 0-9999.9 HZ
	double gstri_;		//symetry of triangle 0.1 - 99.9 % (default= 50 %)
	//rectangle
	double garec_;		//generator aplitute rectangle 0-100 %
	double gorec_;		//aplitute offset rectangle 0-100 %
	double gfrec_;		//generator frequenz rectangle 0-9999.9 HZ
	double gsrec_;		//symetry of rectangle 0.1 - 99.9 % (default= 50 %)
	//noise
	double ganoi_;		//generator aplitute noise 0-100 %
	double gonoi_;		//aplitute offset noise 0-100 %
	//sweep
	double gaswe_;		//generator aplitute sweep 0-100 %
	double goswe_;		//aplitute offset sweep 0-100 %
	double gtswe_;		//generator sweep time 0.4 - 800 [sec/decade]
	
	// scan
	int sct_;			//Scan Type
	bool ss_;			//Scan

	//trigger
	double trgss_;		//trigger position start
	double trgse_;		//trigger position end
	double trgsi_;		//trigger position intervals
	int trglen_;		//n*20us n=1...255
	int trgedge_;		//0 = trigger off
						//1 = trigger at rising edge
						//2 = trigger at falling edge
						//3 = trigger at both edges
};

class Shutter : public CShutterBase<Shutter>
{
public:
   Shutter();
   ~Shutter();

   bool Busy();
   void GetName(char* pszName) const;
   int Initialize();
   int Shutdown();
      
   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

	bool SupportsDeviceDetection(void);
	MM::DeviceDetectionStatus DetectDevice(void);
	int GetStatus(int& stat);
	int GetVersion(std::string& version);
	int GetActuatorName(char* id);
	int GetRgver(int& rgver);
   // action interface
   // ----------------
   //int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnID(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnDeviceNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnShutterNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
	int SetPositionUm(double pos);
	int GetPositionUm(double& pos);
	int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType pAct);
	int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStat(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnShutterState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnModulInput(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMonitor(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSoftstart(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSlewRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLoop(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMinV(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxV(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMinUm(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxUm(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNotch(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNotchFreq(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNotchBand(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLowpass(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLowpassFreq(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPidP(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPidI(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPidD(MM::PropertyBase* pProp, MM::ActionType eAct);
   int SetPidDefault();
   int OnGenerate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSinAmp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSinOff(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSinFreq(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriAmp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriOff(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriFreq(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriSym(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRecAmp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRecOff(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRecFreq(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRecSym(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNoiAmp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNoiOff(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSweAmp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSweOff(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSweTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScan(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerStart(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerEnd(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerInterval(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerType(MM::PropertyBase* pProp, MM::ActionType eAct);
private:
   //int SetShutterPosition(bool state);
   //int GetShutterPosition(bool& state);
	int SendCommand(const char* cmd,std::string &result);
   int GetCommandValue(const char* c,double& d);
   int GetCommandValue(const char* c,int& d);
   int SetCommandValue(const char* c,double fkt);
   int SetCommandValue(const char* c,int fkt);
	int GetLimitsValues();
   std::string name_;   
   bool initialized_; 
	bool close_;		//Version: 0 = open at zero voltage(Version1); 1 = close (Version2)
	bool shut_;			//actual stat 0=open ; 1=close;
   double answerTimeoutMs_;
	char*  ac_name_;
	int stat_;
   double voltage_;
   double min_V_;
   double max_V_;
   double min_um_;
   double max_um_;
	//channel
	double pos_;		//position value in micron
	double ktemp_;		//ktemp in degree celsius
	int rohm_;		//operation time of actuator sine shiping
	int rgver_;			//versions number of loop-controller
	bool fenable_;		//actuator soft start (false=disable ; true=enable)
	double sr_;			//slew rate 

	bool modon_;		//modulation input MOD plug
	int monsrc_;		//monitor output
						//0 = position on close loop
						//1 = command value
						//2 = controller output voltate
						//3 = closed loop deviation
						//4 = absolute close loop deviation
						//5 = actuator voltage
						//6 = position in open loop

	//PID-controler
	bool loop_;			//open loop(false); close loop(true) 
	double kp_;			//proportional term
	double ki_;			//integral term
	double kd_;			//diferential term

	//notch filter (Kerbfilter)
	bool notchon_;		//notch filter off=false; on=true	
	int notchf_;		//notch filter frequency 0-20000 Hz
	int notchb_;		//bandwidth 0-20000 Hz
	bool lpon_;			//low pass filter
	int lpf_;		//low pass cut frequency 0-20000

	int gfkt_;			//internal funktion generator (table 12)
						//0 = off
						//1 = sine
						//2 = triangle
						//3 = rectangle
						//4 = noise
						//5 = sweep
						////6 = Close Loop
						////7 = OPen Loop
	//sine
	double gasin_;		//generator aplitute sine 0-100 %
	double gosin_;		//aplitute offset sine 0-100 %
	double gfsin_;		//generator frequenz sine 0-9999.9 HZ
	//triangle
	double gatri_;		//generator aplitute trianle 0-100 %
	double gotri_;		//aplitute offset tringle 0-100 %
	double gftri_;		//generator frequenz tri 0-9999.9 HZ
	double gstri_;		//symetry of triangle 0.1 - 99.9 % (default= 50 %)
	//rectangle
	double garec_;		//generator aplitute rectangle 0-100 %
	double gorec_;		//aplitute offset rectangle 0-100 %
	double gfrec_;		//generator frequenz rectangle 0-9999.9 HZ
	double gsrec_;		//symetry of rectangle 0.1 - 99.9 % (default= 50 %)
	//noise
	double ganoi_;		//generator aplitute noise 0-100 %
	double gonoi_;		//aplitute offset noise 0-100 %
	//sweep
	double gaswe_;		//generator aplitute sweep 0-100 %
	double goswe_;		//aplitute offset sweep 0-100 %
	double gtswe_;		//generator sweep time 0.4 - 800 [sec/decade]
	
	// scan
	int sct_;			//Scan Type
	bool ss_;			//Scan

	//trigger
	double trgss_;		//trigger position start
	double trgse_;		//trigger position end
	double trgsi_;		//trigger position intervals
	int trglen_;		//n*20us n=1...255
	int trgedge_;		//0 = trigger off
						//1 = trigger at rising edge
						//2 = trigger at falling edge
						//3 = trigger at both edges
	
   
};
#endif //_PSJ_DDRIVE_H_

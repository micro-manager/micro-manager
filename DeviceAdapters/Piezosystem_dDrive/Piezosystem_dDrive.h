// 
///////////////////////////////////////////////////////////////////////////////
// FILE:          Piezosystem_dDrive.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implementation of the Piezosystem device adapter.
//                
//                
// AUTHOR:        Chris Belter, cbelter@piezojena.com 15/07/13.  XYStage and ZStage by Chris Belter
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
#define ACTUATOR_PLUGGED					1		//= 0x0001   Bit0

//#define AKTUATOR_WITHOUT_MEASURE			!6		//= 0x0006 == 0   !Bit1+!Bit2
#define AKTUATOR_STRAIN_GAUGE_MEASURE		2		//= 0x0002   Bit1+!Bit2
#define AKTUATOR_CAPACITIVE_MEASURE			4		//= 0x0004   !Bit1+Bit2
#define AKTUATOR_INDUCTIVE_MEASURE			6		//= 0x0006   Bit1+Bit2

#define OPEN_LOOP_SYSTEM					16		//= 0x0010   Bit4   0=Close_loop; 1=open_loop 
#define PIEZO_VOLTAGE_ENABLE				64		//= 0x0040   Bit6
#define CLOSE_LOOP							128		//= 0x0080   Bit7
//status generator  
#define GENERATOR_OFF_MASK					3584	//= 0x0e00 == 0  !Bit9 + !Bit10 + !Bit11
#define GENERATOR_SINE						512		//= 0x0200   Bit9 + !Bit10 + !Bit11
#define GENERATOR_TRIANGLE					1024	//= 0x0400   !Bit9 + Bit10 + !Bit11
#define GENERATOR_RECTANGLE					1536	//= 0x0600   Bit9 + Bit10 + Bit11
#define GENERATOR_NOISE						2048	//= 0x0800   !Bit9 + !Bit10 + Bit11
#define GENERATOR_SWEEP						2560	//= 0x0a00   Bit9 + !Bit10 + Bit11
#define SCAN_SINE							3070	//= 0x0c00   !Bit9+ Bit10 + Bit11
#define SCAN_TRIANGLE						3584	//= 0x0e00   Bit9 + Bit10 + Bit11

#define NOTCH_FILTER_ON						4096	//= 0x1000   Bit12
#define LOW_PASS_FILTER_ON					8192	//= 0x2000   Bit13

// MMCore name of serial port
std::string port_ = "";
std::vector<int> inventoryDeviceAddresses_; // 1, 2, 6 etc.
std::vector<char> inventoryDeviceIDs_; //  X Y Z, etc.
std::vector<std::string> inventoryDeviceName_;
std::vector<std::string> inventoryDeviceSerNr_;

int clearPort(MM::Device& device, MM::Core& core, const char* port);
char** splitString(char* string,char* delimiter);


struct EVD{
	 bool initialized_;
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
	int rohm_;			//operation time of actuator sine shiping
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
	int lpf_;			//low pass cut frequency 0-20000

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

// N.B. Concrete device classes deriving EVDBase must set core_ in
// Initialize().
class EVDBase 
{
public:
	EVDBase(MM::Device *device);	
    virtual ~EVDBase();

protected:	
	bool initialized_;
    MM::Device *device_;
    MM::Core *core_;

	int SendCommand(const char* cmd,std::string &result);
    int SendServiceCommand(const char* cmd,std::string& result);  
    int GetCommandValue(const char* c,int ch,double& d);
    int GetCommandValue(const char* c,int ch,int& d);
    int SetCommandValue(const char* c,int ch,double fkt);
    int SetCommandValue(const char* c,int ch,int fkt);
	int GetStatus(int& stat, EVD* ch);
    int GetLimitsValues(EVD* struc);
	int GetActuatorName(char* id,int ch);
	int GetPos(double& pos, EVD* struc);
	int SetPos(double pos, EVD* struc);
    int GetLoop(bool& loop,int ch);
    int SetLoop(bool loop,int ch);
};

class PSJdevice
{
public:
	std::string devname_;		//device name
	std::string name_;			//actuator name
	std::string serie_;			//serien typ
	std::string comparename_;	//name with uppercase and without space
	std::string sn_;				//serialnumber
	std::vector<int> channel_;	//channels
};
std::vector<PSJdevice> devicelist_;

class Hub :  public HubBase<Hub>  //public CGenericBase<Hub>
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
	  int DetectInstalledDevices();
	  int GetActuatorName(int ch, char* id);
	  int GetSerialNumberActuator(int ch,char* sn);
	  int GetBright(int& b);
	  int SetBright(int b);
	  std::vector<int> GetDeviceAddresses(); 
	  std::string ConvertName(std::string name);
      // action interface
      // ---------------
      int OnPort (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnConfig (MM::PropertyBase* pProp, MM::ActionType eAct);      
      int OnTransmissionDelay (MM::PropertyBase* pProp, MM::ActionType eAct);
	  int OnBright (MM::PropertyBase* pProp, MM::ActionType eAct);

      // device discovery
      bool SupportsDeviceDetection(void);
      MM::DeviceDetectionStatus DetectDevice(void);

   private:      
	  int SendCommand(const char* cmd,std::string &result);
	  int SendServiceCommand(const char* cmd,std::string& result);
      std::vector<std::string> discoverableDevices_;      
	  int GetVersion(std::string& version);

      // Command exchange with MMCore
      std::string command_;
	  const char* name_;
	  int transmissionDelay_;
      bool initialized_;
	  int bright_;
};

class Stage : public CStageBase<Stage> , public EVDBase //
{



public:
   Stage();
   Stage(int channel);
   Stage(int channel,std::string name);
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
  int SetPositionSteps(long steps){	stepSizeUm_= steps; return DEVICE_UNSUPPORTED_COMMAND; }
  int GetPositionSteps(long& steps){steps = (long) stepSizeUm_; return DEVICE_UNSUPPORTED_COMMAND; }
//  int SetRelativePositionSteps(long steps);
//  int GetRelativePositionSteps(long& steps);
  int SetOrigin();
  int GetLimits(double& min, double& max);

  bool IsContinuousFocusDrive() const {return false;}  
  int GetChannel(int& channel);
  int SetChannel(int channel);
  int GetAxis(int& id);
  int GetActuatorName(char* id);
  int GetSerialNumberActuator(char* sn);
  int GetSerialNumberDevice(char* sn);
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
   int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
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
   int GetCommandValue(const char* c,double& d);
   int GetCommandValue(const char* c,int& d);
   int SetCommandValue(const char* c,double fkt);
   int SetCommandValue(const char* c,int fkt);
   int GetLimitsValues();  
   
   bool initialized_;
   double stepSizeUm_;
   std::string id_;
   char* ac_name_;		//actuator name
   EVD chx_;
  
};



class XYStage : public CXYStageBase<XYStage> , public EVDBase
{
public:
   XYStage();
   XYStage(int nr);
   XYStage(int nr,int chx, int chy);   
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
 
  int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
  int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
  double GetStepSizeXUm() {return stepSizeUm_;}
  double GetStepSizeYUm() {return stepSizeUm_;}
  int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_UNSUPPORTED_COMMAND;}
 
  // action interface
  // ----------------
  int OnNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnStatX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnStatY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnChannelX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnChannelY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnStepX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnStepY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPositionX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPositionY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnLoopX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnLoopY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTempX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTempY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTimeX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTimeY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSoftstartX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSoftstartY(MM::PropertyBase* pProp, MM::ActionType eAct);

  int OnSlewRateX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSlewRateY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnModulInputX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnModulInputY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnMonitorX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnMonitorY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPidPX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPidPY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPidIX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPidIY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPidDX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPidDY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnNotchX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnNotchY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnNotchFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnNotchFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnNotchBandX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnNotchBandY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnLowpassX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnLowpassY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnLowpassFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnLowpassFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);

  int OnGenerateX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnGenerateY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSinAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSinAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSinOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSinOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSinFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSinFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriSymX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriSymY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnRecAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnRecAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnRecOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnRecOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnRecFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnRecFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnRecSymX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnRecSymY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnNoiAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnNoiAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnNoiOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnNoiOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSweAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSweAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSweOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSweOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSweTimeX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSweTimeY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnScanTypeX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnScanTypeY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnScanX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnScanY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriggerStartX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriggerStartY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriggerEndX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriggerEndY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriggerIntervalX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriggerIntervalY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriggerTimeX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriggerTimeY(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriggerTypeX(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnTriggerTypeY(MM::PropertyBase* pProp, MM::ActionType eAct);
/**/

private:
   bool initialized_;
   int nr_;
   long xStep_;
   long yStep_;   
   EVD chx_;
   EVD chy_;
   double stepSizeUm_;
   int xChannel_;
   int yChannel_;
};

class Tritor : public CGenericBase<Tritor> ,EVDBase
{
public:
	Tritor();
	Tritor(int nr, const char* name);
	Tritor(int nr, const char* name,int chx,int chy,int chz);
	~Tritor();

	bool Busy(){return false;}
	void GetName(char* pszName) const;
	int Initialize();
    int Shutdown();


	int SetPositionUm(double x, double y, double z);
	int GetPositionUm(double& x, double& y, double& z);
    // action interface
    // ----------------
    
    int OnChannelX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannelY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannelZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStatX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnStatY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStatZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTempX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTempY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTempZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTimeX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTimeY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLoopX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLoopY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLoopZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPositionX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPositionY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPositionZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSoftstartX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSoftstartY(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSoftstartZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSlewRateX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSlewRateY(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSlewRateZ(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnModulInputX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnModulInputY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnModulInputZ(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnMonitorX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnMonitorY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMonitorZ(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPidPX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPidPY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPidPZ(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPidIX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPidIY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPidIZ(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPidDX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPidDY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPidDZ(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnNotchX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchBandX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchBandY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchBandZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowpassX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowpassY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowpassZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowpassFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowpassFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowpassFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnGenerateX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGenerateY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGenerateZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinOffZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriOffZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriSymX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriSymY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriSymZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecOffZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecSymX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecSymY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecSymZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNoiAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNoiAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNoiAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNoiOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNoiOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNoiOffZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweOffZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweTimeX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweTimeY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnScanTypeX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnScanTypeY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnScanTypeZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnScanX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnScanY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnScanZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerStartX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerStartY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerStartZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerEndX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerEndY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerEndZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerIntervalX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerIntervalY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerIntervalZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerTimeX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerTimeY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerTypeX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerTypeY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerTypeZ(MM::PropertyBase* pProp, MM::ActionType eAct);
private:
	int nr_;
	std::string name_;
	int xChannel_;
	int yChannel_;
	int zChannel_;
	EVD chx_;
	EVD chy_;
	EVD chz_;
	bool initialized_;
};

class Shutter : public CShutterBase<Shutter>,EVDBase
{
public:
   Shutter(int nr,const char *name);
   ~Shutter();

   bool Busy();
   void GetName(char* pszName) const;
   int Initialize();
   int Shutdown();
      
   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   // ----------------
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);   
   int OnStat(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLoop(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnModulInput(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMonitor(MM::PropertyBase* pProp, MM::ActionType eAct);
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
   std::string name_;
   unsigned shutterNumber_;
   bool initialized_;
   bool close_;			//zero volt position
   bool shut_;			//state
   EVD chx_;  
   double answerTimeoutMs_;
   MM::MMTime changedTime_;
};

class Mirror : public CGenericBase<Mirror> ,EVDBase
{
public:
	Mirror();
	Mirror(int nr, int count, const char* name);
	~Mirror();

	bool Busy(){return false;}
	void GetName(char* pszName) const;
	int Initialize();
    int Shutdown();


	
    // action interface
    // ----------------
    
    int OnChannelX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannelY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannelZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStatX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnStatY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStatZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTempX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTempY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTempZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTimeX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTimeY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLoopX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLoopY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLoopZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPositionX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPositionY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPositionZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSoftstartX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSoftstartY(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSoftstartZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSlewRateX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSlewRateY(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSlewRateZ(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnModulInputX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnModulInputY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnModulInputZ(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnMonitorX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnMonitorY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMonitorZ(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPidPX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPidPY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPidPZ(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPidIX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPidIY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPidIZ(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPidDX(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPidDY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPidDZ(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnNotchX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchBandX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchBandY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNotchBandZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowpassX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowpassY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowpassZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowpassFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowpassFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLowpassFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnGenerateX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGenerateY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGenerateZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinOffZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSinFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriOffZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriSymX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriSymY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriSymZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecOffZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecFreqX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecFreqY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecSymX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecSymY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRecSymZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNoiAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNoiAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNoiAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNoiOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNoiOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNoiOffZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweAmpX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweAmpY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweOffX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweOffY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweOffZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweTimeX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweTimeY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSweTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnScanTypeX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnScanTypeY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnScanTypeZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnScanX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnScanY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnScanZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerStartX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerStartY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerStartZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerEndX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerEndY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerEndZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerIntervalX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerIntervalY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerIntervalZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerTimeX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerTimeY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerTypeX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerTypeY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerTypeZ(MM::PropertyBase* pProp, MM::ActionType eAct);
private:
	int nr_;
	int channelcount_;
	std::string name_;
	int xChannel_;
	int yChannel_;
	int zChannel_;
	EVD chx_;
	EVD chy_;
	EVD chz_;
	bool initialized_;
};

#endif //_PIEZOSYSTEM_DDRIVE_H_

///////////////////////////////////////////////////////////////////////////////
// FILE:          Tofra.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for TOFRA devices
//
// COPYRIGHT:     TOFRA, Inc., Palo Alto, CA
//                University of California, San Francisco, CA
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER(S) OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Ilya Ravkin, based on Code by Nenad Amodaj and Nico Stuurman
//


#ifndef _TOFRA_H_
#define _TOFRA_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION			10003
#define ERR_BAD_CONTROLLER_RESPONSE		10010

int ClearPort(MM::Device& device, MM::Core& core, std::string port);
int AnalyzeAnswer(std::string answer);
int isnumeric(const char *str);

class TofraFilterWheel : public CStateDeviceBase<TofraFilterWheel>
{
public:
   TofraFilterWheel();
   ~TofraFilterWheel();
  
   // MM Device API
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return NumPos;}

	// Action interface
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnControllerName(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHomeOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSlewVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInitVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHoldCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRunCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNumPos(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   
	std::string ControllerName;
	long HomeOffset;
	long SlewVelocity;
	long InitVelocity;
	long Acceleration;
	long HoldCurrent;
	long RunCurrent;
	long NumPos;

	bool initialized_;
	MM::MMTime changedTime_;
	long position_;
	std::string port_;

	int InitializeFilterWheel();
};

class ZStage : public CStageBase<ZStage>
{
public:
	ZStage();
	~ZStage();
  
	// MM Device API
	int Initialize();
	int Shutdown();
  
	void GetName(char* pszName) const;
	bool Busy();

	// Stage API
	int SetPositionUm(double pos);
	int GetPositionUm(double& pos);
	int SetRelativePositionUm(double d);
	int SetPositionSteps(long steps);
	int GetPositionSteps(long& steps);
	int SetOrigin();
	int GetLimits(double& min, double& max);
	int Stop();
	int Home();
	int Move(double speed);

	// Action interface
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnControllerName(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSlewVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInitVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHoldCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRunCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMotorSteps(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFullTurnUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWithLimits(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOut1(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOut2(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnExecute(MM::PropertyBase* pProp, MM::ActionType eAct);

    int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
    bool IsContinuousFocusDrive() const {return false;}



private:
   
	std::string ControllerName;
	double SlewVelocity;
	double InitVelocity;
	double Acceleration;
	long HoldCurrent;
	long RunCurrent;
	long MotorSteps;
	long FullTurnUm;
	long WithLimits;
	std::string Position;
	double Speed;

	bool initialized_;
	MM::MMTime changedTime_;
	double stepSizeUm_;
	std::string port_;
   
	std::string Execute;
	long Out1;
	long Out2;

	int InitializeZDrive();
	int SendCommand(const char* cmd);
	int Out();
};

class XYStage : public CXYStageBase<XYStage>
{
public:
	XYStage();
	~XYStage();

	// MM Device API
	// ----------
	int Initialize();
	int Shutdown();
  
	void GetName(char* pszName) const;
	bool Busy();

	// XYStage API
	// -----------
	int SetPositionUm(double x, double y);
	int SetRelativePositionUm(double dx, double dy);
	// int SetAdapterOriginUm(double x, double y);
	int GetPositionUm(double& x, double& y);
	int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);

	int SetPositionSteps(long x, long y);
	int GetPositionSteps(long& x, long& y);
	int SetRelativePositionSteps(long x, long y);
	int Home();
	int Stop();
	int Move(double speedx, double speedy);
	int SetOrigin();
	// int SetAdapterOrigin();
	int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
	double GetStepSizeXUm() {return stepSizeUmX_;}
	double GetStepSizeYUm() {return stepSizeUmX_;}

   	// Action interface
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnControllerNameX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnControllerNameY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSlewVelocityX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSlewVelocityY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInitVelocityX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInitVelocityY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAccelerationX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAccelerationY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHoldCurrentX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHoldCurrentY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRunCurrentX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRunCurrentY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMotorStepsX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMotorStepsY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLeadUmX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLeadUmY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPositionX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPositionY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSpeedX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSpeedY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOut1X(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOut1Y(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOut2X(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOut2Y(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLimitPolarityX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLimitPolarityY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnExecuteX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnExecuteY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStepDivideX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStepDivideY(MM::PropertyBase* pProp, MM::ActionType eAct);

    int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

private:
	std::string ControllerNameX;
	long StepDivideX;
	double SlewVelocityX;
	double InitVelocityX;
	double AccelerationX;
	long HoldCurrentX;
	long RunCurrentX;
	long MotorStepsX;
	long LeadUmX;
	std::string PositionX;
	double SpeedX;
	long Out1X;
	long Out2X;
	long LimitPolarityX;
	double stepSizeUmX_;
	std::string ExecuteX;

	std::string ControllerNameY;
	long StepDivideY;
	double SlewVelocityY;
	double InitVelocityY;
	double AccelerationY;
	long HoldCurrentY;
	long RunCurrentY;
	long MotorStepsY;
	long LeadUmY;
	std::string PositionY;
	double SpeedY;
	long Out1Y;
	long Out2Y;
	long LimitPolarityY;
	double stepSizeUmY_;
	std::string ExecuteY;

	bool initialized_;
	MM::MMTime changedTime_;
	std::string port_;

	bool BusyX();
	bool BusyY();
	int SetPositionUmX(double pos);
	int SetPositionUmY(double pos);
	int GetPositionUmX(double& pos);
	int GetPositionUmY(double& pos);
	int SetPositionStepsX(long pos);
	int SetPositionStepsY(long pos);
	int GetPositionStepsX(long& steps);
	int GetPositionStepsY(long& steps);
	int SetOriginX();
	int SetOriginY();
	int StopX();
	int StopY();
	int HomeX();
	int HomeY();
	int SetRelativePositionStepsX(long steps);
	int SetRelativePositionStepsY(long steps);
	int SetRelativePositionUmX(double d);
	int SetRelativePositionUmY(double d);
	int MoveX(double speed);
	int MoveY(double speed);
	int OutX();
	int OutY();
	int InitializeXYStageX();
	int InitializeXYStageY();
	int InitializeXYStage();
	int SendCommand(const char* con, const char* cmd);

};

class CubeSlider : public CStateDeviceBase<CubeSlider>
{
public:
   CubeSlider();
   ~CubeSlider();
  
   // MM Device API
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return NumPos;}

	// Action interface
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnControllerName(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSlewVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInitVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHomeSlewVel(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHomeInitVel(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHomeAccel(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHoldCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRunCurrent(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNumPos(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHomeOffsetUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBetweenPosUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLeadUm(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMotorSteps(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnStepDivide(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	std::string ControllerName;
	double SlewVelocity;
	double InitVelocity;
	double Acceleration;
	double HomeSlewVel;
	double HomeInitVel;
	double HomeAccel;
	double stepSizeUm_;
	long HoldCurrent;
	long RunCurrent;
	long NumPos;
	long HomeOffsetUm;
	long BetweenPosUm;
	long LeadUm;
	long MotorSteps;
	long StepDivide;

	bool initialized_;
	MM::MMTime changedTime_;
	long position_;
	std::string port_;

	int InitializeCubeSlider();
};


class rgbLED : public CStateDeviceBase<rgbLED>
{
public:
   rgbLED();
   ~rgbLED();
  
   // MM Device API
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return NumPos;}

	// Action interface
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnNumPos(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannel1Intensity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannel2Intensity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannel3Intensity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannel4Intensity(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	bool initialized_;
	MM::MMTime changedTime_;
	long position_;
	std::string port_;
	long delay_;
	double Channel1Intensity;
	double Channel2Intensity;
	double Channel3Intensity;
	double Channel4Intensity;
	long NumPos;

	int InitializergbLED();
	int ChannelIntensity(long channel, double intensity);
};


class RGBLEDShutter : public CShutterBase<RGBLEDShutter>
{
public:
	RGBLEDShutter();
	virtual ~RGBLEDShutter();

	virtual int Initialize();
	virtual int Shutdown();

	virtual void GetName(char* name) const;
	virtual bool Busy();

	virtual int GetOpen(bool& open);
	virtual int SetOpen(bool open);
	virtual int Fire(double) { return DEVICE_UNSUPPORTED_COMMAND; }

private:
	int OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannelIntensity(MM::PropertyBase* pProp, MM::ActionType eAct,
			long chan);

private:
	int ApplyIntensities(bool open);
	int SetChannelIntensity(int chan, double intensity);

private:
	bool initialized_;
	std::string port_;
	bool open_;
	static const unsigned nChannels_ = 4;
	double channelIntensities_[nChannels_];
	static const long delay_ = 20;
};

#endif //_TOFRA_H_

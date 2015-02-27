///////////////////////////////////////////////////////////////////////////////
// FILE:          StandaStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// AUTHOR:        Ed Simmons - ESImaging www.esimagingsolutions.com
//
// COPYRIGHT:     Ed Simmons 2013
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

#ifndef _STANDASTAGE_H_
#define _STANDASTAGE_H_


#include "USMCDLL.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>
#include <algorithm>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_IN_SEQUENCE          104
#define ERR_SEQUENCE_INACTIVE    105
#define ERR_STAGE_MOVING         106
#define SIMULATED_ERROR          200
#define LIBRARY_ERROR            201
#define HUB_NOT_AVAILABLE        107

const char* NoHubError = "Parent Hub not defined.";

////////////////////////
// CStandaHub
//////////////////////

class CStandaHub : public HubBase<CStandaHub>
{
public:
   CStandaHub();
   ~CStandaHub() {};

   // Device API
   // ---------
   int Initialize();
   int Shutdown() {return DEVICE_OK;};
   void GetName(char* pName) const; 
   bool Busy() { return busy_;} ;

   // HUB api
   int DetectInstalledDevices();
   MM::Device* CreatePeripheralDevice(const char* adapterName);

   std::vector<std::string> GetDriveNames();

private:
   void GetPeripheralInventory();
   int RefreshDeviceList();
   bool busy_;
   bool initialized_;
   std::vector<std::string> peripherals_;

   int numberOfDevices_;
   std::vector<std::string> driveNames_; 

};



//////////////////////////////////////////////////////////////////////////////
// CStandaStage class
// single axis stage
//////////////////////////////////////////////////////////////////////////////

class CStandaStage : public CStageBase<CStandaStage>
{
public:
   CStandaStage();
   ~CStandaStage();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // Stage API
   int SetPositionUm(double pos);
   int GetPositionUm(double& pos);
   double GetStepSize() {return stepSizeUm_;}
   int SetPositionSteps(long steps);
   int GetPositionSteps(long& steps);
   int SetOrigin()
   {
      return DEVICE_OK;
   }
   int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }
   int Move(double /*v*/) {return DEVICE_OK;}

   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------

   int OnDriveSelected(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   
   int OnStepsPerSecond(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMicrostepMultiplier(MM::PropertyBase* pProp, MM::ActionType eAct);
   
   
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);




   // Sequence functions
   int IsStageSequenceable(bool& isSequenceable) const {
      isSequenceable = false;
      return DEVICE_OK;
   }
   int GetStageSequenceMaxLength(long& nrEvents) const 
   {
      nrEvents = 0; return DEVICE_OK;
   }
   int StartStageSequence() const
   {
      return DEVICE_OK;
   }
   int StopStageSequence() const
   {  
      return DEVICE_OK;
   }
   int ClearStageSequence() {return DEVICE_OK;}
   int AddToStageSequence(double /* position */) {return DEVICE_OK;}
   int SendStageSequence() const {return DEVICE_OK;}

private:
   
   USMC_State State;
   USMC_StartParameters StPrms;
   USMC_Parameters Prms;
   USMC_Mode Mode;
   USMC_EncoderState EnState;
   bool initAxis(DWORD Dev, USMC_StartParameters StPrms);
   bool setPower(DWORD Dev,bool state);

   double stepSizeUm_;
   double pos_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;

   unsigned int divisor_;

   unsigned int driveID_;
   float stepsPerSecond_;

   std::vector<std::string> drives; // cached copy of the drive list


};

//////////////////////////////////////////////////////////////////////////////
// CStandaXYStage class
// 2 axis stage
//////////////////////////////////////////////////////////////////////////////

class CStandaXYStage : public CXYStageBase<CStandaXYStage>
{
public:
   CStandaXYStage();
   ~CStandaXYStage();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // XYStage API
   /* Note that only the Set/Get PositionStep functions are implemented in the adapter
    * It is best not to override the Set/Get PositionUm functions in DeviceBase.h, since
    * those implement corrections based on whether or not X and Y directionality should be 
    * mirrored and based on a user defined origin
    */

   // This must be correct or the conversions between steps and Um will go wrong - is this esential? if I use the individual axes with different step sizes, how does this work?!
   //virtual double GetStepSize() {return stepSize_um_;}

   int SetPositionSteps(long x, long y);
   int GetPositionSteps(long& x, long& y);
   int SetRelativePositionSteps(long x, long y)                                                           
   {
      long xSteps, ySteps;                                                                                
      GetPositionSteps(xSteps, ySteps);                                                   

      return this->SetPositionSteps(xSteps+x, ySteps+y);                                                  
   } 
   virtual int Home()
   {
      return DEVICE_OK;
   }
   virtual int Stop()
   {
      return DEVICE_OK;
   }

   /* This sets the 0,0 position of the adapter to the current position.  
    * If possible, the stage controller itself should also be set to 0,0
    * Note that this differs form the function SetAdapterOrigin(), which 
    * sets the coordinate system used by the adapter
    * to values different from the system used by the stage controller
    */
   virtual int SetOrigin()
   {
      return DEVICE_OK;
   }
   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimitX_;
      upper = upperLimitY_;
      return DEVICE_OK;
   }
   virtual int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
   {
      xMin = lowerLimitX_; xMax = upperLimitX_;
      yMin = lowerLimitY_; yMax = upperLimitY_;
      return DEVICE_OK;
   }

   virtual int GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
   double GetStepSizeXUm()
   {
      return stepSize_X_um_;
   }
   double GetStepSizeYUm()
   {
      return stepSize_Y_um_;
   }
   int Move(double /*vx*/, double /*vy*/) {return DEVICE_OK;}

   int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}


   // action interface
   // ----------------

   int OnXDriveSelected(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnYDriveSelected(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnXStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnXStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnYStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnYStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   
   int OnXStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnYStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnStepsPerSecond(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMicrostepMultiplier(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   double stepSize_X_um_;
   double stepSize_Y_um_;
   double posX_um_;
   double posY_um_;
   unsigned int driveID_X_;
   unsigned int driveID_Y_;

   bool busy_;
   MM::TimeoutMs* timeOutTimer_;
   double velocity_;
   bool initialized_;
   double lowerLimitX_;
   double upperLimitX_;
   double lowerLimitY_;
   double upperLimitY_;

   float stepsPerSecond_;

   std::vector<std::string> drives; // cached copy of the drive list

   // params for each axis
   USMC_State X_State;
   USMC_StartParameters X_StPrms;
   USMC_Parameters X_Prms;
   USMC_Mode X_Mode;
   USMC_EncoderState X_EnState;

   USMC_State Y_State;
   USMC_StartParameters Y_StPrms;
   USMC_Parameters Y_Prms;
   USMC_Mode Y_Mode;
   USMC_EncoderState Y_EnState;

   bool initAxis(DWORD Dev, USMC_StartParameters StPrms, USMC_Mode Mode, USMC_State State);
   bool setPower(DWORD Dev,bool state, USMC_Mode Mode);

};




#endif //_STANDASTAGE_H_

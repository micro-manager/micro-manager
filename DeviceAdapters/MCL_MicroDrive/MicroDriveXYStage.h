/*
File:		MicroDriveXYStage.h
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#ifndef _MICRODRIVEXYSTAGE_H_
#define _MICRODRIVEXYSTAGE_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "MCL_MicroDrive.h"
#include "MicroDrive.h"

#include <string>
#include <map>

#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_NOT_VALID_INPUT      104

class MicroDriveXYStage : public CXYStageBase<MicroDriveXYStage>
{
public:
   MicroDriveXYStage();
   ~MicroDriveXYStage();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // XYStage API
   virtual double GetStepSize();
   virtual int SetPositionSteps(long x, long y);
   virtual int GetPositionSteps(long& x, long& y);
   virtual int Home();
   virtual int Stop();
   virtual int SetOrigin();
   virtual int GetLimits(double& lower, double& upper);
   virtual int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
   virtual int GetStepLimits(long &xMin, long &xMax, long &yMin, long &yMax);
   virtual double GetStepSizeXUm();
   virtual double GetStepSizeYUm();
   virtual int SetRelativePositionUm(double dx, double dy);
   virtual int IsXYStageSequenceable(bool& isSequenceable) const; 
   

   // Action interface
   int OnPositionXmm(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPositionYmm(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetOriginHere(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCalibrate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReturnToOrigin(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPositionXYmm(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   /// Private methods

   // Initialization
   int CreateMicroDriveXYProperties();

   // Set positions
   int SetPositionMm(double x, double y);
   int GetPositionMm(double& x, double& y);
   int SetRelativePositionMm(double x, double y);
   int SetPositionXSteps(long x);
   int SetPositionYSteps(long y);
   int SetPositionUm(double x, double y);
   int GetPositionUm(double& x, double& y);

   // Calibration & origin methods
   int Calibrate();
   int MoveToForwardLimits();
   int ReturnToOrigin();

   // Pause devices
   void PauseDevice();

   // Check if blocked
   bool XMoveBlocked(double possNewPos);
   bool YMoveBlocked(double possNewPos);

   void GetOrientation(bool& mirrorX, bool& mirrorY);

   /// Private variables
   int MCLhandle_;
   double stepSize_mm_;
   bool busy_;
   bool initialized_;
   double encoderResolution_; 
   double maxVelocity_;
   double minVelocity_;
   double velocity_;
   int rounding_;
};


#endif //_MICRODRIVEXYSTAGE_H_

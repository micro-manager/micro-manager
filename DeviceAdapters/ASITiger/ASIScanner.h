///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIScanner.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI scanner device adapter
//
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 09/2013
//
// BASED ON:      MicroPoint.h and others
//

#ifndef _ASIScanner_H_
#define _ASIScanner_H_

#include "ASIPeripheralBase.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

class CScanner : public ASIPeripheralBase<CGalvoBase, CScanner>
{
public:
   CScanner(const char* name);
   ~CScanner() { Shutdown(); }
  
   // Device API
   // ----------
   int Initialize();
   bool Busy();

   // Galvo API
   // -----------
   int SetPosition(double x, double y);
   int GetPosition(double& x, double& y);
   double GetXRange() { return limitX_; }  // this is only positive limit, on power-up limits are +/- this value
   double GetYRange() { return limitY_; }  // this is only positive limit, on power-up limits are +/- this value
   int AddPolygonVertex(int polygonIndex, double x, double y);
   int DeletePolygons();
   int LoadPolygons();
   int SetPolygonRepetitions(int repetitions);
   int RunPolygons();
   int RunSequence();
   int StopSequence() { return DEVICE_UNSUPPORTED_COMMAND; }  // doesn't appear to be used in MMCore.cpp anyway

   // below aren't really implemented but we do the closest thing we can with our hardware
   int PointAndFire(double x, double y, double /*time_us*/) { return SetPosition(x, y); }  // we can't control beam time but go to location
   int SetSpotInterval(double /*pulseInterval_us*/) { return DEVICE_OK; }  // we can't actual control beam time so just ignore
   int SetIlluminationState(bool on);  // we can't turn off beam but we can steer beam to corner where hopefully it is blocked internally
   int GetChannel(char* channelName);

   // useful functions used internally
   void UpdateIlluminationState();

   // action interface
   // ----------------
   int OnSaveCardSettings     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRefreshProperties    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLowerLimX            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLowerLimY            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUpperLimX            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUpperLimY            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMode                 (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCutoffFreqX          (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCutoffFreqY          (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAttenuateTravelX     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAttenuateTravelY     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJoystickFastSpeed    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJoystickSlowSpeed    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJoystickMirror       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJoystickSelectX      (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJoystickSelectY      (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWheelFastSpeed       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWheelSlowSpeed       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWheelMirror          (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisPolarityX        (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisPolarityY        (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBeamEnabled          (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAAmplitudeX         (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAOffsetX            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAPeriodX            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAModeX              (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAPatternX           (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAAdvancedX          (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAAmplitudeY         (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAOffsetY            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAPeriodY            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAModeY              (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAPatternY           (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAAdvancedY          (MM::PropertyBase* pProp, MM::ActionType eAct);
   // "advanced" single axis properties
   int OnSAClkSrcY            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAClkPolY            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSATTLOutY            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSATTLPolY            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAPatternByteY       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAClkSrcX            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAClkPolX            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSATTLOutX            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSATTLPolX            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAPatternByteX       (MM::PropertyBase* pProp, MM::ActionType eAct);
   // SPIM properties
   int OnSPIMScansPerSlice    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMNumSlices        (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMNumSlicesPerPiezo(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMNumSides         (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMFirstSide        (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMScannerHomeEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMPiezoHomeEnable  (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMModeByte         (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMNumRepeats       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMState            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMCameraDuration   (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMLaserDuration    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMDelayBeforeScan  (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMDelayBeforeCamera(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMDelayBeforeLaser (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMDelayBeforeSide  (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSPIMDelayBeforeRepeat(MM::PropertyBase* pProp, MM::ActionType eAct);
   // ring buffer properties
   int OnRBDelayBetweenPoints (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRBMode               (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRBTrigger            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRBRunning            (MM::PropertyBase* pProp, MM::ActionType eAct);
   // laser TTL properties
   int OnLaserOutputMode      (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLaserSwitchTime      (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   string axisLetterX_;
   string axisLetterY_;
   double unitMultX_;  // units per degree
   double unitMultY_;  // units per degree
   double limitX_;   // positive limit only (on power-up things are symmetric about 0)
   double limitY_;   // positive limit only (on power-up things are symmetric about 0)
   double shutterX_; // home position, used to turn beam off
   double shutterY_;  // home position, used to turn beam off
   double lastX_;   // used to cache position
   double lastY_;   // used to cache position
   bool illuminationState_;  // true if on, false if beam is turned off
   bool refreshOverride_;  // true temporarily if refreshing property

   struct saStateType {
      long mode;
      long pattern;
   };

   saStateType saStateX_;
   saStateType saStateY_;

   // for polygons
   vector< pair<double,double> > polygons_;
   long polygonRepetitions_;
   bool ring_buffer_supported_;

   unsigned char laser_side_;  // code for corresponding laser line: 0 for none, 1 for side0, 2 for side1
   bool laserTTLenabled_;      // whether it has MM_LASER_TTL module

   int SetIlluminationStateHelper(bool on);
   int OnSaveJoystickSettings();
};

#endif //_ASIScanner_H_

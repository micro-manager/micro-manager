///////////////////////////////////////////////////////////////////////////////
// FILE:          Utilitiesr.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Various 'Meta-Devices' that add to or combine functionality of 
//                physcial devices.
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 11/07/2008
// COPYRIGHT:     University of California, San Francisco, 2008
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

#ifndef _UTILITIES_H_
#define _UTILITIES_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_INVALID_DEVICE_NAME            10001
#define ERR_NO_DA_DEVICE                   10002
#define ERR_VOLT_OUT_OF_RANGE              10003
#define ERR_POS_OUT_OF_RANGE               10004
#define ERR_NO_DA_DEVICE_FOUND             10005
#define ERR_NO_STATE_DEVICE                10006
#define ERR_NO_STATE_DEVICE_FOUND          10007
#define ERR_NO_AUTOFOCUS_DEVICE            10008
#define ERR_NO_AUTOFOCUS_DEVICE_FOUND      10009
#define ERR_NO_AUTOFOCUS_DEVICE_FOUND      10009
#define ERR_DEFINITE_FOCUS_TIMEOUT         10020
#define ERR_TIMEOUT                        10021

/*
 * MultiShutter: Combines multiple physical shutters into one logical device
 */
class MultiShutter : public CShutterBase<MultiShutter>
{
public:
   MultiShutter();
   ~MultiShutter();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}
  
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open) {open = open_; return DEVICE_OK;}
   int Fire (double /* deltaT */) { return DEVICE_UNSUPPORTED_COMMAND;}
   // ---------

   // action interface
   // ----------------
   int OnPhysicalShutter(MM::PropertyBase* pProp, MM::ActionType eAct, long index);

private:
   std::vector<std::string> availableShutters_;
   std::vector<std::string> usedShutters_;
   std::vector<MM::Shutter*> physicalShutters_;
   long nrPhysicalShutters_;
   bool open_;
   bool initialized_;
};

/**
 * DAShutter: Adds shuttering capabilities to a DA device
 */
class DAShutter : public CShutterBase<DAShutter>
{
public:
   DAShutter();
   ~DAShutter();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}
  
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire (double /* deltaT */) { return DEVICE_UNSUPPORTED_COMMAND;}
   // ---------

   // action interface
   // ----------------
   int OnDADevice(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::vector<std::string> availableDAs_;
   std::string DADeviceName_;
   MM::SignalIO* DADevice_;
   bool initialized_;
};

/**
 * Allows a DA device to act like a Drive (better hook it up to a drive!)
 */
class DAZStage : public CStageBase<DAZStage>
{
public:
   DAZStage();
   ~DAZStage();
  
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
  int SetPositionSteps(long steps);
  int GetPositionSteps(long& steps);
  int SetOrigin();
  int GetLimits(double& min, double& max);

   // action interface
   // ----------------
   int OnDADevice(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMinVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::vector<std::string> availableDAs_;
   std::string DADeviceName_;
   MM::SignalIO* DADevice_;
   bool initialized_;
   double minDAVolt_;
   double maxDAVolt_;
   double minStageVolt_;
   double maxStageVolt_;
   double minStagePos_;
   double maxStagePos_;
   double pos_;
   double originPos_;
};

/**
 * Treats an AutoFocus device as a Drive.
 * Can be used to make the AutoFocus offset appear in the position list
 */
class AutoFocusStage : public CStageBase<AutoFocusStage>
{
public:
   AutoFocusStage();
   ~AutoFocusStage();
  
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
  int SetPositionSteps(long steps);
  int GetPositionSteps(long& steps);
  int SetOrigin();
  int GetLimits(double& min, double& max);

   // action interface
   // ----------------
   int OnAutoFocusDevice(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::vector<std::string> availableAutoFocusDevices_;
   std::string AutoFocusDeviceName_;
   MM::AutoFocus* AutoFocusDevice_;
   bool initialized_;
   double pos_;
   double originPos_;
};

/**
 * StateDeviceShutter: Adds shuttering capabilities to a State Device
 */
class StateDeviceShutter : public CShutterBase<StateDeviceShutter>
{
public:
   StateDeviceShutter();
   ~StateDeviceShutter();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}
  
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire (double /* deltaT */) { return DEVICE_UNSUPPORTED_COMMAND;}
   // ---------

   // action interface
   // ----------------
   int OnStateDevice(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int WaitWhileBusy();
   std::vector<std::string> availableStateDevices_;
   std::string stateDeviceName_;
   MM::State* stateDevice_;
   bool initialized_;
};

#endif //_UTILITIES_H_

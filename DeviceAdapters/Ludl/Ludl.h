///////////////////////////////////////////////////////////////////////////////
// FILE:          Ludl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The implementation of the Ludl device adapter.
//                Should also work with ASI devices.
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 10/27/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
//
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
//                CVS: $Id$
//

#ifndef _LUDL_H_
#define _LUDL_H_

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

class XYStage : public CXYStageBase<XYStage>
{
public:
   XYStage();
   ~XYStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // XYStage API
   // -----------
  int SetPositionUm(double x, double y);
  int GetPositionUm(double& x, double& y);
  int SetPositionSteps(long x, long y);
  int GetPositionSteps(long& x, long& y);
  int Home();
  int Stop();
  int SetOrigin();//jizhen 4/12/2007
  int GetLimits(double& xMin, double& xMax, double& yMin, double& yMax);

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIDX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIDY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCommandSet(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ExecuteCommand(const std::string& cmd, std::string& response);
   
   std::string port_;
   double stepSizeUm_;
   bool initialized_;
   double answerTimeoutMs_;
   unsigned idX_;
   unsigned idY_;
   bool HLCommandSet_;
};

class Stage : public CStageBase<Stage>
{
public:
   Stage();
   ~Stage();
  
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
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnID(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCommandSet(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAutofocus(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ExecuteCommand(const std::string& cmd, std::string& response);
   int Autofocus(long param);

   std::string port_;
   double stepSizeUm_;
   bool initialized_;
   double answerTimeoutMs_;
   unsigned id_;
   bool HLCommandSet_;
};

class ASIController : public CGenericBase<ASIController>
{
public:
   ASIController();
   ~ASIController();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   MM::DeviceType GetType() const {return MM::GenericDevice;}

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnResponse(MM::PropertyBase* pProp, MM::ActionType eAct);;

private:
   int ExecuteCommand(const std::string& cmd, std::string& response);

   std::string port_;
   bool initialized_;
   double answerTimeoutMs_;
   std::string command_;
   std::string response_;
};


#endif //_LUDL_H_

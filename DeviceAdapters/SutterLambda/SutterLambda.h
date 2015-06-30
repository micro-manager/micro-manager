///////////////////////////////////////////////////////////////////////////////
// FILE:          SutterLambda.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Sutter Lambda controller adapter
// COPYRIGHT:     University of California, San Francisco, 2006
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 10/26/2005
//                Nico Stuurman, Oct. 2010
//
// CVS:           $Id$
//

#ifndef _SUTTER_LAMBDA_H_
#define _SUTTER_LAMBDA_H_

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
#define ERR_UNKNOWN_SHUTTER_MODE     10006
#define ERR_UNKNOWN_SHUTTER_ND       10007
#define ERR_NO_ANSWER                10008

class SutterUtils
{
   public:
      static bool ControllerBusy(MM::Device& device, MM::Core& core, 
            std::string port, unsigned long answerTimeoutMs);
      static int GoOnLine(MM::Device& device, MM::Core& core, 
               std::string port, unsigned long answerTimeoutMs);
      static int GetControllerType(MM::Device& device, MM::Core& core, 
            std::string port, unsigned long answerTimeoutMs, std::string& type,
            std::string& id);
      static int GetStatus(MM::Device& device, MM::Core& core, 
            std::string port, unsigned long answerTimeoutMs, 
            unsigned char* status);

     static int SetCommand(MM::Device& device, MM::Core& core, 
         const std::string port, const std::vector<unsigned char> command, const std::vector<unsigned char> alternateEcho, 
         const unsigned long answerTimeoutMs, const bool responseRequired = true, const bool CRexpected = true);

      static int SetCommand(MM::Device& device, MM::Core& core, 
         const std::string port, const std::vector<unsigned char> command, const std::vector<unsigned char> alternateEcho, 
         const unsigned long answerTimeoutMs, std::vector<unsigned char>& response, const bool responseRequired = true, const bool CRExpected = true);

      // some commands don't send a \r!!!
      static int SetCommandNoCR(MM::Device& device, MM::Core& core, 
         const std::string port, const std::vector<unsigned char> command, const std::vector<unsigned char> alternateEcho, 
         const unsigned long answerTimeoutMs, std::vector<unsigned char>& response, const bool responseRequired = true);



};

class Wheel : public CStateDeviceBase<Wheel>
{
public:
   Wheel(const char* name, unsigned id);
   ~Wheel();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBusy(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAnswerTimeout(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool SetWheelPosition(unsigned pos);

   bool initialized_;
   unsigned numPos_;
   const unsigned id_;
   std::string name_;
   std::string port_;
   unsigned curPos_;
   bool open_;
   unsigned speed_;
   double answerTimeoutMs_;
   Wheel& operator=(Wheel& /*rhs*/) {assert(false); return *this;}
};

class Shutter : public CShutterBase<Shutter>
{
public:
   Shutter(const char* name, int id);
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
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnND(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnControllerID(MM::PropertyBase* pProp, MM::ActionType eAct);
   // for device discovery:
   MM::DeviceDetectionStatus DetectDevice(void);


private:
   bool ControllerBusy();
   bool SetShutterPosition(bool state);
   bool SetShutterMode(const char* mode);
   bool SetND(unsigned int nd);
   int GetControllerType(std::string& type, std::string& id);
   int GoOnLine();

   bool initialized_;
   const int id_;
   std::string name_;
   unsigned int nd_;
   std:: string port_;
   std::string controllerType_;
   std::string controllerId_;
   double answerTimeoutMs_;
   MM::MMTime changedTime_;
   std::string curMode_;
   Shutter& operator=(Shutter& /*rhs*/) {assert(false); return *this;}
};

#ifdef DefineShutterOnTenDashTwo 
class ShutterOnTenDashTwo : public CShutterBase<ShutterOnTenDashTwo>
{
public:
   ShutterOnTenDashTwo(const char* name, int id);
   ~ShutterOnTenDashTwo();

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
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMode(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool ControllerBusy();
   bool SetShutterPosition(bool state);
   bool SetShutterMode(const char* mode);
   bool initialized_;
   const int id_;
   std::string name_;
   std:: string port_;
   double answerTimeoutMs_;
   MM::MMTime changedTime_;
   std::string curMode_;
   ShutterOnTenDashTwo& operator=(ShutterOnTenDashTwo& /*rhs*/) {assert(false); return *this;}
};

#endif

class DG4Wheel : public CStateDeviceBase<DG4Wheel>
{
public:
   DG4Wheel();
   ~DG4Wheel();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBusy(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool SetWheelPosition(unsigned pos);

   bool initialized_;
   unsigned numPos_;
   std::string name_;
   std::string port_;
   unsigned curPos_;
   double answerTimeoutMs_;
};

class DG4Shutter : public CShutterBase<DG4Shutter>
{
public:
   DG4Shutter();
   ~DG4Shutter();

   bool Busy() {return false;}
   void GetName(char* pszName) const;
   int Initialize();
   int Shutdown();
      
   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool SetShutterPosition(bool state);
   bool initialized_;
   std::string name_;
   std:: string port_;
   double answerTimeoutMs_;
};
#endif //_SUTTER_LAMBDA_H_

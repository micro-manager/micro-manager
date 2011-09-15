///////////////////////////////////////////////////////////////////////////////
// FILE:          LeicaDMR.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   LeicaDMR  controller adapter
// COPYRIGHT:     University of California, San Francisco, 2006
//                All rights reserved
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
// AUTHOR:        Nico Stuurman (nico@cmp.ucsf.edu) based on code by Nenad Amodaj, April 2007
//

#ifndef _LeicaDMR_H_
#define _LeicaDMR_H_

#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//

#define ERR_UNKNOWN_COMMAND          10002
#define ERR_UNKNOWN_POSITION         10003
#define ERR_HALT_COMMAND             10004
#define ERR_CANNOT_CHANGE_PROPERTY   10005


#define ERR_PORT_NOT_SET            11001
#define ERR_NOT_CONNECTED           11002
#define ERR_COMMAND_CANNOT_EXECUTE  11003
#define ERR_NO_ANSWER               11004
#define ERR_DEVICE_NOT_FOUND       11005
#define ERR_UNEXPECTED_ANSWER       11006
#define ERR_INDEX_OUT_OF_BOUNDS     11007
#define ERR_INVALID_REFLECTOR_TURRET 11008
#define ERR_INVALID_POSITION        11009
#define ERR_OBJECTIVE_SET_FAILED    11010


class Hub : public CGenericBase<Hub>
{
public:
   Hub();
   ~Hub();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMicroscope(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string version_;
   std::string micType;
   // MMCore name of serial port
   std::string port_;
};

class RLShutter : public CShutterBase<RLShutter>
{
public:
   RLShutter();
   ~RLShutter();

   int Initialize();
   int Shutdown();

   void GetName (char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen (bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   bool open_;
   std::string name_;  
   MM::MMTime changedTime_;
};

class Lamp : public CShutterBase<Lamp>
{
public:
   Lamp();
   ~Lamp();

   int Initialize();
   int Shutdown();

   void GetName (char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen (bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   int intensity_;
   bool open_;
   std::string name_;  
   MM::MMTime changedTime_;
};

class RLModule : public CStateDeviceBase<RLModule>
{
public:
   RLModule();
   ~RLModule();
 
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

private:                                                                     
   bool open_;
   bool initialized_;                                                        
   std::string name_;  
   long  pos_;
   int numPos_;
};

class ZStage : public CStageBase<ZStage>
{
public:
   ZStage();
   ~ZStage();

   bool Busy();
   void GetName(char* pszName) const;
   int Initialize();
   int Shutdown();

   // Stage API   
   int SetPositionUm(double pos);
   int SetRelativePositionUm(double pos);
   int GetPositionUm(double& pos);
   double GetStepSize() {return stepSize_um_;};
   int SetPositionSteps(long steps);
   int SetRelativePositionSteps(long steps);
   int GetPositionSteps(long& steps);
   int SetOrigin();
   int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }
   int Move(double speed);

   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnThreshold(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStop(MM::PropertyBase* pProp, MM::ActionType eAct);

private:                                                                     
   double stepSize_um_;
   std::string name_;  
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
   long upperThreshold_;
   MM::MMTime changedTime_;
}; 

class ObjNosepiece : public CStateDeviceBase<ObjNosepiece>
{
public:
   ObjNosepiece();
   ~ObjNosepiece();
 
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
   int OnImmersionMode(MM::PropertyBase* pProp, MM::ActionType eAct);                
   int OnRotationMode(MM::PropertyBase* pProp, MM::ActionType eAct);                

private:                                                                     
   bool open_;
   bool initialized_;                                                        
   std::string name_;  
   long  pos_;
   int numPos_;
};

class ApertureDiaphragm : public CGenericBase<ApertureDiaphragm>
{
public:
   ApertureDiaphragm();
   ~ApertureDiaphragm();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBreak(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
};

class FieldDiaphragm : public CGenericBase<FieldDiaphragm>
{
public:
   FieldDiaphragm();
   ~FieldDiaphragm();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBreak(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCondensor(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
};


#endif //_LeicaDMR_H_

///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIFW1000.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASIFW1000  controller adapter
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

#ifndef _ASIFW1000_H_
#define _ASIFW1000_H_

#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//

// from Nico's code
#define ERR_UNKNOWN_COMMAND          10002
#define ERR_UNKNOWN_POSITION         10003
#define ERR_HALT_COMMAND             10004
#define ERR_CANNOT_CHANGE_PROPERTY   10005
// eof from Nico's code

// from Prior
#define ERR_INVALID_STEP_SIZE        10006
#define ERR_INVALID_MODE             10008
#define ERR_UNRECOGNIZED_ANSWER      10009
#define ERR_UNSPECIFIED_ERROR        10010

#define ERR_OFFSET 10100

// From ASIFW1000HUb
#define ERR_NOT_CONNECTED           11002
#define ERR_COMMAND_CANNOT_EXECUTE  11003
#define ERR_NO_ANSWER               11004
#define ERR_SETTING_WHEEL           11005
#define ERR_SETTING_VERBOSE_LEVEL   11006
#define ERR_SHUTTER_NOT_FOUND       11007
#define ERR_UNEXPECTED_ANSWER       11008


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
   bool SupportsDeviceDetection(void);
   MM::DeviceDetectionStatus DetectDevice(void);

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   // MMCore name of serial port
   std::string port_;
};

class Shutter : public CShutterBase<Shutter>
{
public:
   Shutter();
   ~Shutter();

   int Initialize();
   int Shutdown();

   void GetName (char* pszName) const;
   bool Busy();
   unsigned long GetShutterNr() const {return shutterNr_;}

   // Shutter API
   int SetOpen (bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterNr(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string name_;
   std::string shutterType_;
   MM::MMTime changedTime_;
   unsigned shutterNr_;
};

class FilterWheel : public CStateDeviceBase<FilterWheel>
{
public:
   FilterWheel();
   ~FilterWheel();
 
   // MMDevice API                                                           
   // ------------                                                           
   int Initialize();                                                         
   int Shutdown();                                                           
                                                                             
   void GetName(char* pszName) const;                                        
   bool Busy();                                                              
   unsigned long GetNumberOfPositions()const {return numPos_;}               
   unsigned long GetWheelNr() const {return wheelNr_;}
                                                                             
   // action interface                                                       
   // ----------------                                                       
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);                
   int OnWheelNr(MM::PropertyBase* pProp, MM::ActionType eAct);                
                                                                             
private:                                                                     
   bool open_;
   bool initialized_;                                                        
   std::string name_;  
   long  pos_;
   int wheelNr_;
   int numPos_;
};


#endif //_ASIFW1000_H_

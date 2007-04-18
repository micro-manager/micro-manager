///////////////////////////////////////////////////////////////////////////////
// FILE:          ASI.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI ProScan controller adapter
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/01/2006
//
// CVS:           $Id: ASI.h,v 1.1 2007/02/27 23:33:14 nenad Exp $
//

#ifndef _ASIFW1000_H_
#define _ASIFW1000_H_

#include "../../MMDevice/MMDevice.h"
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

private:
   bool initialized_;
   // MMCore name of serial port
   std::string port_;
   // Command exchange with MMCore
   std::string command_;
   // Has a command been sent to which no answer has been received yet?
   bool pendingCommand_;
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
   int OnShutterNr(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   unsigned shutterNr_;
   std::string name_;
   bool state_;
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
   bool initialized_;                                                        
   int numPos_;
   int wheelNr_;
   long  pos_;
   std::string name_;  
};


#endif //_ASIFW1000_H_

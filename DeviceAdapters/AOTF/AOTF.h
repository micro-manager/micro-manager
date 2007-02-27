///////////////////////////////////////////////////////////////////////////////
// FILE:          AOTF.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   AOTF parallel port adapter
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 03/15/2006
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
// CVS:           $Id$
//

#ifndef _AOTF_H_
#define _AOTF_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
//#define ERR_UNKNOWN_LABEL 100
#define ERR_UNKNOWN_POSITION 101
#define ERR_INITIALIZE_FAILED 102
#define ERR_WRITE_FAILED 103
#define ERR_CLOSE_FAILED 104

class CAOTFShutter : public CShutterBase<CAOTFShutter>  
{
public:
   CAOTFShutter();
   ~CAOTFShutter();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   
   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   // ----------------
   int OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int WriteToPort(long lnValue);
   long openTimeUs_;
   std::string name_;

   bool initialized_;
};

class CAOTFSwitch : public CStateDeviceBase<CAOTFSwitch>  
{
public:
   CAOTFSwitch();
   ~CAOTFSwitch();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}
   
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int OpenPort(const char* pszName, long lnValue);
   int WriteToPort(long lnValue);
   int ClosePort();

   bool initialized_;
   bool busy_;
   long numPos_;
};


#endif //_AOTF_H_

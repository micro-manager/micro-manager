///////////////////////////////////////////////////////////////////////////////
// FILE:          IntegratedFilterWheel.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs device adapters: BBD102 Controller
//
// COPYRIGHT:     Thorlabs Inc, 2011
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 2011
//                http://nenad.amodaj.com
//

#ifndef _INTEGRATEDFILTERWHEEL_H_
#define _INTEGRATEDFILTERWHEEL_H_

#include <MMDevice.h>
#include <DeviceBase.h>

//////////////////////////////////////////////////////////////////////////////
// IntegratedFilterWheel class
// (device adapter)
//////////////////////////////////////////////////////////////////////////////
class IntegratedFilterWheel : public CStateDeviceBase<IntegratedFilterWheel>
{
public:
   IntegratedFilterWheel();
   ~IntegratedFilterWheel();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions() const;

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetCommand(const unsigned char* command, unsigned length);
   int GetCommand(unsigned char* response, unsigned length, double timeoutMs);
   int DiscoverNumberOfPositions();
   int Home();
   int GoToPosition(long pos);
   int RetrieveCurrentPosition(long& pos);

   long numberOfPositions_;
   bool home_;
   bool initialized_;
   long position_;
   std::string port_;
   double answerTimeoutMs_;      // max wait for the device to answer

   static const unsigned int stepsTurn_ = 1774933; // steps in one wheel turn
};

#endif //_INTEGRATEDFILTERWHEEL_H_

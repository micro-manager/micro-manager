/////////////////////////////////////////////////////////////////////////////
// FILE:          nPC400Ctrl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   nPoint C400 Controller
//
// COPYRIGHT:     nPoint,
//                Mission Bay Imaging, San Francisco, 2011
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER(S) OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Lon Chu (lonchu@yahoo.com) created on August 2011
//

#pragma once

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
	
//
// MAP285 is a micromanipulator controller from Sutter Instrument Comapny.
// It accept remote serial input to conrol micromanipulator.
//
class nPC400Ctrl : public CGenericBase<nPC400Ctrl>
{
   public:

	  // contructor & destructor
	  // .......................
      nPC400Ctrl();
      ~nPC400Ctrl();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();

      void GetName(char* pszName) const;

	  // MP285 doesn't support equivalent command 
	  // return false for now
	  bool Busy() { return false; }


	  int DeInitialize() { m_yInitialized = false; return DEVICE_OK; };
      bool Initialized() { return m_yInitialized; };
      int SetOrigin() { return DEVICE_UNSUPPORTED_COMMAND; }
      int Stop() { return DEVICE_UNSUPPORTED_COMMAND; }
      int Home() { return DEVICE_UNSUPPORTED_COMMAND; }
      int GetChannelsConnected(unsigned char* sResp);
      int GetNumberOfAxes(int nChannelsConnected);

      // action interface
      // ---------------
      int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnDebugLogFlag(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnSpeed(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/) { return DEVICE_UNSUPPORTED_COMMAND; }
      int OnMotionMode(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/) { return DEVICE_UNSUPPORTED_COMMAND; }

   private:
      //void tohex(const unsigned char bByte, char* sHex);
      int WriteCommand(const unsigned char* sCommand);
      int ReadMessage(unsigned char* sMessage);

	  // montoring controller status
      // ---------------------------

	  // int checkError(std::string sWhat, unsigned char* sResp);
      // int checkStatus(unsigned char* sResponse, unsigned int nLength);

      std::string   m_sCommand;             // Command exchange with MMCore
      std::string   m_sPort;                // serial port id
      bool          m_yInitialized;         // controller initialized flag
      double        m_dAnswerTimeoutMs;     // maximum waiting time for receiving reolied message
};

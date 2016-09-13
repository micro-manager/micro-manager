///////////////////////////////////////////////////////////////////////////////
// FILE:          XLedCtrl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Definition of X-Cite Led Controller Class
//
// COPYRIGHT:     Lumen Dynamics
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
// AUTHOR:        Lon Chu (lonchu@yahoo.com) created on July 2011
//

#pragma once

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
//#include "../../MMDevice/ModuleInterface.h"
	
//
// XLED1 is a controller from Lumen Dynamics.
// It accept remote serial input to conrol micromanipulator.
//

class XLedCtrl : public CGenericBase<XLedCtrl>
{
   public:

	  // contructor & destructor
	  // .......................
      XLedCtrl();
      ~XLedCtrl();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();  // shutdown the controller

      void GetName(char* pszName) const;
      bool Busy() { return false; }


	  //int DeInitialize() { m_yInitialized = false; return DEVICE_OK; };
      //bool Initialized() { return m_yInitialized; };
      int ReadAllProperty();
      char* GetXLedStatus(unsigned char* sResp, char* sXLedStatus);
      int GetStatusDescription(long lStatus, char* sStatus);
      // int GetSerialNumber(unsigned char* sResp);


      // action interface
      // ---------------
      int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnDebugLogFlag(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnAllOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnPWMStatus(MM::PropertyBase* pProp, MM::ActionType pAct);
      int OnPWMMode(MM::PropertyBase* pProp, MM::ActionType pAct);
      int OnFrontPanelLock(MM::PropertyBase* pProp, MM::ActionType pAct);
      int OnLCDScrnNumber(MM::PropertyBase* pProp, MM::ActionType pAct);
      int OnLCDScrnBrite(MM::PropertyBase* pProp, MM::ActionType pAct);
      int OnLCDScrnSaver(MM::PropertyBase* pProp, MM::ActionType pAct);
      int OnClearAlarm(MM::PropertyBase* pProp, MM::ActionType pAct);
      int OnSpeakerVolume(MM::PropertyBase* pProp, MM::ActionType pAct);
      int ConnectXLed(unsigned char* sResp);


   private:
      int XLedSerialIO(unsigned char* sCmd, unsigned char* sResp);  // write comand to serial port and read message from serial port
      int WriteCommand(const unsigned char* sCommand);              // write command to serial port
      int ReadMessage(unsigned char* sMessage);                     // read message from serial port

      double        m_dAnswerTimeoutMs;     // maximum waiting time for receiving reolied message
      bool          m_yInitialized;         // controller initialized flag
      long          m_lAllOnOff;            // all on/off flag
      long          m_lPWMState;            // PWM status
      long          m_lPWMMode;             // PWM mode
      long          m_lScrnLock;            // front panel lock
      long          m_lScrnNumber;          // screen number
      long          m_lScrnBrite;           // screen brightness
      long          m_lScrnTimeout;         // screen saver time out
      long          m_lSpeakerVol;          // speaker volume
};

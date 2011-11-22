///////////////////////////////////////////////////////////////////////////////
// FILE:          VariLC.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   VariLC Polarization Adapter
//
//
// AUTHOR:        Rudolf Oldenbourg, MBL, w/ Arthur Edelstein and Karl Hoover, UCSF, Sept, Oct 2010
// COPYRIGHT:     
// LICENSE:       
//

#ifndef _VARILC_H_
#define _VARILC_H_


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////


// MMCore name of serial port
std::string port_;

int ClearPort(MM::Device& device, MM::Core& core, const char* port);

class VariLC : public CGenericBase<VariLC>
{
   public:
      VariLC();
      ~VariLC();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();

      void GetName(char* pszName) const;
      bool Busy();

//      int Initialize(MM::Device& device, MM::Core& core);
      int DeInitialize() {initialized_ = false; return DEVICE_OK;};
      bool Initialized() {return initialized_;};

      // action interface
      // ---------------
      int OnPort    (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnBriefMode (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnWavelength (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnNumTotalLCs (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnNumActiveLCs (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnRetardance (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
//      int OnEpilogueL (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnNumPalEls (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnPalEl (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
      int OnSendToVariLC (MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnGetFromVariLC (MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      // Command exchange with MMCore
      std::string command_;
      bool initialized_;
      double answerTimeoutMs_;
	  bool briefModeQ_;
      double wavelength_; // the cached value
      long numTotalLCs_;  // total number of LCs
      long numActiveLCs_;  // number of actively controlled LCs (the actively controlled LCs appear first in the list of retardance values in the L-command)
      double retardance_[8]; // retardance values of total number of LCs; I made the index 8, a high number unlikely to be exceeded by the variLC hardware
      std::string epilogueL_; // added at the end of every L command to account for uncontrolled LCs
      long numPalEls_;  // total number of palette elements
	  std::string palEl_[5]; // array of palette elements, index is total number of elements
//      std::string currRet_;
      std::string sendToVariLC_;
      std::string getFromVariLC_;

      std::vector<double> getNumbersFromMessage(std::string variLCmessage, bool prefixQ);
	  MM::MMTime changedTime_;

};




#endif //_VARILC_H_

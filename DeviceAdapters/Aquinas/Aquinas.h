///////////////////////////////////////////////////////////////////////////////
// FILE:          Aquinas.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Interfaces with Aquinas microfluidics controller
// COPYRIGHT:     UCSF, 2011
// LICENSE:       LGPL
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu


#ifndef _AQUINAS_H_
#define _AQUINAS_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes                                                
//

#define ERR_PORT_CHANGE_FORBIDDEN    101

class AqController: public CGenericBase<AqController>
{
public: 
   AqController();
   ~AqController();

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();

    void GetName(char* pszName) const;
    bool Busy();

   // action interface
   // ---------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnID(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetPressure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnValveState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnValveOnOff(MM::PropertyBase* pProp, MM::ActionType eAct, long valveNr);

private:
   int SetValveState();

   std::string port_;
   bool initialized_;
   double pressureSetPoint_;
   unsigned char valveState_;
   std::string id_;
};

#endif // _AQUINAS_H_

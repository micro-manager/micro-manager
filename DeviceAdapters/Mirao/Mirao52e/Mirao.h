///////////////////////////////////////////////////////////////////////////////
// FILE:       Mirao.h
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Deformable mirror device adapter
// Replace all occurrences of 'Skeleton' and 'SKELETON' with the name of your device
//   

#ifndef _DEFMIRROR_H_
#define _DEFMIRROR_H_

#include "../MMDevice/MMDevice.h"
#include "../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes are defined in <mirao52e.h>
//

class DefMirror : public CGenericBase<DefMirror>
{
   public:
	   DefMirror();
      ~DefMirror();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();
      void GetName(char* pszName) const;
      bool Busy();
      
      // action interface                                                       
      // ----------------                                                       
      int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      bool initialized_;
      double answerTimeoutMs_;
	  std::string version_;
};

#endif // 

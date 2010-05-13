#ifndef _PVCAM_ACTION_H_
#define _PVCAM_ACTION_H_

#include "../../MMDevice/DeviceBase.h"

template <class T>
class PVCAMAction : public MM::ActionFunctor
{
	typedef int (T::*PVCAMActionFP)(MM::PropertyBase*, MM::ActionType, uns32);

private:
	uns32 param_id_;
	PVCAMActionFP fpt_; 
	T* pObj_;

public:
	PVCAMAction(T* pObj, PVCAMActionFP fpt, uns32 data): param_id_(data), fpt_(fpt), pObj_(pObj) {}; 

	int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      {return (*pObj_.*fpt_)(pProp, eAct, param_id_);};
};

#endif

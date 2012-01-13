#include "PropertyCallback.h"

PropertyCallback::PropertyCallback(string label, DEProtoProxy* proxy) :
	_label(label), _proxy(proxy)
{
}

int PropertyCallback::OnProperty(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return DEVICE_OK;
}



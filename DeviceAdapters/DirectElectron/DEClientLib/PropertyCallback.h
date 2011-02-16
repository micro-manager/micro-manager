#pragma once
#include "../../MMDevice/DeviceBase.h"
#include "DEProtoProxy.h"

namespace DirectElectronPlugin
{
	class PropertyCallback
	{
		public:
			PropertyCallback(string label, DEProtoProxy* proxy);
			int OnProperty(MM::PropertyBase* pProp, MM::ActionType eAct);
		private:
			string _label;
			DEProtoProxy* _proxy;
	};
}

#ifndef _READMODECONTOL_H_
#define _READMODECONTROL_H_

#include "../../MMDevice/Property.h"

class AndorCamera;

class ReadModeControl
{
public:
	ReadModeControl(AndorCamera * cam);
	
	int OnReadMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int getCurrentMode();

private:
	typedef MM::Action<ReadModeControl> CPropertyAction;
	enum MODE { FVB=0, IMAGE=4 };
    MODE currentMode_;

	typedef std::map<MODE, const char*> MODEMAP;
	MODEMAP modes_;
    MODEMAP modeDescriptions_;

	void CreateProperty();
	void InitialiseMaps();
	MODE GetMode(const char* mode);

	AndorCamera * camera_;
};

#endif
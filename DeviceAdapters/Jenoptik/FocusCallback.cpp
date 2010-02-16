#include "mmgrProgRes.h"

void __stdcall FocusCallback(int focus,unsigned long UserValue)
{
	CProgRes* pctrl=reinterpret_cast<CProgRes*>(UserValue);
	pctrl->m_FocusValue = focus;
}

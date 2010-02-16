#include "mmgrProgRes.h"

void __stdcall ExpCtrl(unsigned long ticks, unsigned long UserValue)
{
	CProgRes* pctrl=reinterpret_cast<CProgRes*>(UserValue);
	pctrl->SetExposure((double)mexTicksToMicroSeconds (pctrl->m_GUID
		                                              , ticks
													  , pctrl->m_AcqParams.mode, pctrl->m_AcqParams.ccdtransferCode));
}

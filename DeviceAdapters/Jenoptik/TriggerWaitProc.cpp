#include "sdk/mexexl.h"
#include <atltime.h>
#include "mmgrProgRes.h"
extern 	unsigned __int64 g_TriggeredGUID;
extern mexAcquisParams* g_pAcqProperties;
extern bool* g_pSnapFinished;
bool g_TriggerAbort = false;

DWORD WINAPI grab(void* param)
{
	HWND			hdlg = (HWND) param;
	long status;

	status = mexGrab (g_TriggeredGUID, g_pAcqProperties, NULL);
	if (status != NOERR)
	{
		return ERR_ACQ_PARAMS_ERR;
	}

	while (!*g_pSnapFinished && !g_TriggerAbort) Sleep (0);
	
	SendMessage(hdlg, WM_COMMAND, MAKEWPARAM(IDOK, 0), status);
	return status;
}

BOOL CALLBACK TriggerWaitProc(HWND hwndDlg, 
                             UINT message, 
                             WPARAM wParam, 
                             LPARAM lParam) 
{ 
    switch (message) 
    { 
        case WM_INITDIALOG:
			g_TriggerAbort = false;
			CreateThread(NULL, 0, grab, hwndDlg, 0, NULL);
            break;
        case WM_COMMAND: 
            switch (LOWORD(wParam)) 
            { 
                case IDOK:
					EndDialog(hwndDlg, lParam);
					break;
                case IDCANCEL:
					mexAbortAcquisition(g_TriggeredGUID);
					g_TriggerAbort = true;
					EndDialog(hwndDlg, TRUE);
					return TRUE;
                    break;
            } 
    } 
    return FALSE; 
}
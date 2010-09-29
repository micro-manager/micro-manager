//--------------------------------------------------------------------------- 
#ifndef DynDialogsH 
#define DynDialogsH 
//--------------------------------------------------------------------------- 
#include <windows.h> 
//--------------------------------------------------------------------------- 

// Input-Box-Childs 
#define  ID_INPUT     200 
#define  ID_INFOTEXT  201 

// Control Classes 
#define  BUTTON_CLASS       0x0080 
#define  EDIT_CLASS         0x0081 
#define  STATIC_CLASS       0x0082 
#define  LISTBOX_CLASS      0x0083 
#define  SCROLLBAR_CLASS    0x0084 
#define  COMBOBOX_CLASS     0x0085 

// Definitions of icon representations in ShowMessage() 
#define  SM_NONE       0 
#define  SM_WARNING    1 
#define  SM_INFO       2 
#define  SM_ERROR      3 
//--------------------------------------------------------------------------- 

LPWORD          DWORD_ALIGN(LPWORD); 
LPWORD          NON_DWORD_ALIGN(LPWORD); 
LPWORD          InitDialog(LPVOID, LPCTSTR, DWORD, WORD, LPCTSTR, WORD, short, short, short, short); 
LPWORD          CreateDlgControl(LPWORD, WORD, WORD, LPCTSTR, DWORD, short, short, short, short); 
int             InputBox(HWND, LPCTSTR, LPCTSTR, LPTSTR, INT); 
VOID            ShowMessage(HWND, LPCTSTR, LPCTSTR, WORD); 
BOOL  CALLBACK  InputBoxDlgProc(HWND hwnd, UINT uiMsg, WPARAM wParam, LPARAM lParam); 
//--------------------------------------------------------------------------- 
#endif 

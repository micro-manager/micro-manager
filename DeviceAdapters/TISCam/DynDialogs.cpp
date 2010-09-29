//--------------------------------------------------------------------------- 
#pragma comment (lib, "gdi32.lib")

#include "DynDialogs.h" 
//--------------------------------------------------------------------------- 

LPWORD DWORD_ALIGN(LPWORD lpIn) 
{ 
   ULONG ul = (ULONG)lpIn; 

   ul += 3; 
   ul >>= 2; 
   ul <<= 2; 

   return (LPWORD)ul; 
} 
//--------------------------------------------------------------------------- 

LPWORD NON_DWORD_ALIGN(LPWORD lpIn) 
{ 
   return (DWORD_ALIGN(lpIn - 1) + 1); 
} 
//--------------------------------------------------------------------------- 

LPWORD InitDialog(LPVOID lpv, LPCTSTR title, DWORD style, WORD ctrlno, LPCTSTR fontname, 
                  WORD fontsize, short x, short y, short cx, short cy) 
{ 
    LPWORD        lpw; 
    LPWSTR        lpwsz; 
    int           nchar, wcharlength; 
    LPDLGTEMPLATE lpdt = (LPDLGTEMPLATE)lpv; 

    lpdt->style = style; 
    lpdt->dwExtendedStyle = 0; 
    lpdt->cdit = ctrlno; 
    lpdt->x  = x;  lpdt->y  = y; 
    lpdt->cx = cx; lpdt->cy = cy; 

    lpw = (LPWORD)(lpdt + 1); 

    // No menu 
    *lpw = 0; 
    lpw++; 

    // Predefined dialog box class (by default) 
    *lpw = 0; 
    lpw++; 

    // The title 
    lpwsz = (LPWSTR)lpw; 
    wcharlength = MultiByteToWideChar(CP_ACP, 0, title, -1, lpwsz, 0); 
    nchar = MultiByteToWideChar(CP_ACP, 0, title, -1, lpwsz, wcharlength); 
    lpw += nchar; 

    // The font used in dialog 
    if(style & DS_SETFONT) 
    { 
       *lpw = 8; 
       lpw++; 
       lpwsz = (LPWSTR)lpw; 
       wcharlength = MultiByteToWideChar(CP_ACP, 0, fontname, -1, lpwsz, 0); 
       nchar = MultiByteToWideChar(CP_ACP, 0, fontname, -1, lpwsz, wcharlength); 
       lpw += nchar; 
    } 

    return lpw; 
} 
//--------------------------------------------------------------------------- 

LPWORD CreateDlgControl(LPWORD lpw, WORD ctrlclass, WORD id, LPCTSTR caption, 
                      DWORD style, short x, short y, short cx, short cy) 
{ 
    LPDLGITEMTEMPLATE lpdit; 
    LPWSTR            lpwsz; 
    int               nchar, wcharlength; 

    lpw = DWORD_ALIGN(lpw); 
    lpdit = (LPDLGITEMTEMPLATE)lpw; 
    lpdit->style = style; 
    lpdit->dwExtendedStyle = 0; 
    lpdit->x  = x ; lpdit->y  = y; 
    lpdit->cx = cx; lpdit->cy = cy; 
    lpdit->id = id; 

    // class 
    lpw = (LPWORD)(lpdit + 1); 
    *lpw = 0xFFFF; 
    lpw++; 
    *lpw = ctrlclass; 
    lpw++; 

    // title 
    lpwsz = (LPWSTR)lpw; 
    wcharlength = MultiByteToWideChar(CP_ACP, 0, caption, -1, lpwsz, 0); 
    nchar = MultiByteToWideChar(CP_ACP, 0, caption, -1, lpwsz, wcharlength); 
    lpw += nchar; 

    // no creation data 
    lpw = NON_DWORD_ALIGN(lpw); 
    *lpw = 0; 
    lpw++; 

    return lpw; 
} 
//--------------------------------------------------------------------------- 

int InputBox(HWND hwnd, LPCTSTR prompt, LPCTSTR title, LPTSTR buffer, INT buflength) 
{ 
    HGLOBAL  hgbl; 
    LPWORD   lpw; 
    LRESULT  ret; 
    DWORD    style; 

    hgbl = GlobalAlloc(GMEM_ZEROINIT, 1024); 
    if(!hgbl) return 0; 
    LPVOID lpv = GlobalLock(hgbl); 

    // Prepare the dialog box 
    style = DS_SETFONT | DS_CENTER | DS_3DLOOK | WS_POPUP | WS_SYSMENU | DS_MODALFRAME | WS_CAPTION | WS_VISIBLE; 
    lpw = InitDialog(lpv, title, style, 4, "MS Sans Serif", 8, 0, 0, 319, 47); 

    // OK-Button 
    style = WS_CHILD | WS_VISIBLE | WS_TABSTOP |BS_DEFPUSHBUTTON; 
    lpw = CreateDlgControl(lpw, BUTTON_CLASS, IDOK, "OK", style, 264, 7, 48, 15); 

    // Cancel-Button 
    style = WS_CHILD | WS_VISIBLE | WS_TABSTOP | BS_PUSHBUTTON; 
    lpw = CreateDlgControl(lpw, BUTTON_CLASS, IDCANCEL, "Abbrechen", style, 264, 26, 48, 15); 

    // Text to prompt 
    style = WS_CHILD | WS_VISIBLE | SS_LEFT; 
    lpw = CreateDlgControl(lpw, STATIC_CLASS, ID_INFOTEXT, prompt, style, 10, 9, 129, 16); 

    // Edit-Control 
    style = WS_CHILD | WS_VISIBLE | WS_BORDER | WS_TABSTOP; 
    lpw = CreateDlgControl(lpw, EDIT_CLASS, ID_INPUT, "", style, 10, 26, 249, 13); 

    GlobalUnlock(hgbl); 
    int data[2] = {(int)buffer, buflength}; 
    ret = DialogBoxIndirectParamA(NULL, (LPDLGTEMPLATE)hgbl, hwnd, 
                                 (DLGPROC)InputBoxDlgProc, (int)data); 
    GlobalFree(hgbl); 
    return (ret > 0) ? ret : 0; 
} 
//--------------------------------------------------------------------------- 

BOOL CALLBACK InputBoxDlgProc(HWND hwnd, UINT uiMsg, WPARAM wParam, LPARAM lParam) 
{ 
   static char* buf; 
   static int   buflength; 
   static HFONT hFont; 

   switch(uiMsg) 
   { 
      case WM_INITDIALOG: 
      { 
         int* data = (int*)lParam; 
         buf = (char*)data[0]; 
         buflength = data[1]; 
         HWND hEdit = GetDlgItem(hwnd, ID_INPUT); 
         SetFocus(hEdit); 
         break; 
      } 

      case WM_SETFONT: 
         hFont = (HFONT)wParam; 
         break; 

      case WM_CLOSE: 
         EndDialog(hwnd, IDCANCEL); 
         DeleteObject(hFont); 
         break; 

      case WM_COMMAND: 
      { 
         switch(LOWORD(wParam)) 
         { 
            case IDOK: 
            { 
               HWND hEdit = GetDlgItem(hwnd, ID_INPUT); 
               GetWindowText(hEdit, buf, buflength); 
               EndDialog(hwnd, IDOK); 
               DeleteObject(hFont); 
               break; 
            } 

            case IDCANCEL: 
            { 
               EndDialog(hwnd, IDCANCEL); 
               break; 
            } 
         } 
      } 
   } 
   return FALSE; 
} 
//--------------------------------------------------------------------------- 

VOID ShowMessage(HWND hwnd, LPCTSTR lpszMsg, LPCTSTR lpszTitle, WORD wIcon) 
{ 
   UINT uType = 0; 
   if(wIcon == SM_WARNING) 
      uType = MB_ICONWARNING; 
   else if(wIcon == SM_INFO) 
      uType = MB_ICONINFORMATION; 
   else if(wIcon == SM_ERROR) 
      uType = MB_ICONERROR; 

   MessageBox(hwnd, lpszMsg, lpszTitle, uType); 
} 
//---------------------------------------------------------------------------
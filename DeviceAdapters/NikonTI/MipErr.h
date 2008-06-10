/////////////////////////////////////////////////////////////////////////////
// MipErr.h contains helper methods to catch and display COM errors
//
// NOTE: This file MUST be included before any #import statements!
//
// When included as above, the get/put functions will throw CMipErr objects
// Catch these exceptions as follows:
//      try
//		{
//		...
//		}
//		catch (CMipErr err)
//		{
//			err.MessageBox(NULL,__FILE__,__LINE__);
//		}

#ifndef _MIP_ERR_H
#define _MIP_ERR_H

#include <atlbase.h>
#include <comdef.h>

// returns an interface from an IID
inline HRESULT InterfaceNameFromIID(REFIID iid, BSTR * pbstr)
{
	*pbstr = NULL;
	CComBSTR bstrTmp(iid);
	CComBSTR bstrRegPath(L"\\Interface\\");
	bstrRegPath += bstrTmp;
	HKEY hKey;
	LONG lResult = RegOpenKeyExW(HKEY_CLASSES_ROOT,bstrRegPath,0,KEY_READ,&hKey);
	if(lResult == ERROR_SUCCESS)
	{
		DWORD dwType;
		DWORD dwCount;
		lResult = RegQueryValueExW(hKey, NULL, NULL, &dwType,NULL, &dwCount);
		if(lResult == ERROR_SUCCESS)
		{
			ATLASSERT(dwType == REG_SZ);
			*pbstr = SysAllocStringLen(NULL,dwCount/2-1);
			lResult = RegQueryValueExW(hKey, NULL, NULL, &dwType,(LPBYTE)(BSTR)*pbstr, &dwCount);
		}
		RegCloseKey(hKey);
	}
	return lResult == ERROR_SUCCESS ? S_OK: REGDB_E_CLASSNOTREG;
}

// returns an interface from an IID
inline HRESULT InterfaceNameFromIID(REFIID iid, _bstr_t * pbstr)
{
	BSTR tmp;
	HRESULT hr = InterfaceNameFromIID(iid,&tmp);
	if(SUCCEEDED(hr))
	{
		pbstr->Assign(tmp);
		SysFreeString(tmp);
	}
	return hr;
}

// thrown by _mip_issue_error
class CMipErr: public _com_error
{
public:
	CMipErr(LPCTSTR szFile, int iLine, _GUID guid, HRESULT hr, IErrorInfo * pInfo = NULL)
	: _com_error(hr,pInfo,true)
	, m_bstrFile(szFile)
	, m_iLine(iLine)
	, m_guid(guid)
	{
	}

    __declspec(property(get=GetFile)) _bstr_t File;
	_bstr_t m_bstrFile;
	inline _bstr_t GetFile(void) { return _bstr_t(m_bstrFile,true); }

    __declspec(property(get=GetLine)) int Line;
	int m_iLine;
	inline int GetLine(void) { return m_iLine; }

    __declspec(property(get=GetGUID)) _GUID GUID;
	_GUID m_guid;
	inline _GUID GetGUID(void) { _GUID tmp= _com_error::GUID(); if(tmp == GUID_NULL) tmp = m_guid; return tmp; }

	int MessageBox(HWND hwndParent, LPCTSTR szFile, int iLine)
	{
		USES_CONVERSION;
		_bstr_t bstrSource = Source();
		if(!bstrSource)
		{
			HRESULT hr = InterfaceNameFromIID(m_guid,&bstrSource);
		}
		TCHAR tmp[8*256];
		wsprintf(tmp,
			"caught in:\t%s (%d)\n"
			"from:\t\t%s (%d):\n"
			"hresult:\t\t0x%08x: %s\n"
			"source:\t\t%s\n"
			"description:\t%s\n"
			,szFile,iLine
			,(TCHAR*)File,Line
			,Error(),ErrorMessage()
			,(TCHAR*)bstrSource
			,(TCHAR*)Description()
			);
#ifdef __AFX_H__
		return AfxMessageBox(tmp);
#else
		return ::MessageBox(hwndParent,tmp,"MIP Exception",MB_OK);
#endif
	}
};

// replace compiler default _com_issue_errorex by _mip_issue_error
#define _com_issue_errorex(hr,pObj,uuid) _mip_issue_error(__FILE__,__LINE__,hr,pObj,uuid);

#pragma warning( disable: 4290 )  /* C++ Exception Specification ignored */

// throw the CMipErr exception
inline void __stdcall _mip_issue_error(LPCTSTR szFile, int iLine, HRESULT hr, IUnknown* piu, REFIID riid) throw(_com_error)
{
	CComPtr<IErrorInfo> pieiObj;
	GetErrorInfo(0,&pieiObj);
	throw CMipErr(szFile,iLine,riid,hr,pieiObj);
}

inline HRESULT MipReportComErrorDlg(HRESULT r, LPCTSTR szTitle = "COM error")
{
	if(r<0)
	{
		LPTSTR pBuf = NULL;
		::FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER|FORMAT_MESSAGE_FROM_SYSTEM,NULL,r,0,(LPTSTR)(&pBuf),0,NULL);
		MessageBox(NULL,pBuf,szTitle,MB_OK);
		LocalFree(pBuf);
	}
	return r;
}


#endif
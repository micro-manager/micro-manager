/////////////////////////////////////////////////////////////////////////////
// The CParamEventSinkImpl class illustrates how events from MipParameter 
// properties can be handled.  There are three types of events:
//
//   OnValueChanged : fired when the value of a property has changed
//   OnInfoChanged  : fired when information (lower limit, higher limit,
//                    increment etc) about the property has changed
//   Disconnect     : fired when the sink must unsubscribe from the event source
//
// There is a possibility for a deadlock situation when the MTA thread fires an
// event to the STA. To overcome this problem, the CParamAsyncEventSinkImpl class
// can be used. This class implements an asynchronous notification using the 
// following strategy:
//
// - the sink object lives in the MTA and sinks the event without a thread switch
// - when the sink object receives an event, it posts a message to an STA window 
//   and the MTA thread is allowed to proceed
// - subsequent events will not post a message anymore
// - the posted message will be dispatched by the STA thread
// - the dispatch implementation re-arms the sink object to post a new message
//   when the next event occurs
//
// The CParamAsyncEventSinkImpl class is optimal when updating UI widgets that
// show the last value rather than all of the intermediate values.
//
// The differences between the CParamEventSinkImpl and CParamAsyncEventSinkImpl
// implementations include:
//
//                      CParamEventSinkImpl        CParamAsyncEventSinkImpl
//
// MTA thread will be			yes                       no
// blocked during dispatch?
// every change event			yes                       only the last one
// will be dispatched ?
//
// 

#ifndef PARAMEVENTIMPL_H
#define PARAMEVENTIMPL_H

//#include <Afxwin.h>
#include "AtlBase.h" // for AtlAdvise
#include "MipErr.h"

class CParamEventSinkImpl: public MIPPARAMLib::IMipParameterEvents
{
public:
	CParamEventSinkImpl(VARIANT & varParam = _variant_t(0l))
	: m_dwParamEventCookie(0)
	{

      //CC_STDCALL, VT_UI4, 1, VT_BSTR}
		if((varParam.vt == VT_UNKNOWN) || (varParam.vt == VT_DISPATCH) || (varParam.vt == VT_UI4) || 
         (varParam.vt == VT_BSTR) || (varParam.vt == CC_STDCALL) )
		{
			MIPPARAMLib::IMipParameterPtr pParam = varParam.punkVal;
			assert(pParam != NULL);
			Advise(pParam);
		}
	}

	~CParamEventSinkImpl(void)
	{
		Unadvise();
	}

	// connect to the following parameter
	MIPPARAMLib::IMipParameterPtr m_pParam;

	// the source-sink connection is identified by a cookie
	DWORD m_dwParamEventCookie;

	// call this method to setup a connection
	HRESULT Advise(MIPPARAMLib::IMipParameter * pParam)
	{
		if(!pParam)
		{
			assert(0);
			return E_POINTER;
		}

		Unadvise();

		// setup the source-sink connection
		HRESULT hr = AtlAdvise(pParam,this,__uuidof(MIPPARAMLib::IMipParameterEvents),&m_dwParamEventCookie);
		if(FAILED(hr))
		{
			assert(0);
			return hr;
		}

		// remember the parameter to which we are connected
		m_pParam = pParam;

		// update the information on the screen
		OnInfoChanged();
		OnValueChanged();
      //OnPositionChanged();

		return S_OK;
	}

	// overload for _variant_t
	HRESULT Advise(_variant_t & pParam)
	{
		return Advise(MIPPARAMLib::IMipParameterPtr(pParam));
	}

	HRESULT Unadvise(void)
	{
		// unsubscribe from the events
		HRESULT hr = S_OK;
		if(m_dwParamEventCookie)
		{
			hr = AtlUnadvise(m_pParam,__uuidof(MIPPARAMLib::IMipParameterEvents),m_dwParamEventCookie);
			m_dwParamEventCookie = 0;
		}
		if(m_pParam != NULL)
		{
			m_pParam.Release();
		}
		return hr;
	}

	// IUnknown Methods

	virtual HRESULT __stdcall QueryInterface(REFIID riid,void **ppvObject) {
		if(riid == IID_IUnknown) { *ppvObject = static_cast<IUnknown*>(this); return S_OK; }
		if(riid == IID_IDispatch) { *ppvObject = static_cast<IDispatch*>(this); return S_OK; }
		if(riid == __uuidof(MIPPARAMLib::IMipParameterEvents)) { *ppvObject = static_cast<MIPPARAMLib::IMipParameterEvents*>(this); return S_OK; }
		return E_NOINTERFACE; }
	virtual ULONG STDMETHODCALLTYPE AddRef( void) {return 1;}
	virtual ULONG STDMETHODCALLTYPE Release( void){return 1;}

    // IDispatch Methods:

    virtual HRESULT __stdcall GetTypeInfoCount( 
        /* [out] */ UINT __RPC_FAR *pctinfo) { return E_NOTIMPL; }
    
    virtual HRESULT __stdcall GetTypeInfo( 
        /* [in] */ UINT iTInfo,
        /* [in] */ LCID lcid,
        /* [out] */ ITypeInfo __RPC_FAR *__RPC_FAR *ppTInfo) { return E_NOTIMPL; }
    
    virtual HRESULT __stdcall GetIDsOfNames( 
        /* [in] */ REFIID riid,
        /* [size_is][in] */ LPOLESTR __RPC_FAR *rgszNames,
        /* [in] */ UINT cNames,
        /* [in] */ LCID lcid,
        /* [size_is][out] */ DISPID __RPC_FAR *rgDispId) { return E_NOTIMPL; }
    
    virtual /* [local] */ HRESULT __stdcall Invoke( 
        /* [in] */ DISPID dispIdMember,
        /* [in] */ REFIID riid,
        /* [in] */ LCID lcid,
        /* [in] */ WORD wFlags,
        /* [out][in] */ DISPPARAMS __RPC_FAR *pDispParams,
        /* [out] */ VARIANT __RPC_FAR *pVarResult,
        /* [out] */ EXCEPINFO __RPC_FAR *pExcepInfo,
        /* [out] */ UINT __RPC_FAR *puArgErr) {
			switch(dispIdMember) {
				case MIPPARAMLib::EMIPPAR_VALUECHANGED: return OnValueChanged();
				case MIPPARAMLib::EMIPPAR_INFOCHANGED: return OnInfoChanged();
				case MIPPARAMLib::EMIPPAR_DISCONNECT: return Disconnect(); }
			return E_INVALIDARG; }

    // IMipParameterEvents Methods:

	virtual HRESULT __stdcall OnValueChanged(void) { return S_OK; }
	virtual HRESULT __stdcall OnInfoChanged(void) { return S_OK; }
	virtual HRESULT __stdcall Disconnect(void) { return S_OK; }
};


#endif PARAMEVENTIMPL_H
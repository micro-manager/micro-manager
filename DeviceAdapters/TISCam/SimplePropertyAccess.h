
#ifndef SIMPLEPROPERTYACCESS_H_INC_
#define SIMPLEPROPERTYACCESS_H_INC_

#include "..\..\..\3rdparty\TheImagingSource\classlib\include\tisudshl.h"

class CSimplePropertyAccess
{
public:
	CSimplePropertyAccess();
	CSimplePropertyAccess( _DSHOWLIB_NAMESPACE::tIVCDPropertyItemsPtr pItems );
	~CSimplePropertyAccess();

	void	init( _DSHOWLIB_NAMESPACE::tIVCDPropertyItemsPtr pItems );

	bool	isAvailable( const GUID& id );
	long	getValue( const GUID& id );
	void	setValue( const GUID& id, long val );

	long	getRangeMin( const GUID& id );
	long	getRangeMax( const GUID& id );
	long	getDefault( const GUID& id );

	bool	isAutoAvailable( const GUID& id );
	bool	getAuto( const GUID& id );
	void	setAuto( const GUID& id, bool b );

	bool	isSwitchAvailable( const GUID& id );
	bool	getSwitch( const GUID& id );
	void	setSwitch( const GUID& id, bool b );

	bool	isOnePushAvailable( const GUID& id );
	void	push( const GUID& id );

protected:
	_DSHOWLIB_NAMESPACE::tIVCDPropertyItemsPtr	m_pItemContainer;
};

#endif // SIMPLEPROPERTYACCESS_H_INC_
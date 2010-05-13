
#include "SimplePropertyAccess.h"

using namespace _DSHOWLIB_NAMESPACE;

CSimplePropertyAccess::CSimplePropertyAccess()
: m_pItemContainer( 0 )
{
}

CSimplePropertyAccess::CSimplePropertyAccess( _DSHOWLIB_NAMESPACE::tIVCDPropertyItemsPtr pItems )
: m_pItemContainer( pItems )
{
}

CSimplePropertyAccess::~CSimplePropertyAccess()
{
}

void	CSimplePropertyAccess::init( _DSHOWLIB_NAMESPACE::tIVCDPropertyItemsPtr pItems )
{
	m_pItemContainer = pItems;
}

tIVCDRangePropertyPtr	getRangeInterface( _DSHOWLIB_NAMESPACE::tIVCDPropertyItemsPtr& pItems, const GUID& id )
{
	GUID itemID = id;
	GUID elemID = VCDElement_Value;

	if( itemID == VCDElement_WhiteBalanceRed || itemID == VCDElement_WhiteBalanceBlue )
	{
		elemID = itemID;
		itemID = VCDID_WhiteBalance;
	}

	if( itemID == VCDElement_GPIOIn || itemID == VCDElement_GPIOOut )
	{
		elemID = itemID;
		itemID = VCDID_GPIO;
	}

	if( itemID == VCDElement_StrobeDelay || itemID == VCDElement_StrobeDuration )
	{
		elemID = itemID;
		itemID = VCDID_Strobe;
	}

	tIVCDPropertyElementPtr pFoundElement = pItems->findElement( itemID, elemID );
	if( pFoundElement != 0 )
	{
		tIVCDRangePropertyPtr pRange;
		if( pFoundElement->getInterfacePtr( pRange ) != 0 )
		{
			return pRange;
		}
	}
	return 0;
}

tIVCDSwitchPropertyPtr	getAutoInterface( _DSHOWLIB_NAMESPACE::tIVCDPropertyItemsPtr& pItems, const GUID& id )
{
	tIVCDPropertyElementPtr pFoundElement = pItems->findElement( id, VCDElement_Auto );
	if( pFoundElement != 0 )
	{
		tIVCDSwitchPropertyPtr pAuto;
		if( pFoundElement->getInterfacePtr( pAuto ) != 0 )
		{
			return pAuto;
		}
	}
	return 0;
}

tIVCDButtonPropertyPtr	getOnePushInterface( _DSHOWLIB_NAMESPACE::tIVCDPropertyItemsPtr& pItems, const GUID& id )
{
	GUID itemID = id;
	GUID elemID = VCDElement_OnePush;

	if( itemID == VCDElement_GPIORead || itemID == VCDElement_GPIOWrite )
	{
		elemID = itemID;
		itemID = VCDID_GPIO;		
	}

	tIVCDPropertyElementPtr pFoundElement = pItems->findElement( itemID, elemID );
	if( pFoundElement != 0 )
	{
		tIVCDButtonPropertyPtr pOnePush;
		if( pFoundElement->getInterfacePtr( pOnePush ) != 0 )
		{
			return pOnePush;
		}
	}
	return 0;
}

bool	CSimplePropertyAccess::isAvailable( const GUID& id )
{
	assert( m_pItemContainer != 0 );

	if( id == VCDElement_WhiteBalanceRed || id == VCDElement_WhiteBalanceBlue )
	{
		return m_pItemContainer->findElement( VCDID_WhiteBalance, id ) != 0;
	}
	else
	{
		return m_pItemContainer->findItem( id ) != 0;
	}
}

long	CSimplePropertyAccess::getValue( const GUID& id )
{
	assert( m_pItemContainer != 0 );

	long rval = 0;
	tIVCDRangePropertyPtr pRange = getRangeInterface( m_pItemContainer, id );
	if( pRange != 0 )
	{
		rval = pRange->getValue();
	}
	return rval;
}

void	CSimplePropertyAccess::setValue( const GUID& id, long val )
{
	assert( m_pItemContainer != 0 );

	tIVCDRangePropertyPtr pRange = getRangeInterface( m_pItemContainer, id );
	if( pRange != 0 )
	{
		pRange->setValue( val );
	}
}

long	CSimplePropertyAccess::getRangeMin( const GUID& id )
{
	assert( m_pItemContainer != 0 );

	long rval = 0;
	tIVCDRangePropertyPtr pRange = getRangeInterface( m_pItemContainer, id );
	if( pRange != 0 )
	{
		rval = pRange->getRangeMin();
	}
	return rval;
}

long	CSimplePropertyAccess::getRangeMax( const GUID& id )
{
	assert( m_pItemContainer != 0 );

	long rval = 0;
	tIVCDRangePropertyPtr pRange = getRangeInterface( m_pItemContainer, id );
	if( pRange != 0 )
	{
		rval = pRange->getRangeMax();
	}
	return rval;
}

long	CSimplePropertyAccess::getDefault( const GUID& id )
{
	assert( m_pItemContainer != 0 );

	long rval = 0;
	tIVCDRangePropertyPtr pRange = getRangeInterface( m_pItemContainer, id );
	if( pRange != 0 )
	{
		rval = pRange->getDefault();
	}
	return rval;
}

bool	CSimplePropertyAccess::isAutoAvailable( const GUID& id )
{
	assert( m_pItemContainer != 0 );

	return getAutoInterface( m_pItemContainer, id ) != 0;
}

bool	CSimplePropertyAccess::getAuto( const GUID& id )
{
	assert( m_pItemContainer != 0 );

	bool rval = false;
	tIVCDSwitchPropertyPtr pAuto = getAutoInterface( m_pItemContainer, id );
	if( pAuto != 0 )
	{
		rval = pAuto->getSwitch();
	}
	return rval;
}

void	CSimplePropertyAccess::setAuto( const GUID& id, bool b )
{
	assert( m_pItemContainer != 0 );

	tIVCDSwitchPropertyPtr pAuto = getAutoInterface( m_pItemContainer, id );
	if( pAuto != 0 )
	{
		pAuto->setSwitch( b );
	}
}

bool	CSimplePropertyAccess::isOnePushAvailable( const GUID& id )
{
	assert( m_pItemContainer != 0 );

	tIVCDButtonPropertyPtr pOnePush = getOnePushInterface( m_pItemContainer, id );
	return pOnePush != 0;
}

void	CSimplePropertyAccess::push( const GUID& id )
{
	assert( m_pItemContainer != 0 );

	tIVCDButtonPropertyPtr pOnePush = getOnePushInterface( m_pItemContainer, id );
	if( pOnePush != 0 )
	{
		pOnePush->push();
	}
}

bool	CSimplePropertyAccess::isSwitchAvailable( const GUID& id )
{
	return isAutoAvailable( id );
}

bool	CSimplePropertyAccess::getSwitch( const GUID& id )
{
	return getAuto( id );
}

void	CSimplePropertyAccess::setSwitch( const GUID& id, bool b )
{
	setAuto( id, b );
}

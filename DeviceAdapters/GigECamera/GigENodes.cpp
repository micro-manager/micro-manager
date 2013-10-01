///////////////////////////////////////////////////////////////////////////////
// FILE:          GigNodes.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   An adapter for Gigbit-Ethernet cameras using an
//				  SDK from JAI, Inc.  Users and developers will 
//				  need to download and install the JAI SDK and control tool.
//                
// AUTHOR:        David Marshburn, UNC-CH, marshbur@cs.unc.edu, Jan. 2011
//


#include "GigENodes.h"
#include <vector>
#include <map>
#include <boost/assign/list_of.hpp>
#include <boost/lexical_cast.hpp>

namespace {

const std::map< int, std::string > sfncNames 
	= boost::assign::map_list_of
							// mandatory nodes
							( (int) WIDTH, (std::string) "Width" )
							( HEIGHT, "Height" )
							( PIXEL_FORMAT, "PixelFormat" )
							( ACQUISITION_MODE, "AcquisitionMode" )

							// recommended/optional nodes
							( DEVICE_VENDOR_NAME, "DeviceVendorName" )
							( DEVICE_MODEL_NAME, "DeviceModelName" )
							( DEVICE_MANUFACTURER_INFO, "DeviceManufacturerInfo" )
							( DEVICE_VERSION, "DeviceVersion" )
							( DEVICE_FIRMWARE_VERSION, "DeviceFirmwareVersion" )
							( DEVICE_ID, "DeviceID" )

							( SENSOR_WIDTH, "SensorWidth" )
							( SENSOR_HEIGHT, "SensorHeight" )
							( WIDTH_MAX, "WidthMax" )
							( HEIGHT_MAX, "HeightMax" )
							( BINNING_HORIZONTAL, "BinningHorizontal" )
							( BINNING_VERTICAL, "BinningVertical" )

							( EXPOSURE_MODE, "ExposureMode" )
							( EXPOSURE_TIME, "ExposureTime" )
							( EXPOSURE_TIME_ABS, "ExposureTimeAbs" )
							( EXPOSURE_TIME_ABS_INT, "ExposureTimeAbs" )

							( GAIN, "Gain" )
							( GAIN_RAW, "GainRaw" )

							( TEMPERATURE, "DeviceTemperature" )

							( GEV_VERSION_MAJOR, "GevVersionMajor" )
							( GEV_VERSION_MINOR, "GevVersionMinor" )

							( ACQUISITION_FRAME_RATE, "AcquisitionFrameRate" )
							( ACQUISITION_FRAME_RATE_STR, "AcquisitionFrameRate" )
							;


class NodeFactory {
	CAM_HANDLE camera;
	boost::function<void(const std::string&)> logger;

public:
	NodeFactory( CAM_HANDLE camera, boost::function<void(const std::string&)> logger = 0 ) :
		camera( camera ),
		logger( logger )
	{}

	Node<int64_t> IntNode( InterestingNodeInteger node )
	{ return Node<int64_t>( sfncNames.find( node )->second, camera, logger ); }
	Node<double> FloatNode( InterestingNodeFloat node )
	{ return Node<double>( sfncNames.find( node )->second, camera, logger ); }
	Node<std::string> StringNode( InterestingNodeString node )
	{ return Node<std::string>( sfncNames.find( node )->second, camera, logger ); }
};

} // anonymous namespace


GigENodes::GigENodes( CAM_HANDLE camera, boost::function<void(const std::string&)> logger )
	: camera( camera )
{
	// the types of all these nodes is as specified in the GenICam sfnc 1.4

	NodeFactory nf( camera, logger );

	// mandatory nodes
	intNodes.insert( std::make_pair( WIDTH, nf.IntNode( WIDTH ) ) );
	intNodes.insert( std::make_pair( HEIGHT, nf.IntNode( HEIGHT ) ) );
	stringNodes.insert( std::make_pair( PIXEL_FORMAT, nf.StringNode( PIXEL_FORMAT ) ) );
	stringNodes.insert( std::make_pair( ACQUISITION_MODE, nf.StringNode( ACQUISITION_MODE ) ) );

	// recommended/optional nodes
	stringNodes.insert( std::make_pair( DEVICE_VENDOR_NAME, nf.StringNode( DEVICE_VENDOR_NAME ) ) );
	stringNodes.insert( std::make_pair( DEVICE_MODEL_NAME, nf.StringNode( DEVICE_MODEL_NAME ) ) );
	stringNodes.insert( std::make_pair( DEVICE_MANUFACTURER_INFO, nf.StringNode( DEVICE_MANUFACTURER_INFO ) ) );
	stringNodes.insert( std::make_pair( DEVICE_VERSION, nf.StringNode( DEVICE_VERSION ) ) );
	stringNodes.insert( std::make_pair( DEVICE_FIRMWARE_VERSION, nf.StringNode( DEVICE_FIRMWARE_VERSION ) ) );
	stringNodes.insert( std::make_pair( DEVICE_ID, nf.StringNode( DEVICE_ID ) ) );

	intNodes.insert( std::make_pair( SENSOR_WIDTH, nf.IntNode( SENSOR_WIDTH ) ) );
	intNodes.insert( std::make_pair( SENSOR_HEIGHT, nf.IntNode( SENSOR_HEIGHT ) ) );
	intNodes.insert( std::make_pair( WIDTH_MAX, nf.IntNode( WIDTH_MAX ) ) );
	intNodes.insert( std::make_pair( HEIGHT_MAX, nf.IntNode( HEIGHT_MAX ) ) );
	intNodes.insert( std::make_pair( BINNING_HORIZONTAL, nf.IntNode( BINNING_HORIZONTAL ) ) );
	intNodes.insert( std::make_pair( BINNING_VERTICAL, nf.IntNode( BINNING_VERTICAL ) ) );
	
	stringNodes.insert( std::make_pair( EXPOSURE_MODE, nf.StringNode( EXPOSURE_MODE ) ) );
	floatNodes.insert( std::make_pair( EXPOSURE_TIME, nf.FloatNode( EXPOSURE_TIME ) ) );
	floatNodes.insert( std::make_pair( EXPOSURE_TIME_ABS, nf.FloatNode( EXPOSURE_TIME_ABS ) ) );
	intNodes.insert( std::make_pair( EXPOSURE_TIME_ABS_INT, nf.IntNode( EXPOSURE_TIME_ABS_INT ) ) );
	
	floatNodes.insert( std::make_pair( GAIN, nf.FloatNode( GAIN ) ) );
	intNodes.insert( std::make_pair( GAIN_RAW, nf.IntNode( GAIN_RAW ) ) );

	floatNodes.insert( std::make_pair( TEMPERATURE, nf.FloatNode( TEMPERATURE ) ) );

	intNodes.insert( std::make_pair( GEV_VERSION_MAJOR, nf.IntNode( GEV_VERSION_MAJOR ) ) );
	intNodes.insert( std::make_pair( GEV_VERSION_MINOR, nf.IntNode( GEV_VERSION_MINOR ) ) );

	floatNodes.insert( std::make_pair( ACQUISITION_FRAME_RATE, nf.FloatNode( ACQUISITION_FRAME_RATE ) ) );
	stringNodes.insert( std::make_pair( ACQUISITION_FRAME_RATE_STR, nf.StringNode( ACQUISITION_FRAME_RATE_STR ) ) );
}


GigENodes::~GigENodes(void)
{


}


bool GigENodes::get( int64_t& i, InterestingNodeInteger node )
{
	IntNode n = intNodes[node];
	if( n.isReadable() )
	{
		return n.get( camera, i ) == J_ST_SUCCESS;
	}
	return false;
}


bool GigENodes::get( double& i, InterestingNodeFloat node )
{
	FloatNode n = floatNodes[node];
	if( n.isReadable() )
	{
		return n.get( camera, i ) == J_ST_SUCCESS;
	}
	return false;
}


bool GigENodes::get( std::string& i, InterestingNodeString node )
{
	StringNode n = stringNodes[node];
	if( n.isReadable() )
	{
		return n.get( camera, i ) == J_ST_SUCCESS;
	}
	return false;
}


bool GigENodes::set( const int64_t i, InterestingNodeInteger node )
{
	IntNode n = intNodes[node];
	if( n.isWritable() )
	{
		return n.set( camera, i ) == J_ST_SUCCESS;
	}
	return false;
}


bool GigENodes::set( const double i, InterestingNodeFloat node )
{
	FloatNode n = floatNodes[node];
	if( n.isWritable() )
	{
		return n.set( camera, i ) == J_ST_SUCCESS;
	}
	return false;
}


bool GigENodes::set( const std::string i, InterestingNodeString node )
{
	StringNode n = stringNodes[node];
	if( n.isWritable() )
	{
		return n.set( camera, i ) == J_ST_SUCCESS;
	}
	return false;
}


int64_t GigENodes::getMin( InterestingNodeInteger node )
{
	return intNodes[node].getMinimum( camera );
}


int64_t GigENodes::getMax( InterestingNodeInteger node )
{
	return intNodes[node].getMaximum( camera );
}


int64_t GigENodes::getIncrement( InterestingNodeInteger node )
{
	return intNodes[node].getIncrement( camera );
}


double GigENodes::getMin( InterestingNodeFloat node )
{
	return floatNodes[node].getMinimum( camera );
}


double GigENodes::getMax( InterestingNodeFloat node )
{
	return floatNodes[node].getMaximum( camera );
}


bool GigENodes::hasIncrement( InterestingNodeFloat node )
{
	return floatNodes[node].hasIncrement( );
}


double GigENodes::getIncrement( InterestingNodeFloat node )
{
	return floatNodes[node].getIncrement( camera );
}


bool GigENodes::isEnum( InterestingNodeInteger node )
{
	return intNodes[node].isEnumeration( );
}


bool GigENodes::isEnum( InterestingNodeString node )
{
	return stringNodes[node].isEnumeration();
}


uint32_t GigENodes::getNumEnumEntries( InterestingNodeInteger node )
{
	uint32_t i;
	J_STATUS_TYPE retval = intNodes[node].getNumEnumEntries( camera, i );
	if( retval != J_ST_SUCCESS )
		return 0;
	return i;
}


uint32_t GigENodes::getNumEnumEntries( InterestingNodeString node )
{
	uint32_t i;
	J_STATUS_TYPE retval = stringNodes[node].getNumEnumEntries( camera, i );
	if( retval != J_ST_SUCCESS )
		return 0;
	return i;
}


bool GigENodes::getEnumEntry( std::string& entry, uint32_t index, InterestingNodeInteger node )
{
	J_STATUS_TYPE retval = intNodes[node].getEnumEntry( camera, index, entry );
	if( retval == J_ST_SUCCESS )
		return true;
	else
		return false;
}


bool GigENodes::getEnumEntry( std::string& entry, uint32_t index, InterestingNodeString node )
{
	J_STATUS_TYPE retval = stringNodes[node].getEnumEntry( camera, index, entry );
	if( retval == J_ST_SUCCESS )
		return true;
	else
		return false;
}


bool GigENodes::getEnumDisplayName( std::string& name, uint32_t index, InterestingNodeInteger node )
{
	J_STATUS_TYPE retval = intNodes[node].getEnumDisplayName( camera, index, name );
	if( retval == J_ST_SUCCESS )
		return true;
	else
		return false;
}


bool GigENodes::getEnumDisplayName( std::string& name, uint32_t index, InterestingNodeString node )
{
	J_STATUS_TYPE retval = stringNodes[node].getEnumDisplayName( camera, index, name );
	if( retval == J_ST_SUCCESS )
		return true;
	else
		return false;
}







template< class T > Node<T>::Node( )
	: sfncName( "" ),
	  available( false ),
	  readable( false ),
	  writable( false ),
	  hasMin( false ),
	  hasMax( false ),
	  hasInc( false ),
	  isEnum( false )
{
	
}

template< class T > Node<T>::Node( const std::string name, CAM_HANDLE camera, boost::function<void(const std::string&)> logger )
	: sfncName( name ),
	  available( false ),
	  readable( false ),
	  writable( false ),
	  hasMin( false ),
	  hasMax( false ),
	  hasInc( false ),
	  isEnum( false )
{
	testAvailability( camera, logger );
	testMinMaxInc( camera, logger );
	testEnum( camera, logger );
}


template< class T > Node<T>::Node( const Node<T>& n ) 
	: sfncName( n.sfncName ),
	  available( n.available ),
	  readable( n.readable ),
	  writable( n.writable ),
	  hasMin( n.hasMin ),
	  hasMax( n.hasMax ),
	  hasInc( n.hasInc ),
	  isEnum( n.isEnum ),
	  val( n.val )
{

}

template<class T> Node<T>& Node<T>::operator=( const Node<T>& rhs )
{
	if( this == &rhs ) return *this;
	this->sfncName = rhs.sfncName;
	this->available = rhs.available;
	this->readable = rhs.readable;
	this->writable = rhs.writable;
	this->isEnum = rhs.isEnum;
	this->val = rhs.val;
	return *this;
}


template<class T> void Node<T>::testAvailability( CAM_HANDLE camera, boost::function<void(const std::string&)> logger )
{
	logger( "Getting node: " + sfncName );
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
	{
		logger( "Failed to get node: " + sfncName );
		this->available = false;
		this->readable = false;
		this->writable = false;
		return;
	}

	logger( "Getting node access mode: " + sfncName );
	J_NODE_ACCESSMODE access;
	retval = J_Node_GetAccessMode( node, &access );
	if( retval != J_ST_SUCCESS )
	{
		logger( "Failed to get node access mode: " + sfncName );
		this->available = false;
		this->readable = false;
		this->writable = false;
		return;
	}
	logger( "Node " + sfncName + " access mode = " + boost::lexical_cast<std::string>( access ) );
	switch( access )
	{
	case NI:  // not implemented
	case NA:  // not available
	case _UndefinedAccesMode:  // not initialized propertly or yet (yes, misspelled as Acces)
		this->available = false;
		this->readable = false;
		this->writable = false;
		break;
	case WO:  // write-only
		this->available = true;
		this->readable = false;
		this->writable = true;
		break;
	case RO:  // read-only;
		this->available = true;
		this->readable = true;
		this->writable = false;
		break;
	case RW:  // read-write
		this->available = true;
		this->readable = true;
		this->writable = true;
		break;
	}
	testType( node, logger );
}


template<> void Node<int64_t>::testType( NODE_HANDLE node, boost::function<void(const std::string&)> logger )
{
	logger( "Getting node type: " + sfncName );
	J_NODE_TYPE type;
	J_STATUS_TYPE retval = J_Node_GetType( node, &type );
	if( retval != J_ST_SUCCESS )
	{
		logger( "Failed to get node type: " + sfncName );
		this->available = false;
		this->readable = false;
		this->writable = false;
	}

	// disable this node if the camera reports a type different than that expected
	if( type != J_IInteger && type != J_IEnumeration )
	{
		logger( "Unexpected type for node " + sfncName + ": expected integer or enum; got " + boost::lexical_cast<std::string>( type ) );
		this->available = false;
		this->readable = false;
		this->writable = false;
	}
}


template<> void Node<double>::testType( NODE_HANDLE node, boost::function<void(const std::string&)> logger )
{
	logger( "Getting node type: " + sfncName );
	J_NODE_TYPE type;
	J_STATUS_TYPE retval = J_Node_GetType( node, &type );
	if( retval != J_ST_SUCCESS )
	{
		logger( "Failed to get node type: " + sfncName );
		this->available = false;
		this->readable = false;
		this->writable = false;
	}

	// disable this node if the camera reports a type different than that expected
	if( type != J_IFloat )
	{
		logger( "Unexpected type for node " + sfncName + ": expected float; got " + boost::lexical_cast<std::string>( type ) );
		this->available = false;
		this->readable = false;
		this->writable = false;
	}
}


template<> void Node<std::string>::testType( NODE_HANDLE node, boost::function<void(const std::string&)> logger )
{
	logger( "Getting node type: " + sfncName );
	J_NODE_TYPE type;
	J_STATUS_TYPE retval = J_Node_GetType( node, &type );
	if( retval != J_ST_SUCCESS )
	{
		logger( "Failed to get node type: " + sfncName );
		this->available = false;
		this->readable = false;
		this->writable = false;
	}

	// disable this node if the camera reports a type different than that expected
	if( type != J_IStringReg && type != J_IEnumeration )
	{
		logger( "Unexpected type for node " + sfncName + ": expected string or enum; got " + boost::lexical_cast<std::string>( type ) );
		this->available = false;
		this->readable = false;
		this->writable = false;
	}
}


template<class T> void Node<T>::testEnum( CAM_HANDLE camera, boost::function<void(const std::string&)> logger )
{
	logger( "Getting node: " + sfncName );
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
	{
		logger( "Failed to get node: " + sfncName );
		this->isEnum = false;
		return;
	}

	logger( "Getting node type: " + sfncName );
	J_NODE_TYPE type;
	retval = J_Node_GetType( node, &type );
	if( retval != J_ST_SUCCESS )
	{
		logger( "Failed to get node type: " + sfncName );
		this->isEnum = false;
		return;
	}
	if( type == J_IEnumeration )
		this->isEnum = true;
	else
		this->isEnum = false;
}



template<> void Node<int64_t>::testMinMaxInc( CAM_HANDLE, boost::function<void(const std::string&)> logger )
{
	this->hasMin = true;
	this->hasMax = true;
	this->hasInc = true;
}


template<> void Node<double>::testMinMaxInc( CAM_HANDLE camera, boost::function<void(const std::string&)> logger )
{
	this->hasMin = true;
	this->hasMax = true;
	logger( "Getting node: " + sfncName );
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS ) {
		logger( "Failed to get node: " + sfncName );
		this->hasInc = false;
		return;
	}
	logger( "Getting float node increment flag: " + sfncName );
	uint32_t i;
	retval = J_Node_GetFloatHasInc( node, &i );
	if( retval != J_ST_SUCCESS || i == 0 ) {
		this->hasInc = false;
		logger( "Failed to get float node increment flag: " + sfncName );
	}
	else
		this->hasInc = true;
}


template<> void Node<std::string>::testMinMaxInc( CAM_HANDLE, boost::function<void(const std::string&)> logger )
{
	this->hasMin = false;
	this->hasMax = false;
	this->hasInc = false;
}


template<> int64_t Node<int64_t>::getMinimum( CAM_HANDLE camera )
{
	int64_t i = 0;
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return 0;
	retval = J_Node_GetMinInt64( node, &i );
	if( retval == J_ST_SUCCESS )
		return i;
	else
		return 0;
}


template<> int64_t Node<int64_t>::getMaximum( CAM_HANDLE camera )
{
	int64_t i = 0;
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return 0;
	retval = J_Node_GetMaxInt64( node, &i );
	if( retval == J_ST_SUCCESS )
		return i;
	else
		return 0;
}


template<> int64_t Node<int64_t>::getIncrement( CAM_HANDLE camera )
{
	int64_t i = 0;
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return 0;
	retval = J_Node_GetInc( node, &i );
	if( retval == J_ST_SUCCESS )
		return i;
	else
		return 0;
}


template<> double Node<double>::getMinimum( CAM_HANDLE camera )
{
	double i = 0;
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return 0;
	retval = J_Node_GetMinDouble( node, &i );
	if( retval == J_ST_SUCCESS )
		return i;
	else
		return 0;
}


template<> double Node<double>::getMaximum( CAM_HANDLE camera )
{
	double i = 0;
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return 0;
	retval = J_Node_GetMaxDouble( node, &i );
	if( retval == J_ST_SUCCESS )
		return i;
	else
		return 0;
}


template<> double Node<double>::getIncrement( CAM_HANDLE camera )
{
	double i = 0;
	if( !(this->hasInc ) ) return 0;
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return 0;
	retval = J_Node_GetFloatInc( node, &i );
	if( retval == J_ST_SUCCESS )
		return i;
	else
		return 0;
}


template<> std::string Node<std::string>::getMinimum( CAM_HANDLE )
{
	// strings never have a min, so return an empty string
	return (std::string)"";
}


template<> std::string Node<std::string>::getMaximum( CAM_HANDLE )
{
	// strings never have a max, so return an empty string
	return (std::string)"";
}


template<> std::string Node<std::string>::getIncrement( CAM_HANDLE )
{
	// strings never have an increment, so return an empty string
	return (std::string)"";
}


template<> J_STATUS_TYPE Node<int64_t>::get( CAM_HANDLE camera, int64_t& a )
{
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return retval;
	int64_t i;
	retval = J_Node_GetValueInt64( node, false, &i );
	if( retval == J_ST_SUCCESS )
		a = i;
	return retval;
}


template<> J_STATUS_TYPE Node<double>::get( CAM_HANDLE camera, double& a )
{
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return retval;
	double i;
	retval = J_Node_GetValueDouble( node, false, &i );
	if( retval == J_ST_SUCCESS )
		a = i;
	return retval;
}


template<> J_STATUS_TYPE Node<std::string>::get( CAM_HANDLE camera, std::string& a )
{
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return retval;
	uint32_t size = 512;
	int8_t s[512];
	retval = J_Node_GetValueString( node, false, s, &size );
	if( retval == J_ST_SUCCESS )
		a = s;
	return retval;
}


template<> J_STATUS_TYPE Node<int64_t>::set( CAM_HANDLE camera, const int64_t& a )
{
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return retval;
	retval = J_Node_SetValueInt64( node, true, a );
	return retval;
}


template<> J_STATUS_TYPE Node<double>::set( CAM_HANDLE camera, const double& a )
{
	NODE_HANDLE node;
	J_STATUS_TYPE retval  = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return retval;
	retval = J_Node_SetValueDouble( node, true, a );
	return retval;
}


template<> J_STATUS_TYPE Node<std::string>::set( CAM_HANDLE camera, const std::string& a )
{
	NODE_HANDLE node;
	J_STATUS_TYPE retval = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return retval;
	retval = J_Node_SetValueString( node, true, const_cast<char*>( a.c_str() ) );
	return retval;
}


template<class T> J_STATUS_TYPE Node<T>::getNumEnumEntries( CAM_HANDLE camera, uint32_t& i )
{
	NODE_HANDLE node;
	J_STATUS_TYPE retval = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return retval;
	retval = J_Node_GetNumOfEnumEntries( node, &i );
	return retval;
}


template<class T> J_STATUS_TYPE Node<T>::getEnumEntry( CAM_HANDLE camera, uint32_t index, std::string& entry )
{
	NODE_HANDLE node;
	J_STATUS_TYPE retval = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return retval;
	NODE_HANDLE enumEntry;
	retval = J_Node_GetEnumEntryByIndex( node, index, &enumEntry );
	if( retval != J_ST_SUCCESS )
		return retval;
	uint32_t len = 512;
	char a[512];
	retval = J_Node_GetName( enumEntry, a, &len );
	if( retval != J_ST_SUCCESS )
		return retval;

	// the JAI sdk prepends some stuff in front of that GenICam name.
	// this stuff is of the format "EnumEntry_(NodeName)_(EnumValue)".
	// per personal communication with Gordon Rice at JAI, we need to
	// chop off everything before (and including) the second underscore.
	std::string s = a;
	size_t pos = s.find_first_of( '_' );
	if( pos != std::string::npos && pos != s.length() )
	{
		s = s.substr( pos + 1 );
		pos = s.find_first_of( '_' );
		if( pos != std::string::npos && pos != s.length() )
			s = s.substr( pos + 1 );
	}
	entry = s;

	return retval;
}


template<class T> J_STATUS_TYPE Node<T>::getEnumDisplayName( CAM_HANDLE camera, uint32_t index, std::string& name )
{
	NODE_HANDLE node;
	J_STATUS_TYPE retval = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return retval;
	NODE_HANDLE enumEntry;
	retval = J_Node_GetEnumEntryByIndex( node, index, &enumEntry );
	if( retval != J_ST_SUCCESS )
		return retval;
	uint32_t len = 512;
	char a[512];
	retval = J_Node_GetDisplayName( enumEntry, a, &len );
	if( retval != J_ST_SUCCESS )
		return retval;
	name = a;
	return retval;
}
	
/*
template<class T> J_STATUS_TYPE Node<T>::getEnumEntryFromDisplayName( CAM_HANDLE camera, const std::string name, std::string& entry )
{
	NODE_HANDLE node;
	J_STATUS_TYPE retval = J_Camera_GetNodeByName( camera, const_cast<char*>( this->sfncName.c_str() ), &node );
	if( retval != J_ST_SUCCESS )
		return retval;

	uint32_t n;
	retval = this->getNumEnumEntries( camera, n );
	if( retval != J_ST_SUCCESS )
		return retval;
	NODE_HANDLE enumEntry;
	for( uint32_t i = 0; i <= n - 1; i++ )
	{
		retval = J_Node_GetEnumEntryByIndex( node, index, &enumEntry );
		if( retval != J_ST_SUCCESS )
			return retval;
		uint32_t len = 512;
		char a[512];
		retval = J_Node_GetDisplayName( enumEntry, a, &len );
		if( retval != J_ST_SUCCESS )
			return retval;
		if( name.compare( a ) == 0 )
		{
			return this->getEnumEntry( camera, i, entry ) 
		}
	}

	// if we get this far, we didn't find one
	return J_ST_ERROR;
}
*/


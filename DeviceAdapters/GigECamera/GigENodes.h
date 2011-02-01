///////////////////////////////////////////////////////////////////////////////
// FILE:          GigENodes.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   An adapter for Gigbit-Ethernet cameras using an
//				  SDK from JAI, Inc.  Users and developers will 
//				  need to download and install the JAI SDK and control tool.
//                
// AUTHOR:        David Marshburn, UNC-CH, marshbur@cs.unc.edu, Jan. 2011
//

#pragma once

#include <string>
#include <vector>
#include <map>
#include <boost/any.hpp>
#include <Jai_Factory.h>


// based on GenICam Standard Features Naming Convention 1.4
enum InterestingNodeInteger
{
	// mandatory nodes
	WIDTH = 0,
	HEIGHT,

	// recommended/optional nodes
	SENSOR_WIDTH,
	SENSOR_HEIGHT,
	WIDTH_MAX,
	HEIGHT_MAX,
	BINNING_HORIZONTAL,
	BINNING_VERTICAL,
	GEV_VERSION_MAJOR,
	GEV_VERSION_MINOR,

	EXPOSURE_TIME_ABS_INT, // deprecated, but JAI seems to use only this, not EXPOSURE_TIME
	// also, the GenICam docs say this should be a float, but our camera has it as an int

	GAIN_RAW,  // deprecated, but JAI seems to use only this, not GAIN

	// ensure that the last enum value does not overlap with the values of the following enum
};


enum InterestingNodeFloat
{
	// ensure that the first enum value does not overlap with the values of the previous enum

	// recommended/optional nodes
	EXPOSURE_TIME = 50,
	EXPOSURE_TIME_ABS, // deprecated, but JAI seems to use only this, not EXPOSURE_TIME
	GAIN,
	TEMPERATURE,
	ACQUISITION_FRAME_RATE,

	// ensure that the last enum value does not overlap with the values of the following enum
};


enum InterestingNodeString
{
	// ensure that the first enum value does not overlap with the values of the previous enum

	// mandatory nodes
	PIXEL_FORMAT = 150,  // IEnumeration
	ACQUISITION_MODE,

	// recommended/optional nodes
	DEVICE_VENDOR_NAME,
	DEVICE_MODEL_NAME,
	DEVICE_MANUFACTURER_INFO,
	DEVICE_VERSION,
	DEVICE_FIRMWARE_VERSION,
	DEVICE_ID,

	EXPOSURE_MODE,

	ACQUISITION_FRAME_RATE_STR, // this is supposed to be a float, but our camera has it as a string enum
};


template<class T> class Node
{
public:
	Node();
	Node( const std::string name );
	Node( const Node& );
	virtual ~Node() { }
	Node<T>& operator=( const Node<T>& rhs );
	void setName( std::string sfncName ) { this->sfncName = sfncName; }

	bool isAvailable() { return available; }
	bool isReadable() { return readable; }
	bool isWritable() { return writable; }
	bool hasMinimum() { return hasMin; }
	bool hasMaximum() { return hasMax; }
	bool hasIncrement() { return hasInc; }
	T getMinimum( CAM_HANDLE camera );
	T getMaximum( CAM_HANDLE camera );
	T getIncrement( CAM_HANDLE camera );
	J_STATUS_TYPE get( CAM_HANDLE camera, T& );
	J_STATUS_TYPE set( CAM_HANDLE camera, const T& );
	
	bool isEnumeration() { return isEnum; }
	J_STATUS_TYPE getNumEnumEntries( CAM_HANDLE camera, uint32_t& i );
	J_STATUS_TYPE getEnumEntry( CAM_HANDLE camera, uint32_t index, std::string& entry );
	J_STATUS_TYPE getEnumDisplayName( CAM_HANDLE camera, uint32_t index, std::string& name );
	//J_STATUS_TYPE getEnumEntryFromDisplayName( CAM_HANDLE camera, const std::string name, std::string& entry );

	typedef typename T value_type;

	void testAvailability( CAM_HANDLE camera );
	void testMinMaxInc( CAM_HANDLE camera );
	void testEnum( CAM_HANDLE camera );

protected:
	bool available;
	bool readable;
	bool writable;
	bool hasMin;
	bool hasMax;
	bool hasInc;
	bool isEnum;
	std::string sfncName;
	T val; 

	// test the type of the node.  if not the type we expect,
	// (for instance, if the camera is an older model that isn't
	// completely GenICam-compliant) disable the node
	void testType( NODE_HANDLE camera );
};



class GigENodes
{
protected:
	typedef Node<int64_t> IntNode;
	typedef Node<double> FloatNode;
	typedef Node<std::string> StringNode;

	typedef std::pair<int, IntNode> IntPairType;
	typedef std::pair<int, FloatNode> FloatPairType;
	typedef std::pair<int, StringNode> StringPairType;

	typedef std::map< int, Node<int64_t> > IntMapType;
	typedef std::map< int, Node<double> > FloatMapType;
	typedef std::map< int, Node<std::string> > StringMapType;

	IntMapType intNodes;
	FloatMapType floatNodes;
	StringMapType stringNodes;

	static std::map< int, std::string > sfncNames;
	CAM_HANDLE camera;

public:
	GigENodes( CAM_HANDLE camera );
	virtual ~GigENodes(void);

	bool isAvailable( InterestingNodeInteger node )
	{  return intNodes[node].isAvailable();  }
	bool isAvailable( InterestingNodeFloat node )
	{  return floatNodes[node].isAvailable();  }
	bool isAvailable( InterestingNodeString node )
	{  return stringNodes[node].isAvailable();  }

	bool isWritable( InterestingNodeInteger node )
	{  return intNodes[node].isWritable();  }
	bool isWritable( InterestingNodeFloat node )
	{  return floatNodes[node].isWritable();  }
	bool isWritable( InterestingNodeString node )
	{  return stringNodes[node].isWritable();  }

	bool isReadable( InterestingNodeInteger node )
	{  return intNodes[node].isReadable();  }
	bool isReadable( InterestingNodeFloat node )
	{  return floatNodes[node].isReadable();  }
	bool isReadable( InterestingNodeString node )
	{  return stringNodes[node].isReadable();  }

	bool get( int64_t& i, InterestingNodeInteger node );
	bool get( double& i, InterestingNodeFloat node );
	bool get( std::string& i, InterestingNodeString node );

	bool set( const int64_t i, InterestingNodeInteger node );
	bool set( const double i, InterestingNodeFloat node );
	bool set( const std::string i, InterestingNodeString node );

	// a note on min, max and increment:  (per the JAI library documentation)
	// - GenICam integer nodes always have a min, max and increment
	// - float (double) nodes always have min and max, and may have increment
	// - string nodes never have min, max or increment
	int64_t getMin( InterestingNodeInteger node );
	int64_t getMax( InterestingNodeInteger node );
	int64_t getIncrement( InterestingNodeInteger node );

	double getMin( InterestingNodeFloat node );
	double getMax( InterestingNodeFloat node );
	bool hasIncrement( InterestingNodeFloat node );
	double getIncrement( InterestingNodeFloat node );

	// per the JAI library documentation, only integer and string nodes can be enumerated
	bool isEnum( InterestingNodeInteger node );
	bool isEnum( InterestingNodeString node );
	uint32_t getNumEnumEntries( InterestingNodeInteger node );
	uint32_t getNumEnumEntries( InterestingNodeString node );
	bool getEnumEntry( std::string& entry, uint32_t index, InterestingNodeInteger node );
	bool getEnumEntry( std::string& entry, uint32_t indes, InterestingNodeString node );
	bool getEnumDisplayName( std::string& name, uint32_t index, InterestingNodeInteger node );
	bool getEnumDisplayName( std::string& name, uint32_t index, InterestingNodeString node );
	//bool getEnumEntryFromDisplayName( std::string& entry, const std::string displayName, InterestingNodeInteger node );
	//bool getEnumEntryFromDisplayName( std::string& entry, const std::string displayName, InterestingNodeString node );
	
};




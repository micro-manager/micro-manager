#include "PropertyHelper.h"

using namespace DirectElectronPlugin;

PropertyHelper::PropertyHelper()
{
	_min = _max = 0;	
	_values.clear();
}	

bool PropertyHelper::Parse(DEPacket& pkt)
{
	_min = _max = 0;
	_values.clear();

	SingleAcknowledge ack = pkt.acknowledge(0);
	if (ack.parameter_size() == 0) return false;
	
	if (string("property").compare(ack.parameter(0).name()) != 0 ||
		ack.parameter(0).type() != AnyParameter::P_STRING)
		return false;

	string property_type = ack.parameter(0).p_string();
	if( property_type.compare("string")==0 )
		this->_property = String; 
	else if( property_type.compare("float")==0 )
		this->_property = Float; 
	else if( property_type.compare("int")==0 )
		this->_property = Integer; 
	else
		return false;

	if (string("type").compare(ack.parameter(1).name()) != 0 ||
		ack.parameter(1).type() != AnyParameter::P_STRING)
		return false;	

	string property_allowable_type = ack.parameter(1).p_string();

	if (property_allowable_type.compare("range") == 0)
	{
		switch (this->_property) {
		case Float:
			this->_type = Range;
			if (ack.parameter_size() == 4 &&
				ack.parameter(2).type() == AnyParameter::P_FLOAT &&
				ack.parameter(3).type() == AnyParameter::P_FLOAT)
			{
				this->_min = ack.parameter(2).p_float();
				this->_max = ack.parameter(3).p_float();
			}
			break;
		case Integer:
			this->_type = Range;
			if (ack.parameter_size() == 4 &&
				ack.parameter(2).type() == AnyParameter::P_INT &&
				ack.parameter(3).type() == AnyParameter::P_INT)
			{
				this->_min = (double)ack.parameter(2).p_int();
				this->_max = (double)ack.parameter(3).p_int();
			}
			break;
		default: 
			return false;
		}
	}
	else if (property_allowable_type.compare("set") == 0)
	{
		this->_type = Set;
		for (int i = 2; i < ack.parameter_size(); i++)
		{
			if (ack.parameter(i).type() == AnyParameter::P_STRING)
			{
				this->_values.push_back(ack.parameter(i).p_string());
			}
		}
	}
	else if (property_allowable_type.compare("allow_all") == 0)
	{
		this->_type = Allow_All;
	}
	else if (property_allowable_type.compare("ReadOnly") == 0)
	{
		this->_type = ReadOnly;
	}
	else 
		return false;

	return true;
}

PropertyHelper::Type PropertyHelper::GetType()
{
	return this->_type;
}

PropertyHelper::PropertyType PropertyHelper::GetProperty()
{
	return this->_property;
}

boost::tuple<double, double> PropertyHelper::GetRange()
{
	return boost::tuple<double, double>(this->_min, this->_max);
}

void PropertyHelper::GetSet(vector<string>& values)
{
	for (unsigned int i = 0; i < this->_values.size(); i++)
	{
		values.push_back(this->_values[i]);
	}
}

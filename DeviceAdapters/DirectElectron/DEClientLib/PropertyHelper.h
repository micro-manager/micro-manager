#pragma once

#include <string>
#include <vector>

#include <boost/tuple/tuple.hpp>
#include "DEServer.pb.h"

using namespace DEMessaging;
using namespace std;

namespace DirectElectronPlugin
{
	/**
	 * Creating Properties.
	 */
	class PropertyHelper
	{
	public:
		enum Type { Range = 0, Set, ReadOnly, Allow_All };		
		enum PropertyType { String = 0, Float, Integer };
			
		PropertyHelper();
		bool Parse(DEPacket& pkt);
		Type GetType();
		PropertyType GetProperty();
		boost::tuple<double, double> GetRange();
		void GetSet(vector<string>& values);		

	private:
		Type _type;
		PropertyType _property; 
		double _min;
		double _max;
		vector<string> _values;		
	};
}

#pragma once

#include <vector>
#include <string>
#include <boost/tuple/tuple.hpp>
#include "DEConstants.h"
#include "DEServer.pb.h"

using namespace std;
using namespace DEMessaging;

namespace DEMessaging
{
	class PacketUtility
	{
		typedef boost::tuple<AnyParameter::Type, void*, string> paramType;
		public:
			PacketUtility();
			PacketUtility(bool persistent);
			~PacketUtility();
			void add(int* val);
			void add(float* val);
			void add(string* val);
			void add(int* val, string label);
			void add(float* val, string label);
			void add(string* val, string label);
			void clear();

			bool getValues(const SingleCommand& singleCommand);
			bool getValues(const SingleAcknowledge& singleAck);
			bool getStrings(const SingleAcknowledge& singleAck, vector<string>& values);


		private:
			//vector<AnyParameter::Type> paramTypes;
			//vector<void*> paramVals; 
			vector<paramType> _params;
			bool persistent;
	};
}

#pragma once

#include <vector>
#include <string>
#include <boost/tuple/tuple.hpp>
#include <boost/tuple/tuple.hpp>
#include "DEConstants.h"
#include "DEServer.pb.h"

using namespace std;
using namespace DEMessaging;

namespace DEMessaging
{
	class PacketParser
	{
		typedef boost::tuple<AnyParameter::Type, void*, string> ParamType;
		public:
			PacketParser();
			PacketParser(bool persistent);
			~PacketParser();
			void add(int* val);
			void add(float* val);
			void add(string* val);
			void add(int* val, string label);
			void add(float* val, string label);
			void add(string* val, string label);
			void clear();

			bool getStrings(const SingleAcknowledge& singleAck, vector<string>& values);
			
			template <class T> bool getValues(const T& singleCommand)
			{
				// Check return types.
				bool formatIsCorrect = true;
				if (singleCommand.parameter_size() != this->_params.size())
					formatIsCorrect = false;

				if (formatIsCorrect)
				{
					for (unsigned int i = 0; i < this->_params.size(); i++)
					{
						if (singleCommand.parameter(i).type() != this->_params[i].get<0>())
						{
							formatIsCorrect = false;
							break;
						}
					}
				}

				// Everything checks out, get values.
				if (formatIsCorrect)
				{
					for (int i = 0; i < singleCommand.parameter_size(); i++)
					{
						this->_writeValue(singleCommand.parameter(i), this->_params[i]);
					}
				}

				if (!this->_persistent) this->clear();
				return formatIsCorrect;
			}

			template <class T>
			void getParams(const T& singleAck, vector<AnyP>& values)
			{
				for (int i = 0; i < singleAck.parameter_size(); i++)
				{
					AnyP val;
					switch (singleAck.parameter(i).type()) {		
						case AnyParameter::P_INT:
							val = singleAck.parameter(i).p_int();
							break;
						case AnyParameter::P_BOOL:
							val = singleAck.parameter(i).p_bool();
							break;
						case AnyParameter::P_FLOAT:
							val = singleAck.parameter(i).p_float();
							break;
						case AnyParameter::P_STRING:
							val = singleAck.parameter(i).p_string();
							break;
					}
					values.push_back(val);
				}
			}
		private:
			vector<ParamType> _params;
			bool _persistent;
			
			void _writeValue(const AnyParameter& param, ParamType& v);
	};
}

#include "microz.h"

const char* g_StageDeviceName_STEP = "MicroZ-Step";
const char* g_StageDeviceName_JOG = "MicroZ-Jog";
double MicroZStage::stepSize_um_ = 0.0;

///////////////////////////////////////////////////////////////////////////////
// CRC16
///////////////////////////////////////////////////////////////////////////////
uint16_t crc16_modbus(const unsigned char* input_str, size_t num_bytes) {

	uint16_t crc;
	const unsigned char* ptr;
	size_t a;

	if (!crc_tab16_init) crc16_init();

	crc = CRC_START_MODBUS;
	ptr = input_str;

	if (ptr != NULL) for (a = 0; a < num_bytes; a++) {

		crc = (crc >> 8) ^ crc_tab16[(crc ^ (uint16_t)*ptr++) & 0x00FF];
	}

	return crc;

}

void crc16_init(void) {

	uint16_t i;
	uint16_t j;
	uint16_t crc;
	uint16_t c;

	for (i = 0; i < 256; i++) {

		crc = 0;
		c = i;

		for (j = 0; j < 8; j++) {

			if ((crc ^ c) & 0x0001) crc = (crc >> 1) ^ CRC_POLY_16;
			else                      crc = crc >> 1;

			c = c >> 1;
		}

		crc_tab16[i] = crc;
	}

	crc_tab16_init = true;
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_StageDeviceName_STEP, MM::StageDevice, "MicroZ Stage Step Mode");
	RegisterDevice(g_StageDeviceName_JOG, MM::StageDevice, "MicroZ Stage Jog Mode");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (strcmp(deviceName, g_StageDeviceName_STEP) == 0) {
		MicroZStage* device = new MicroZStage();
		device->SetType(false);
		return device;
	}

	if (strcmp(deviceName, g_StageDeviceName_JOG) == 0) {
		MicroZStage* device = new MicroZStage();
		device->SetType(true);
		return device;
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

//////////////////////////////////////////////////////////////////////////////
// MicroZStage class
//////////////////////////////////////////////////////////////////////////////
MicroZStage::MicroZStage()
	: isReverseDirection_(false)
	, busy_(false)
	, isJog_(false)
	, isJogRunning_(false)
	, isSetZero_(false)
	, velocity_(1000)
	, goPos_(0.0)
{
	InitializeDefaultErrorMessages();

	stepSize_um_ = 0.01;
	pos_um_ = 0.0;
	busy_ = false;
	initialized_ = false;

	CPropertyAction* pAct = new CPropertyAction(this, &MicroZStage::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

MicroZStage::~MicroZStage()
{
	Shutdown();
}

void MicroZStage::GetName(char* pszName) const
{
	if (isJog_) {
		CDeviceUtils::CopyLimitedString(pszName, g_StageDeviceName_JOG);
	}
	else {
		CDeviceUtils::CopyLimitedString(pszName, g_StageDeviceName_STEP);
	}
}

void MicroZStage::SetType(bool isJog)
{
	isJog_ = isJog;
}

int MicroZStage::Initialize()
{
	core_ = GetCoreCallback();
	PurgeComPort(port_.c_str());

	if (initialized_)
		return DEVICE_OK;

	int ret;
	if (isJog_) {
		ret = CreateStringProperty(MM::g_Keyword_Name, g_StageDeviceName_JOG, true);
		if (DEVICE_OK != ret)
			return ret;
	}
	else {
		ret = CreateStringProperty(MM::g_Keyword_Name, g_StageDeviceName_STEP, true);
		if (DEVICE_OK != ret)
			return ret;
	}

	ret = CreateStringProperty(MM::g_Keyword_Description, "DemoZ stage driver", true);
	if (DEVICE_OK != ret)
		return ret;

	CPropertyAction* pAct = nullptr;
	if (!isJog_) {
		pAct = new CPropertyAction(this, &MicroZStage::OnSetStepSize);
		ret = CreateFloatProperty("Step Size(um)", 0.1, false, pAct);
		if (DEVICE_OK != ret)
			return ret;

		pAct = new CPropertyAction(this, &MicroZStage::OnGotoValue);
		ret = CreateFloatProperty("Goto Position(um)", 0.0, false, pAct);
		if (DEVICE_OK != ret)
			return ret;

		pAct = new CPropertyAction(this, &MicroZStage::OnGoto);
		ret = CreateProperty("Goto", "NO", MM::String, false, pAct);
		AddAllowedValue("Goto", "YES");
		AddAllowedValue("Goto", "NO");
		if (DEVICE_OK != ret)
			return ret;

		pAct = new CPropertyAction(this, &MicroZStage::OnSetVelocity);
		ret = CreateProperty("Velocity(step)", "1000", MM::Integer, false, pAct);
		if (DEVICE_OK != ret)
			return ret;

		pAct = new CPropertyAction(this, &MicroZStage::OnZHome);
		ret = CreateProperty("ZHome", "NO", MM::String, false, pAct);
		AddAllowedValue("ZHome", "NO");
		AddAllowedValue("ZHome", "YES");
		if (DEVICE_OK != ret)
			return ret;
	}

	pAct = new CPropertyAction(this, &MicroZStage::OnReverseDirection);
	ret = CreateProperty("Reverse Direction", "NO", MM::String, false, pAct);
	AddAllowedValue("Reverse Direction", "NO");
	AddAllowedValue("Reverse Direction", "YES");
	if (DEVICE_OK != ret)
		return ret;

	pAct = new CPropertyAction(this, &MicroZStage::OnEStop);
	ret = CreateProperty("Stop", "NO", MM::String, false, pAct);
	AddAllowedValue("Stop", "NO");
	AddAllowedValue("Stop", "YES");
	if (DEVICE_OK != ret)
		return ret;

	pAct = new CPropertyAction(this, &MicroZStage::OnSetZero);
	ret = CreateProperty("SetZero", "NO", MM::String, false, pAct);
	AddAllowedValue("SetZero", "NO");
	AddAllowedValue("SetZero", "YES");
	if (DEVICE_OK != ret)
		return ret;

	ret = UpdateStatus();
	if (DEVICE_OK != ret)
		return ret;

	initialized_ = true;

	return DEVICE_OK;
}

int MicroZStage::Shutdown()
{
	if (initialized_)
		initialized_ = false;

	return DEVICE_OK;
}

int MicroZStage::CommandQuery(unsigned char* command, size_t c_len, unsigned char* answer, size_t a_len)
{
	int ret = DEVICE_OK;

	SendData(command, (unsigned int)c_len);
	unsigned long num = 0;
	unsigned long read_count = 0;

	MM::MMTime startTime = GetCurrentMMTime();
	MM::MMTime now = startTime;
	MM::MMTime timeOut(1000 * 1000);

	do {
		ReadFromComPort(port_.c_str(), answer + read_count, (unsigned int)a_len - read_count, num);
		read_count += num;
		
		now = GetCurrentMMTime();
		if ((now - startTime) > timeOut) {
			ret = DEVICE_NOT_CONNECTED;
			break;
		}

	} while (read_count != a_len);

	return ret;
}

int MicroZStage::SetPositionUm(double pos)
{
	pos_um_ = pos;

	double num_steps = pos / stepSize_um_;

	unsigned int val = (unsigned int)num_steps;
	unsigned char h_val[4] = { 0x00, 0x00, 0x00, 0x00 };

	unsigned int temp = val;
	int i = 0;
	while (temp > 0) {
		h_val[i] = temp % 256;
		temp = temp / 256;

		i++;
	}

	unsigned char ret[4] = { 0x00, 0x00, 0x00, 0x00 };
	int j = 0;
	for (int k = 3; k >= 0; k--) {
		ret[j] = h_val[k];
		j++;
	}

	// set real position
	unsigned char data1[] = { 0x01, 0x10, 0x18, 0x02, 0x00, 0x04, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x13, 0x88, 0x06, 0xE7 };
	data1[7] = ret[0];
	data1[8] = ret[1];
	data1[9] = ret[2];
	data1[10] = ret[3];

	unsigned char temp1[15];
	for (int i = 0; i < 15; i++) {
		temp1[i] = data1[i];
	}

	// calculate CRC16 for data1 0~15
	uint16_t crc161 = crc16_modbus(temp1, sizeof(temp1) / sizeof(unsigned char));
	uint16_t first = crc161 / 256;
	uint16_t second = crc161 % 256;
	data1[15] = (unsigned char)second;
	data1[16] = (unsigned char)first;

	unsigned char ret1[8];
	if (DEVICE_OK != CommandQuery(data1, sizeof(data1) / sizeof(unsigned char), ret1, sizeof(ret1) / sizeof(unsigned char)))
		return DEVICE_NOT_CONNECTED;

	unsigned char data2[] = { 0x01, 0x06, 0x00, 0x7D, 0x00, 0x08, 0x18, 0x14 };
	unsigned char ret2[8];
	if (DEVICE_OK != CommandQuery(data2, sizeof(data2) / sizeof(unsigned char), ret2, sizeof(ret2) / sizeof(unsigned char)))
		return DEVICE_NOT_CONNECTED;

	unsigned char data3[] = { 0x01, 0x06, 0x00, 0x7D, 0x00, 0x00, 0x19, 0xD2 };
	unsigned char ret3[8];
	if (DEVICE_OK != CommandQuery(data3, sizeof(data3) / sizeof(unsigned char), ret3, sizeof(ret3) / sizeof(unsigned char)))
		return DEVICE_NOT_CONNECTED;

	return OnStagePositionChanged(pos_um_);
}

int MicroZStage::GetPositionUm(double& pos)
{
	unsigned char data[] = { 0x01, 0x03, 0x00, 0xCC, 0x00, 0x02, 0x04, 0x34 };
	unsigned char ret[9];
	if (DEVICE_OK != CommandQuery(data, sizeof(data) / sizeof(unsigned char), ret, sizeof(ret) / sizeof(unsigned char)))
		return DEVICE_NOT_CONNECTED;

	double step_pos = ret[3] * 256 * 256 * 256.0 + ret[4] * 256 * 256.0 + ret[5] * 256.0 + ret[6];
	if (isSetZero_ && ret[3] == 255 && ret[4] == 255) {
		step_pos = 0.0;
		isSetZero_ = false;
	}

	unsigned int max_step = 0x7FFFFFFF;
	unsigned int max = 0xFFFFFFFF;
	if (step_pos > max_step)
		step_pos = step_pos - max;

	pos = step_pos * stepSize_um_;
	pos_um_ = pos;

	return DEVICE_OK;
}

double MicroZStage::GetStepSize()
{
	return stepSize_um_;
}

int MicroZStage::SetPositionSteps(long steps)
{
	pos_um_ = steps * stepSize_um_;
	return OnStagePositionChanged(pos_um_);
}

int MicroZStage::GetPositionSteps(long& steps)
{
	steps = (long)(pos_um_ / stepSize_um_);
	return DEVICE_OK;
}

int MicroZStage::SetOrigin()
{
	return DEVICE_OK;
}

int MicroZStage::GetLimits(double& lower, double& upper)
{
	lower = -4500000;
	upper = 4500000;
	return DEVICE_OK;
}

int MicroZStage::Move(double d)
{
	(void)d;
	return DEVICE_OK;
}

int MicroZStage::SetRelativePositionUm(double d)
{
	if (isJog_ == false) { // STEP mode
		if (busy_) {
			return DEVICE_OK;
		}

		d = d / stepSize_um_;

		if (isReverseDirection_) {
			d = -d;
		}

		std::vector<unsigned char> buf;

		if (d < 0) {
			int d_int = (int)d;

			std::bitset<32> d_bin(d_int);
			std::bitset<8> temp;
			for (int i = 0; i < 4; i++) {
				for (int j = 7; j >= 0; j--) {
					int idx = 32 - (8 - j) - i * 8;
					temp[j] = d_bin[idx];
				}

				buf.push_back((unsigned char)temp.to_ulong());
			}
		}
		else {
			unsigned char tmpBuf[4] = {0x00, 0x00, 0x00, 0x00};
			int temp = (int)d;
			int i = 0;
			while (temp > 0) {
				tmpBuf[i] = temp % 256;
				temp = temp / 256;

				i++;
			}

			int j = 0;
			for (int k = 3; k >= 0; k--) {
				buf.push_back((unsigned char)tmpBuf[k]);
				j++;
			}
		}

		// set relative position
		unsigned char data1[17] = { 0x01, 0x10, 0x18, 0x02, 0x00, 0x04, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0xE8, 0x00, 0x00 };
		data1[7] = buf[0];
		data1[8] = buf[1];
		data1[9] = buf[2];
		data1[10] = buf[3];

		// set velocity
		unsigned char vBuf[4] = { 0x00, 0x00, 0x00, 0x00 };
		int vk = 0;
		long vTemp = velocity_;
		while (vTemp > 0) {
			vBuf[vk++] = vTemp % 256;
			vTemp = vTemp / 256;
		}

		data1[11] = vBuf[3];
		data1[12] = vBuf[2];
		data1[13] = vBuf[1];
		data1[14] = vBuf[0];

		unsigned char temp1[15] = { 0 };
		for (int i = 0; i < 15; i++) {
			temp1[i] = data1[i];
		}

		// calculate CRC16 and appending 
		uint16_t crc161 = crc16_modbus(temp1, sizeof(temp1) / sizeof(unsigned char));
		uint16_t first = crc161 / 256;
		uint16_t second = crc161 % 256;
		data1[15] = (unsigned char)second;
		data1[16] = (unsigned char)first;

		unsigned char ret[8];
		if (DEVICE_OK != CommandQuery(data1, sizeof(data1) / sizeof(unsigned char), ret, sizeof(ret) / sizeof(unsigned char)))
			return DEVICE_NOT_CONNECTED;

		unsigned char data2[] = { 0x01, 0x06, 0x00, 0x7D, 0x00, 0x08, 0x18, 0x14 };
		unsigned char ret2[8];
		if (DEVICE_OK != CommandQuery(data2, sizeof(data2) / sizeof(unsigned char), ret2, sizeof(ret2) / sizeof(unsigned char)))
			return DEVICE_NOT_CONNECTED;

		unsigned char data3[] = { 0x01, 0x06, 0x00, 0x7D, 0x00, 0x00, 0x19, 0xD2 };
		unsigned char ret3[8];
		if (DEVICE_OK != CommandQuery(data3, sizeof(data3) / sizeof(unsigned char), ret3, sizeof(ret3) / sizeof(unsigned char)))
			return DEVICE_NOT_CONNECTED;
	}
	else { // JOG mode
		if (isJogRunning_ == false) {
			if (busy_) {
				return DEVICE_OK;
			}

			if (d > 100000) {
				return DEVICE_CAN_NOT_SET_PROPERTY;
			}

			d = d / stepSize_um_;

			if (isReverseDirection_) {
				d = -d;
			}

			std::vector<unsigned char> buf;

			if (d < 0) {
				int d_int = (int)d;

				std::bitset<32> d_bin(d_int);
				std::bitset<8> temp;
				for (int i = 0; i < 4; i++) {
					for (int j = 7; j >= 0; j--) {
						int idx = 32 - (8 - j) - i * 8;
						temp[j] = d_bin[idx];
					}

					buf.push_back((unsigned char)temp.to_ulong());
				}
			}
			else {
				unsigned char tmpBuf[4] = { 0x00, 0x00, 0x00, 0x00 };
				int temp = (int)d;
				int i = 0;
				while (temp > 0) {
					tmpBuf[i] = temp % 256;
					temp = temp / 256;

					i++;
				}

				int j = 0;
				for (int k = 3; k >= 0; k--) {
					buf.push_back((unsigned char)tmpBuf[k]);
					j++;
				}
			}

			// set relative position
			unsigned char data1[13] = { 0x01, 0x10, 0x04, 0x80, 0x00, 0x02, 0x04, 0x00, 0x00, 0x13, 0x88, 0xC4, 0x59 };
			data1[7] = buf[0];
			data1[8] = buf[1];
			data1[9] = buf[2];
			data1[10] = buf[3];

			// copy data1 to temp buffer
			unsigned char temp1[11] = { 0 };
			for (int i = 0; i < 11; i++) {
				temp1[i] = data1[i];
			}

			// calculate CRC16 and appending 
			uint16_t crc161 = crc16_modbus(temp1, sizeof(temp1) / sizeof(unsigned char));
			uint16_t first = crc161 / 256;
			uint16_t second = crc161 % 256;
			data1[11] = (unsigned char)second;
			data1[12] = (unsigned char)first;

			unsigned char data_res[8];
			if (DEVICE_OK != CommandQuery(data1, sizeof(data1) / sizeof(unsigned char), data_res, sizeof(data_res) / sizeof(unsigned char)))
				return DEVICE_NOT_CONNECTED;

			// JOR run
			unsigned char data_jog_run[8] = { 0x01, 0x06, 0x00, 0x7D, 0x40, 0x00, 0x28, 0x12 };
			unsigned char res[8];
			if (DEVICE_OK != CommandQuery(data_jog_run, sizeof(data_jog_run) / sizeof(unsigned char), res, sizeof(res) / sizeof(unsigned char)))
				return DEVICE_NOT_CONNECTED;

			isJogRunning_ = true;
		}
		else {
			unsigned char data1[8] = { 0x01, 0x06, 0x00, 0x7D, 0x00, 0x20, 0x18, 0x0A };
			unsigned char res1[8];
			if (DEVICE_OK != CommandQuery(data1, sizeof(data1) / sizeof(unsigned char), res1, sizeof(res1) / sizeof(unsigned char)))
				return DEVICE_NOT_CONNECTED;

			unsigned char data2[8] = { 0x01, 0x06, 0x00, 0x7D, 0x00, 0x00, 0x19, 0xD2 };
			unsigned char res2[8];
			if (DEVICE_OK != CommandQuery(data2, sizeof(data2) / sizeof(unsigned char), res2, sizeof(res2) / sizeof(unsigned char)))
				return DEVICE_NOT_CONNECTED;

			isJogRunning_ = false;
		}
	}

	return DEVICE_OK;
}

int MicroZStage::Home()
{
	unsigned char data_on[] = { 0x01, 0x06, 0x00, 0x7D, 0x00, 0x10, 0x18, 0x1E };
	unsigned char ret_data_on[8];
	if (DEVICE_OK != CommandQuery(data_on, sizeof(data_on) / sizeof(unsigned char), ret_data_on, sizeof(ret_data_on) / sizeof(unsigned char)))
		return DEVICE_NOT_CONNECTED;

	unsigned char data_off[] = { 0x01, 0x06, 0x00, 0x7D, 0x00, 0x00, 0x19, 0xD2 };
	unsigned char ret_data_off[8];
	if (DEVICE_OK != CommandQuery(data_off, sizeof(data_off) / sizeof(unsigned char), ret_data_off, sizeof(ret_data_off) / sizeof(unsigned char)))
		return DEVICE_NOT_CONNECTED;

	return DEVICE_OK;
}

bool MicroZStage::IsContinuousFocusDrive() const
{
	return false;
}

int MicroZStage::IsStageSequenceable(bool& isSequenceable) const
{
	isSequenceable = false;
	return DEVICE_OK;
}

int MicroZStage::GetStageSequenceMaxLength(long& nrEvents) const
{
	nrEvents = 0;
	return DEVICE_OK;
}

int MicroZStage::StartStageSequence()
{
	return DEVICE_OK;
}

int MicroZStage::StopStageSequence()
{
	return DEVICE_OK;
}

int MicroZStage::ClearStageSequence()
{
	return DEVICE_OK;
}

int MicroZStage::AddToStageSequence(double)
{
	return DEVICE_OK;
}

int MicroZStage::SendStageSequence()
{
	return DEVICE_OK;
}

int MicroZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	(void)eAct;
	(void)pProp;
	return DEVICE_OK;
}

int MicroZStage::OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	(void)eAct;
	(void)pProp;
	return DEVICE_OK;
}

int MicroZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(port_.c_str());
	}
	else if (eAct == MM::AfterSet) {
		if (initialized_) {
			pProp->Set(port_.c_str());
			return -1;
		}

		pProp->Get(port_);
	}

	return DEVICE_OK;
}

int MicroZStage::OnSetVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {

	}
	else if (eAct == MM::AfterSet) {
		long velocity;
		pProp->Get(velocity);

		if (velocity < 0 || velocity > 100000) {
			pProp->Set(velocity_);
			return DEVICE_CAN_NOT_SET_PROPERTY;
		}

		velocity_ = velocity;
	}

	return DEVICE_OK;
}

int MicroZStage::OnSetStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {

	}
	else if (eAct == MM::AfterSet) {
		double stepSize;
		pProp->Get(stepSize);

		stepSize_um_ = stepSize;
	}

	return DEVICE_OK;
}

int MicroZStage::OnReverseDirection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {

	}
	else if (eAct == MM::AfterSet) {
		std::string str;
		pProp->Get(str);
		if (str == "YES") {
			isReverseDirection_ = true;
		}
		else {
			isReverseDirection_ = false;
		}
	}

	return DEVICE_OK;
}

int MicroZStage::OnZHome(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {

	}
	else if (eAct == MM::AfterSet) {
		std::string str;
		pProp->Get(str);
		if (str == "YES") {

			if (busy_) {
				return DEVICE_CAN_NOT_SET_PROPERTY;
			}

			Home();
		}
	}

	return DEVICE_OK;
}

int MicroZStage::OnGoto(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {

	}
	else if (eAct == MM::AfterSet) {
		std::string str;
		pProp->Get(str);
		if (str == "YES") {

			if (busy_) {
				return DEVICE_CAN_NOT_SET_PROPERTY;
			}

			double pos_um_tmp;
			GetPositionUm(pos_um_tmp);

			double distance = goPos_ - pos_um_tmp;
			SetRelativePositionUm(distance);
		}
	}

	return DEVICE_OK;
}

int MicroZStage::OnGotoValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {

	}
	else if (eAct == MM::AfterSet) {
		double val;
		pProp->Get(val);

		goPos_ = val;
	}

	return DEVICE_OK;
}

int MicroZStage::OnSetZero(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {

	}
	else if (eAct == MM::AfterSet) {
		std::string str;
		pProp->Get(str);
		if (str == "YES") {

			if (busy_) {
				return DEVICE_CAN_NOT_SET_PROPERTY;
			}

			// set zero position
			unsigned char data1[] = { 0x01, 0x10, 0x01, 0x8A, 0x00, 0x02, 0x04, 0x00, 0x00, 0x00, 0x01, 0xB7, 0xE0 };
			unsigned char res1[8];
			if (DEVICE_OK != CommandQuery(data1, sizeof(data1) / sizeof(unsigned char), res1, sizeof(res1) / sizeof(unsigned char)))
				return DEVICE_NOT_CONNECTED;

			unsigned char data2[] = { 0x01, 0x10, 0x01, 0x8A, 0x00, 0x02, 0x04, 0x00, 0x00, 0x00, 0x00, 0x76, 0x20 };
			unsigned char res2[8];
			if (DEVICE_OK != CommandQuery(data2, sizeof(data2) / sizeof(unsigned char), res2, sizeof(res2) / sizeof(unsigned char)))
				return DEVICE_NOT_CONNECTED;

			isSetZero_ = true;
		}

		if (str == "NO") {
			isSetZero_ = false;
		}
	}

	return DEVICE_OK;
}

int MicroZStage::OnEStop(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {

	}
	else if (eAct == MM::AfterSet) {
		std::string str;
		pProp->Get(str);
		if (str == "YES") {
			unsigned char data1[] = { 0x01, 0x06, 0x00, 0x7D, 0x00, 0x20, 0x18, 0x0A };
			unsigned char res1[8];
			if (DEVICE_OK != CommandQuery(data1, sizeof(data1) / sizeof(unsigned char), res1, sizeof(res1) / sizeof(unsigned char)))
				return DEVICE_NOT_CONNECTED;

			unsigned char data2[] = { 0x01, 0x06, 0x00, 0x7D, 0x00, 0x00, 0x19, 0xD2 };
			unsigned char res2[8];
			if (DEVICE_OK != CommandQuery(data2, sizeof(data2) / sizeof(unsigned char), res2, sizeof(res2) / sizeof(unsigned char)))
				return DEVICE_NOT_CONNECTED;
		}
	}
	return DEVICE_OK;
}

bool MicroZStage::Busy()
{
	if (isJog_) {
		return false;
	}
	else
	{
		busy_ = false;

		if (!initialized_)
			return busy_;

		unsigned char data[] = { 0x01, 0x03, 0x01, 0x79, 0x00, 0x02, 0x14, 0x2E };
		unsigned char ret[9];
		if (DEVICE_OK != CommandQuery(data, sizeof(data) / sizeof(unsigned char), ret, sizeof(ret) / sizeof(unsigned char)))
			return false;

		//unsigned char state_slience[9] = { 0x01, 0x03, 0x04, 0xC4, 0x3C, 0x00, 0x00, 0x07, 0x0F };
		//unsigned char state_move[9] = { 0x01, 0x03, 0x04, 0x60, 0x6C, 0x00, 0x00, 0x24, 0x2E };

		if (ret[3] == 0x60 && (ret[4] == 0x6C || ret[4] == 0x4C)) {
			busy_ = true;
		}
		else {
			busy_ = false;
		}

		return busy_;
	}
}

int MicroZStage::CheckDeviceStatus()
{
	return DEVICE_OK;
}

int MicroZStage::SendData(unsigned char *data, unsigned int size)
{
	return WriteToComPort(port_.c_str(), data, size);
}
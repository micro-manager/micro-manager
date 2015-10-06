#pragma once

#include <DeviceBase.h>
#include <okolib.h>
#include <DeviceThreads.h>
#include <list>

class OkolabThread;

class OkolabDevice : public CGenericBase<OkolabDevice>
{
public:
	OkolabDevice(void);
	virtual ~OkolabDevice(void);

	bool isValid() const;
	// MMDevice API
	// ------------
	int Initialize();
	int Shutdown();

	void GetName(char* name) const;      

	virtual bool Busy() { LogMessage("Busy()", false); return _busy; }

	// device discovery (auto-configuration)
	//virtual MM::DeviceDetectionStatus DetectDevice(void);

	// Actions
	int OnOkolabPropertyChanged(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnLogToFileChanged(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPlaybackFileChanged(MM::PropertyBase* pProp, MM::ActionType eAct);
	
	int updateOkolabProperties(MM::MMTime startTime);

private:
	int initializeVersionAndDetectPorts();
	int initializeOkolabLibrary();
	int connectToPort(const std::string &port);
	int initializeAvailablePorts();
	int initializeVersionProperties();
	void createOkolabProperties();
	void AddOkolabProperty(uint32_t propertyIndex);

	void DetectPorts();
	int openAutoPort();
	int openDeviceIfNecessary();

	void propertyLock(const std::string &propertyName);
	void propertyUnlock(const std::string &propertyName);
	bool propertyLocked(const std::string &propertyName);

private:
	void LogOkolabError(oko_res_type err, std::string title);
	int translateError(oko_res_type ret);
	void createLoggerAndPlaybackFileProperties();
	std::string createPropertyError(oko_res_type ret);

protected:
	static bool _initialized;
	bool _busy;

	std::string _workingPort;
	std::string _module;
	uint32_t _deviceHandle;

	static std::vector<std::string> _ports;
	std::map<std::string, oko_module_type> _modules;

	friend class OkolabThread;
	OkolabThread * _okolabThread;
	long _timeBetweenUpdates;

	MMThreadLock _propertyUnderEditionLock;
	std::list<std::string> _propertiesUnderEdition;
	bool _isValid;
};

class OkolabThread : public MMDeviceThreadBase
{
	friend class OkolabDevice;
	enum { default_intervalMS = 100 };
public:
	OkolabThread(OkolabDevice* pDevice);
	virtual ~OkolabThread();
	void Stop();
	void Start(double intervalMs);
	bool IsStopped();
	void Suspend();
	bool IsSuspended();
	void Resume();
	double GetIntervalMs(){return _intervalMs;}
	MM::MMTime GetStartTime(){return _startTime;}
	MM::MMTime GetActualDuration(){return _actualDuration;}
private:                                                                     
	int svc(void) throw();
	double _intervalMs;
	bool _stop;
	bool _suspend;                                                            
	OkolabDevice* _device;
	MM::MMTime _startTime;
	MM::MMTime _actualDuration;
	MM::MMTime _lastFrameTime;
	MMThreadLock _stopLock;
	MMThreadLock _suspendLock;
};
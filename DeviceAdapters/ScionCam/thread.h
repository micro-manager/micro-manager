//////////////////////////////////////////////////////////////////////////////////////
//
//
//	sfwcore	- Scion Firewire Core Library
//
//	Version	1.0
//
//	Copyright 2008-2009  Scion Corporation  	(Win 2000/XP/Vista 32/64 Platforms)
//
//
//////////////////////////////////////////////////////////////////////////////////////

//////////////////////////////////////////////////////////////////////////////////////
//
//
//	File	thread.h
//
//	definitions for thread state class
//
//
//////////////////////////////////////////////////////////////////////////////////////

#if !defined(THREAD_H__INCLUDED_)
#define THREAD_H__INCLUDED_

#ifndef	DLLExport
#ifdef	_DLL
#define	DLLExport	__declspec (dllexport)
#else
#define	DLLExport	__declspec (dllimport)
#endif
#endif

// thread state definitions

#define	ts_null			0		// null state
#define	ts_running		1		// thread is in progress
#define	ts_complete		2		// thread terminated
#define	ts_stop_in_progress	3	// thread is processing stop request


// thread status definitions

#define	tcc_ok			0		// thread completed successfully
#define	tcc_error		1		// thread terminated by error condition
#define	tcc_abort		2		// thread abort from user request
#define	tcc_stop		3		// thread stopped from user request

class DLLExport Cthread_state
{
public:
	Cthread_state();

	virtual ~Cthread_state();

protected:
	volatile unsigned int	state;			// thread state
	volatile unsigned int	status;			// thread status

	volatile unsigned int	stop_request;	// stop request
	volatile unsigned int	abort_request;	// abort request

	volatile unsigned int	last_cc;		// last capture cc

	volatile unsigned int	active_status;	// 1 = thread is active

public:
	virtual	unsigned int	get_state();
	virtual	unsigned int	get_status();
	virtual	unsigned int	get_last_cc();

	virtual	bool			stop_requested();
	virtual	bool			abort_requested();
	virtual	bool			active();

	virtual	void			set_state(unsigned int state);
	virtual	void			set_status(unsigned int status);
	virtual	void			set_cc(unsigned int cc);
	
	virtual	void			clear_stop();
	virtual	void			clear_abort();
	virtual	void			set_inactive();

	virtual	void			set_stop();
	virtual	void			set_abort();
	virtual	void			set_active();
};


#endif // !defined(THREAD_H__INCLUDED_)

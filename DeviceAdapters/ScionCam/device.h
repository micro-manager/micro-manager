//////////////////////////////////////////////////////////////////////////////////////
//
//
//	sfwcore	- Scion Firewire Core Library
//
//	Version	1.0
//
//	Copyright 2004-2009  Scion Corporation  	(Win 2000/XP/Vista 32/64 Platforms)
//
//
//////////////////////////////////////////////////////////////////////////////////////

//////////////////////////////////////////////////////////////////////////////////////
//
//
//	File	device.h
//
//	definitions for camera device classes
//
//
//////////////////////////////////////////////////////////////////////////////////////

#if !defined(DEVICE_H__INCLUDED_)
#define DEVICE_H__INCLUDED_

#include "sfwlib.h"
#include "iformat.h"
#include "settings.h"
#include "buffers.h"

#ifndef	DLLExport
#ifdef	_DLL
#define	DLLExport	__declspec (dllexport)
#else
#define	DLLExport	__declspec (dllimport)
#endif
#endif

#ifdef __cplusplus
extern "C" {
#endif

class DLLExport Csfw_interface
{
public:
	Csfw_interface();

public:
	virtual	~Csfw_interface();

protected:
	static SFW_HANDLE		sfw_interface;

public:
	virtual	int				open();
	virtual void			close();
	
	static SFW_HANDLE	get_interface();

	static int		list(LPSFW_LIST_HANDLE lp);
	static int		dispose_object(LPSFW_OBJECT_HANDLE hp);

	static int		camera_vendor_desc(unsigned int type, unsigned int index, 
								char *desc, unsigned int size);
	static int		camera_vendor_prefix(unsigned int type, unsigned int index, 
								char *desc, unsigned int size);
	static int		camera_product_desc(unsigned int type, unsigned int index, 
								char *desc, unsigned int size);
	static int		camera_product_prefix(unsigned int type, unsigned int index, 
								char *desc, unsigned int size);
};


// camera device class
class DLLExport Cdevice
{
public:
	Cdevice();
	virtual	~Cdevice();

public:
	// image control data record definitions			

	#define	BIT_DEPTH_8		0x01	// supports 8-bit components
	#define	BIT_DEPTH_10	0x02	// supports 10-bit components
	#define	BIT_DEPTH_12	0x04	// supports 12-bit components
	#define BIT_DEPTH_14	0x08	// supports 14-bit components
	#define	BIT_DEPTH_16	0x10	// supports 16-bit components


protected:
	// fw camera id information structure

	typedef	struct	
		{
		unsigned int		type;		// device type
		unsigned int		index;		// occurance
		}	fw_id_t;

	fw_id_t			fw_id;				// fw id

	SFW_HANDLE		sfw_interface;		// fw interface
	SFW_CAMERA_HANDLE	pfw;			// fw camera handle

	unsigned int	serial_no;			// fw serial no
	unsigned int	camera_caps;		// camera capability flags
	unsigned int	camera_type;		// 0 = grayscale, 1 = color		
	unsigned int	camera_depth;		// 1 = 8-bit, 2 = 10-bit, 4 = 12-bit

	// currently color camera only do color images and grayscale on grayscale
	unsigned int	image_mode;			// 0 = grayscale, 1 = color

	unsigned int	image_width;		// image width for current mode
	unsigned int	image_height;		// image height for current mode

	unsigned int	max_width;			// max image width
	unsigned int	max_height;			// max image height
	unsigned int	bin_width;			// bin image width
	unsigned int	bin_height;			// bin image height
	unsigned int	preview_width;		// preview width
	unsigned int	preview_height;		// preview height

	unsigned int	max_depth;			// max depth supported by camera
										// (max component depth in bits)

	unsigned int	camera_mode;		// 0 = normal, 1 = bin, 2 = preview

	unsigned int	format;				// current format
	Ciformat		format_info;		// current format info

	Ccam_settings	default_config;		// camera default configuration

	Cbuffer_pool	bpool;				// buffer pool

public:
	virtual	int				open(unsigned int type, unsigned int index);
	virtual	int				open(unsigned int type, unsigned int index, unsigned int no_buffers);
	virtual	int				open_any(unsigned int &type, unsigned int &index);
	virtual	int				open_any(unsigned int &type, unsigned int &index, unsigned int no_buffers);
	virtual	void			close();

// utility functions
public:
	virtual	SFW_CAMERA_HANDLE	get_camera_handle() const;	// get camera handle to camera library

	virtual	unsigned int	get_camera_id() const;
	virtual	unsigned int	get_camera_index() const;
	virtual	unsigned int	get_serial_no() const;
	virtual	unsigned int	get_camera_caps() const;
	virtual	unsigned int	get_camera_type() const;
	virtual	unsigned int	get_camera_depths() const;
	virtual	unsigned int	get_image_mode() const;

	virtual	unsigned int	get_width() const;
	virtual	unsigned int	get_height() const;

	virtual	unsigned int	get_max_width() const;
	virtual	unsigned int	get_max_height() const;
	virtual	unsigned int	get_bin_width() const;
	virtual	unsigned int	get_bin_height() const;
	virtual	unsigned int	get_preview_width() const;
	virtual	unsigned int	get_preview_height() const;

	virtual	unsigned int	get_max_depth() const;

	virtual	Cbuffer_pool *	get_bpool();
	virtual	Cfw_image *		get_buffer(unsigned int buffer_no);
	virtual	sfw_frame_t *	get_frame_info(unsigned int buffer_no);

	virtual	unsigned int	get_format(unsigned int image_type, const Ccam_settings &config) const;

	virtual	void			set_bin_mode();
	virtual	void			set_preview_mode();
	virtual	void			set_normal_mode();

	virtual	bool			set_format(unsigned int format);
	virtual	bool			set_format(unsigned int image_type,
								unsigned int no_components,
								unsigned int component_depth,
								unsigned int component_order);

	virtual	bool			load_settings(const Ccam_settings &config);

protected:
	virtual	bool			validate_format(unsigned int format);


// library interface
public:
	virtual	int			setup_camera(DWORD options);

	virtual	int			assign_buffers(SFW_FRAME_HANDLE fp, DWORD no);
	virtual	int			release_buffers();

	virtual	int			start_frame_index(DWORD index);
	virtual	int			start_frame(SFW_FRAME_HANDLE fp); 

	virtual	int			check_for_complete();
	virtual	int			check_start_next(BOOLEAN start);
	virtual	int			abort_frame();

	virtual	int			get_info(unsigned int key, unsigned int *pvalue) const;

	virtual	int			get_param(unsigned int key, unsigned int *pvalue) const;
	virtual	int			get_param_min(unsigned int key, unsigned int *pvalue) const;
	virtual	int			get_param_max(unsigned int key, unsigned int *pvalue) const;
	virtual	int			get_param_default(unsigned int key, unsigned int *pvalue) const;
	virtual	int			set_param(unsigned int key, unsigned int value);

	virtual	int			get_fparam(unsigned int key, double *pvalue) const;
	virtual	int			get_fparam_min(unsigned int key, double *pvalue) const;
	virtual	int			get_fparam_max(unsigned int key, double *pvalue) const;
	virtual	int			get_fparam_default(unsigned int key, double *pvalue) const;
	virtual	int			set_fparam(unsigned int key, double value);

	virtual	int			get_stat(unsigned int key, unsigned int *pvalue) const;
	virtual	int			reset_stat(unsigned int key);

	virtual	int			copy_frame(Cframe_info &frame, Cfw_image &dest_image) const;

	virtual	int			copy_frame(Cframe_info &frame,
							unsigned int dformat, unsigned char *dp,
							RECT drect, unsigned int drowbytes) const;

	virtual	int			copy_frame(SFW_FRAME_HANDLE fp,
							unsigned int dformat, unsigned char *dp,
							RECT drect, unsigned int drowbytes) const;

	virtual	int			copy_frame(unsigned int buffer_no, 
							unsigned int dformat, unsigned char *dp,
							RECT drect, unsigned int drowbytes);

	virtual	int			copy_roi(SFW_FRAME_HANDLE fp,
							RECT roi, unsigned int dformat, unsigned char *dp, 
							RECT drect, unsigned int drowbytes) const;

	virtual int			copy_roi(unsigned int buffer_no,
							RECT roi, unsigned int dformat, unsigned char *dp, 
							RECT drect, unsigned int drowbytes);

	virtual	int			camera_vendor_desc(char *desc, unsigned int size) const;
	virtual	int			camera_vendor_prefix(char *desc, unsigned int size) const;
	virtual	int			camera_product_desc(char *desc, unsigned int size) const;
	virtual	int			camera_product_prefix(char *desc, unsigned int size) const;

	virtual	int			load_lut(unsigned char *lut, unsigned int lut_ch);
	virtual	int			get_lut(unsigned char *lut, unsigned int lut_ch);

	virtual	int			awb_roi_index(unsigned int index, LPSFW_AWBPROC awb_proc, 
							UINT_PTR user_data, RECT roi);
	virtual	int			awb_roi(SFW_FRAME_HANDLE fp, LPSFW_AWBPROC awb_proc, 
							UINT_PTR user_data, RECT roi);

	virtual int			auto_gain_index(unsigned int index, LPSFW_APRPROC apr_proc, 
							UINT_PTR user_data, unsigned int min, unsigned int max);
	virtual int			auto_gain(SFW_FRAME_HANDLE fp, LPSFW_APRPROC apr_proc, 
							UINT_PTR user_data, unsigned int min, unsigned int max);

	virtual int			auto_exp_index(unsigned int index, LPSFW_APRPROC apr_proc, 
							UINT_PTR user_data, unsigned int min, unsigned int max);
	virtual int			auto_exp(SFW_FRAME_HANDLE fp, LPSFW_APRPROC apr_proc, 
							UINT_PTR user_data, unsigned int min, unsigned int max);

	virtual int			auto_exp_index_ex(unsigned int index, LPSFW_APRPROC apr_proc, 
							UINT_PTR user_data, unsigned int min, unsigned int max, 
							UINT_PTR min_gain, unsigned int max_gain);
	virtual int			auto_exp_ex(SFW_FRAME_HANDLE fp, LPSFW_APRPROC apr_proc,
							UINT_PTR user_data, unsigned int min, unsigned int max, 
							UINT_PTR min_gain, unsigned int max_gain);

	virtual int			register_event_callback(unsigned int type, LPSFW_PROC proc, 
							unsigned int proc_type, UINT_PTR user_data);

	virtual int			remove_event_callback(unsigned int type);

	virtual int			exp_to_index(unsigned int exposure, unsigned int *index) const;
	virtual int			index_to_exp(unsigned int index, unsigned int *exposure) const;

	virtual int			gain_to_index(double gain, unsigned int *index) const;
	virtual int			index_to_gain(unsigned int index, double *gain) const;

	virtual int			bl_to_index(double bl, unsigned int *index) const;
	virtual int			index_to_bl(unsigned int index, double *bl) const;

	virtual int			wb_gain_to_index(double gain, unsigned int *index) const;
	virtual int			index_to_wb_gain(unsigned int index, double *gain) const;

	virtual int			gamma_to_index(double gamma, unsigned int *index) const;
	virtual int			index_to_gamma(unsigned int index, double *gamma) const;
};

DLLExport	Csfw_interface *	Create_sfw_interface();
DLLExport	Cdevice *	Create_device();

#ifdef __cplusplus
}
#endif

#endif // !defined(DEVICE_H__INCLUDED_)

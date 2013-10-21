#!/usr/bin/env python

"""
setup.py file for SWIG example
"""

from distutils.core import setup, Extension
import numpy.distutils.misc_util
import os

os.environ['CC'] = 'g++'
#os.environ['CXX'] = 'g++'
#os.environ['CPP'] = 'g++'
#os.environ['LDSHARED'] = 'g++'


mmcorepy_module = Extension('_MMCorePy',
                           sources=['MMCorePy_wrap.cxx',
			'../MMDevice/DeviceUtils.cpp',
			'../MMDevice/ImgBuffer.cpp', 
			'../MMDevice/Property.cpp', 
			'../MMCore/CircularBuffer.cpp',
			'../MMCore/Configuration.cpp',
			'../MMCore/CoreCallback.cpp',
			'../MMCore/CoreProperty.cpp',
			'../MMCore/FastLogger.cpp',
			'../MMCore/MMCore.cpp',
			'../MMCore/PluginManager.cpp'],
			language = "c++",
			extra_objects = [],
                            include_dirs=numpy.distutils.misc_util.get_numpy_include_dirs(),
                           )

setup (name = 'MMCorePy',
       version = '0.1',
       author      = "Micro-Manager",
       description = "Micro-Manager Core Python wrapper",
       ext_modules = [mmcorepy_module],
       py_modules = ["MMCorePy"],
       )


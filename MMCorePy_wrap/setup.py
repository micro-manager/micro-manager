#!/usr/bin/env python

"""
setup.py file for SWIG example
"""

from distutils.core import setup, Extension
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
                        include_dirs = ["/Developer/SDKs/MacOSX10.5.sdk/System/Library/Frameworks/Python.framework/Versions/2.5/Extras/lib/python/numpy/core/include/numpy"]
                           )

setup (name = 'MMCorePy',
       version = '0.1',
       author      = "SWIG Docs",
       description = """Simple swig example from docs""",
       ext_modules = [mmcorepy_module],
       py_modules = ["MMCorePy"],
       )


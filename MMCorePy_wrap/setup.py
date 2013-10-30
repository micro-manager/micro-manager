#!/usr/bin/env python

"""
This setup.py is intended for use from the Autoconf/Automake build system.
It makes a number of assumtions, including that the SWIG sources have already
been generated. Yet it also ignores settings detected by the configure script -
so it should probably be replaced with rules in the Makefile.am.
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
                                     '../MMCore/CircularBuffer.cpp',
                                     '../MMCore/Configuration.cpp',
                                     '../MMCore/CoreCallback.cpp',
                                     '../MMCore/CoreProperty.cpp',
                                     '../MMCore/FastLogger.cpp',
                                     '../MMCore/Host.cpp',
                                     '../MMCore/MMCore.cpp',
                                     '../MMCore/PluginManager.cpp',
                                     '../MMDevice/DeviceUtils.cpp',
                                     '../MMDevice/ImgBuffer.cpp',
                                    ],
                            language="c++",
                            include_dirs=numpy.distutils.misc_util.get_numpy_include_dirs(),
                           )

setup(name='MMCorePy',
      version='0.1',
      author="Micro-Manager",
      description="Micro-Manager Core Python wrapper",
      ext_modules=[mmcorepy_module],
      py_modules=["MMCorePy"],
     )

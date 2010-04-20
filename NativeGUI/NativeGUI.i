%module NativeGUIMac
%include std_string.i

%{
#include "NativeGUI.h"
%}

%include "NativeGUI.h"

%pragma(java) jniclasscode=%{
  static {
    try {
      System.loadLibrary("NativeGUI");
    } catch (UnsatisfiedLinkError e) {
      System.err.println("Native code library failed to load. \n" + e);
      System.exit(1);
    }
  }
%}

devenv /BUILD Release .\MMCorePy_wrap\MMCorePy_wrap.sln
copy .\bin_Win32\MMCorePy.py .\Install_Win32\micro-manager
copy .\bin_Win32\_MMCorePy.pyd .\Install_Win32\micro-manager
copy .\MMCorePy_wrap\MMCoreWrapDemo.py .\Install_Win32\micro-manager
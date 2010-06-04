devenv /REBUILD "Release|x64" .\MMCorePy_wrap\MMCorePy_wrap.sln
copy .\bin_x64\MMCorePy.py .\Install_x64\micro-manager
copy .\bin_x64\_MMCorePy.pyd .\Install_x64\micro-manager
copy .\MMCorePy_wrap\MMCoreWrapDemo.py .\Install\micro-manager
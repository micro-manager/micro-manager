devenv /REBUILD "Release|x64" .\MMCorePy_wrap\MMCorePy_wrap.sln
copy .\bin64\MMCorePy.py .\Install64\micro-manager
copy .\bin64\_MMCorePy.pyd .\Install64\micro-manager
copy .\MMCorePy_wrap\MMCoreWrapDemo.py .\Install\micro-manager
#!/usr/bin/env python3
# vim: se ts=4 sw=4 et sta :

import itertools
import sys
import xml.etree.ElementTree as ET


# Terminology for var names:
# e_* - XML element
# le_* - list(XML element)
# config - usu. Debug or Release
# platform - usu. Win32 or x64
# conplat - "Debug|Win32", etc.
# configdict - dict(setting -> value)
# setting - name of Configuration attribute or $(Tool name):$(Tool attributes)


def read_vcproj(filename):
    tree = ET.parse(filename)
    e_proj = tree.getroot()
    return read_proj(e_proj)


def read_proj(e_proj):
    """
    Return dict(config_name: config_settings).
    
    config_settings is a dict containing the (flattened) settings for the
    configuration.
    """
    le_configs = e_proj.findall('Configurations')
    assert len(le_configs) == 1
    le_config = le_configs[0].findall('Configuration')
    configdicts = dict()  # conplat -> configdict
    for e_config in le_config:
        conplat, configdict = read_config(e_config)
        configdicts[conplat] = configdict
    return configdicts


def read_config(e_config):
    """
    Return (conplat, configdict).
    """
    attrs = e_config.attrib.copy()
    conplat = attrs["Name"]
    del attrs["Name"]
    configdict = attrs

    le_tool = e_config.findall('Tool')
    for e_tool in le_tool:
        toolconfigdict = read_tool(e_tool)
        configdict.update(toolconfigdict)

    return conplat, configdict


def read_tool(e_tool):
    """
    Return (toolname, dict(attr -> value)).
    """
    attrs = e_tool.attrib.copy()
    toolname = attrs["Name"]
    del attrs["Name"]

    configdict = dict()
    for settingname, value in attrs.items():
        configdict["{}:{}".format(toolname, settingname)] = value

    return configdict


def check_conplat_consistency(configdicts):
    configs, platforms = set(), set()
    for conplat in configdicts.keys():
        config, platform = conplat.split("|")
        configs.add(config)
        platforms.add(platform)
    configs, platforms = sorted(configs), sorted(platforms)

    print("configurations:", ", ".join(sorted(configs)))
    print("platforms:", ", ".join(sorted(platforms)))
    print()

    all_settings = set()
    all_settings.update(*(configdict.keys()
                          for configdict in configdicts.values()))
    for setting in sorted(all_settings):
        check_setting_consistency(setting, configdicts, configs, platforms)


def check_setting_consistency(setting, configdicts, configs, platforms):
    conplat_values = dict() # conplat -> setting_value
    for conplat in (conplat_str(config, platform)
                    for config, platform
                    in itertools.product(configs, platforms)):
        conplat_values[conplat] = configdicts[conplat].get(setting)

    if len(set(conplat_values.values())) == 1:
        # All config-platforms have the same value for this setting.
        return

    # Inconsistencies can come in the following varieties:
    # 1) Different between configs but consistent across platforms
    # 2) Different between platforms but consistent across configs
    # 3) Different in just one config-platform from all others
    # 4) Other

    # Check if case 1) applies.
    standard_values = None
    for platform in platforms:
        values = tuple(conplat_values[conplat_str(config, platform)]
                       for config in configs)
        if standard_values is None:  # First platform
            standard_values = values
            continue
        if values != standard_values:
            break
    else:
        if setting in (
                       "VCCLCompilerTool:BasicRuntimeChecks",
                       "VCCLCompilerTool:MinimalRebuild",
                       "VCCLCompilerTool:Optimization",
                       "VCCLCompilerTool:RuntimeLibrary",
                       "VCLinkerTool:LinkIncremental",
                       "VCLinkerTool:OptimizeReferences",
                       "VCLinkerTool:EnableCOMDATFolding",
                       "VCLinkerTool:ProgramDatabaseFile",

                       # Inconsistent across device adapters:
                       "VCLinkerTool:GenerateDebugInformation",
                       "VCLinkerTool:LinkTimeCodeGeneration",
                       "VCCLCompilerTool:EnableFunctionLevelLinking",
                       "VCCLCompilerTool:EnableIntrinsicFunctions",
                       "WholeProgramOptimization",
                      ):
            return
        if (setting == "VCCLCompilerTool:DebugInformationFormat" and
            len(platforms) == 1 and platforms[0] == "Win32"):
            return

        if setting == "VCCLCompilerTool:PreprocessorDefinitions":
            if len(set(conplat_values[conplat_str(config,
                                                  platforms[0])].
                       replace(";_DEBUG;", ";NDEBUG;")
                       for config in configs)) == 1:
                return

        print(setting, ": inconsistent across configurations")
        for config in configs:
            conplat = conplat_str(config, platforms[0])
            print("    {:7s}: {}".
                  format(config, value_str(conplat_values[conplat])))
        return

    # Check if case 2) applies.
    if len(configs) > 1:
        standard_values = None
        for config in configs:
            values = tuple(conplat_values[conplat_str(config, platform)]
                           for platform in platforms)
            if standard_values is None:  # First config
                standard_values = values
                continue
            if values != standard_values:
                break
        else:
            if setting in (
                           "VCLinkerTool:TargetMachine",
                           "VCMIDLTool:TargetEnvironment",
                          ):
                return

            print(setting, ": inconsistent across platforms")
            for platform in platforms:
                conplat = conplat_str(configs[0], platform)
                print("    {:5s}: {}".
                      format(platform, value_str(conplat_values[conplat])))
            return

    # Check if case 3) applies.
    all_values = list(conplat_values.values())
    distinct_values = list(set(all_values))
    if len(distinct_values) == 2 and len(conplat_values) > 2:
        values = list(conplat_values.values())
        if values.count(distinct_values[0]) == 1:
            singular_value, standard_value = distinct_values
        elif values.count(distinct_values[1]) == 1:
            standard_value, singular_value = distinct_values
        else:
            return

        if setting in (
                       "VCCLCompilerTool:DebugInformationFormat",
                      ):
            return

        print(setting, ": differs in one configuration-platform")
        for conplat, value in conplat_values.items():
            if value == singular_value:
                print("    {:13s}: {}".
                      format(conplat, value_str(singular_value)))
                break
        print("    {:13s}: {}".format("all others", value_str(standard_value)))
        return

    # Case 4) applies.
    print(setting, ": inconsistent")
    for config in configs:
        for platform in platforms:
            conplat = conplat_str(config, platform)
            print("    {:13s}: {}".
                  format(conplat, value_str(conplat_values[conplat])))


def conplat_str(config, platform):
    return "{}|{}".format(config, platform)


def value_str(value):
    return (value if value is not None else "<missing>")


def main():
    configdicts = read_vcproj(sys.argv[1])
    check_conplat_consistency(configdicts)


if __name__ == "__main__":
    main()


# Copyright (c) 2013 University of California, San Francisco

import fnmatch
import os, os.path
import re
import sys

try:
    import vcxproj
except:
    import urllib.request
    response = urllib.request.urlopen("https://github.com/marktsuchida/vcxproj-stream-editor/raw/9aed52959ca8e7c97c8926b81eb42dcf654458d8/vcxproj.py")
    with open(os.path.join(os.path.dirname(os.path.relpath(__file__)), "vcxproj.py"), "wb") as mod_vcxproj:
        mod_vcxproj.write(response.read())
    import vcxproj


# Regular expression matching Condition attributes
def config_re(config=".+", platform=".+"):
    return r"^\s*'\$\(Configuration\)\|\$\(Platform\)'\s*==\s*'" + config + r"\|" + platform + r"'\s*$"


# Remove the spurious "Template" configurations that are sometimes generated
# during the conversion process.
# http://connect.microsoft.com/VisualStudio/feedback/details/540363/seemingly-spurious-template-configuration-created-by-conversion-wizard
@vcxproj.coroutine
def remove_spurious_template_configs(target):
    found, action, params = yield from vcxproj.skip_to(target, "Project")
    assert found
    target.send((action, params))

    found, action, params = yield from vcxproj.skip_to(target, "ItemGroup",
                                                       lambda a: a.get("Label") == "ProjectConfigurations")
    assert found
    target.send((action, params))

    while True:
        found, action, params = yield from vcxproj.skip_to(target, "ProjectConfiguration",
                                                           lambda a: a.get("Include", "").startswith("Template|"))
        if not found:
            target.send((action, params))  # end_elem ItemGroup
            break
        _, action, params = yield from vcxproj.skip_to(None)  # Discard ProjectConfiguration

    # From the remainder, discard any elements with a Condition attribute referencing Template|*.
    while True:
        action, params = yield
        if action == "start_elem" and re.match(config_re("Template"), params["attrs"].get("Condition", "")):
            _, action, params = yield from vcxproj.skip_to(None)
            continue
        target.send((action, params))


# Set the platform toolset (this cannot be done in property sheets)
@vcxproj.coroutine
def set_platform_toolset(target, toolset="Windows7.1SDK"):
    found, action, params = yield from vcxproj.skip_to(target, "Project")
    assert found
    target.send((action, params))

    while True:
        found, action, params = yield from vcxproj.skip_to(target, "PropertyGroup",
                                                           lambda a: a.get("Label") == "Configuration")
        target.send((action, params))  # start_elem PropertyGroup or end_elem Project
        if not found:
            break
        action, params = yield from vcxproj.set_content(target, "PlatformToolset", toolset)
        target.send((action, params))  # end_elem PropertyGroup

    while True:
        target.send((yield))


# Add a property sheet to the project
@vcxproj.coroutine
def add_prop_sheet(target, filename):
    found, action, params = yield from vcxproj.skip_to(target, "Project")
    assert found
    target.send((action, params))

    while True:
        found, action, params = yield from vcxproj.skip_to(target, "ImportGroup",
                                                           lambda a: a.get("Label") == "PropertySheets")
        target.send((action, params))  # start_elem ImportGroup or end_elem Project
        if not found:
            break
        found, action, params = yield from vcxproj.skip_to(target, "Import",
                                                           lambda a: a.get("Project").replace("\\", "/").strip('"') ==
                                                           filename.replace("\\", "/").strip('"'))
        assert not found, "Property sheet " + filename + " already imported"
        vcxproj.send_element(target, "Import", vcxproj.dict(Project=filename))
        target.send((action, params))  # end_elem ImportGroup

    while True:
        target.send((yield))


# Remove all occurrences of a particular tag
@vcxproj.coroutine
def remove_tag(target, tag_name_and_attr_test_seq):
    found, action, params = yield from vcxproj.skip_to(target, "Project")
    assert found
    target.send((action, params))
    action, params = yield from remove_tag_sub(target, tag_name_and_attr_test_seq)
    assert action == "end_elem"
    assert params["name"] == "Project"
    target.send((action, params))
    while True:
        target.send((yield))


@vcxproj.subcoroutine
def remove_tag_sub(target, tag_name_and_attr_test_seq):
    tag_name, attr_test = tag_name_and_attr_test_seq[0]
    further_levels = tag_name_and_attr_test_seq[1:]
    while True:
        found, action, params = yield from vcxproj.skip_to(target, tag_name, attr_test)
        if found:
            if further_levels:
                target.send((action, params))
                action, params = yield from remove_tag_sub(target, further_levels)
                assert action == "end_elem"
                assert params["name"] == tag_name
                target.send((action, params))
                continue
            found, action, params = yield from vcxproj.skip_to(None)
            continue
        return action, params


def genfilter_movetopropsheet(target):
    ret = target
    ret = remove_tag(ret, [("ItemDefinitionGroup", None), ("ClCompile", None), ("DebugInformationFormat", None)])
    ret = remove_tag(ret, [("ItemDefinitionGroup", None), ("ClCompile", None), ("WarningLevel", None)])
    ret = remove_tag(ret, [("ItemDefinitionGroup", None), ("Link", None), ("ProgramDatabaseFile", None)])
    ret = remove_tag(ret, [("ItemDefinitionGroup", None), ("Link", None), ("ImportLibrary", None)])
    ret = remove_tag(ret, [("ItemDefinitionGroup", None), ("Link", None), ("ShowProgress", None)])
    ret = remove_tag(ret, [("ItemDefinitionGroup", None), ("Link", None), ("GenerateDebugInformation", None)])
    ret = remove_tag(ret, [("ItemDefinitionGroup", None), ("Link", None), ("RandomizedBaseAddress", None)])
    ret = remove_tag(ret, [("ItemDefinitionGroup", None), ("Link", None), ("TargetMachine", None)])
    ret = remove_tag(ret, [("ItemDefinitionGroup", None), ("PreBuildEvent", None)])
    ret = remove_tag(ret, [("ItemDefinitionGroup", None), ("PostBuildEvent", None)])
    return ret


def genfilter_deviceadapter(target, proj_path):
    props_filename = ".\\buildscripts\\VisualStudio\\MMDeviceAdapter.props"
    props_filename = os.path.relpath(props_filename, proj_path)
    return genfilter_allprojects(add_prop_sheet(target,
                                                props_filename),
                                 proj_path)


def genfilter_allprojects(target, proj_path):
    props_filename = ".\\buildscripts\\VisualStudio\\MMCommon.props"
    props_filename = os.path.relpath(props_filename, proj_path)
    return remove_spurious_template_configs(set_platform_toolset(add_prop_sheet(genfilter_movetopropsheet(target),
                                                                                props_filename)))


def all_vcxprojs_in_dir(path):
    projs = list()
    for root, dirs, files in os.walk(path):
        for filename in files:
            if fnmatch.fnmatch(filename, "*.vcxproj"):
                filename = os.path.join(root, filename)
                projs.append(filename)
    return projs

if __name__ == "__main__":
    all_projects = all_vcxprojs_in_dir(".")
    device_adapters = all_vcxprojs_in_dir(".\\DeviceAdapters")
    secret_device_adapters = all_vcxprojs_in_dir(".\\SecretDeviceAdapters")
    test_device_adapters = all_vcxprojs_in_dir(".\\TestDeviceAdapters")

    def da_exception(da):
        non_da_projs = ["DEMessaging", "DEClientLib", "BFTest", "OlympusIX83Control"]
        for p in non_da_projs:
            if p in da:
                return True
        return False
    device_adapters = list(filter(lambda da: not da_exception(da), device_adapters))
    secret_device_adapters = list(filter(lambda da: not da_exception(da), secret_device_adapters))
    test_device_adapters = list(filter(lambda da: not da_exception(da), test_device_adapters))

    da_projects = sorted(set(device_adapters).union(set(secret_device_adapters)).union(set(test_device_adapters)))
    non_da_projects = sorted(set(all_projects) - set(da_projects))

    for p in non_da_projects:
        print(p)
        genfilter = lambda target: genfilter_allprojects(target, os.path.dirname(p))
        vcxproj.filter_file(p, genfilter, p)

    for p in da_projects:
        print(p)
        genfilter = lambda target: genfilter_deviceadapter(target, os.path.dirname(p))
        vcxproj.filter_file(p, genfilter, p)

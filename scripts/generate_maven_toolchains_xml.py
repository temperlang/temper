import click
from collections import namedtuple
from glob import iglob
from os import mkdir, environ
from os.path import expanduser, join, exists, dirname
import re, sys
from subprocess import check_call, check_output, STDOUT, CalledProcessError
import xml.etree.ElementTree as ET

Toolchain = namedtuple("Toolchain", ["versions", "vendor", "jdk_home"])

command_help = """\
This script updates the ~/.m2/toolchains.xml file for use with Maven.

\b
1. Reads the file, if present, otherwise creates an empty file.
2. Search for existing JDKs in common places.
3. Calls java -version for each existing JDK to infer the vendor and version.
4. Updates the file.

There's some code to drop existing entries if the `java` command fails, but it's disabled.
"""

command_epilog = """\
Example toolchains file.

\b
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  ...
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>VERSION</version>
      <vendor>VENDOR</vendor>
    </provides>
    <configuration>
      <jdkHome>JDKHOMEDIR</jdkHome>
    </configuration>
  </toolchain>
  ...
</toolchains>
"""


def minimal_toolchains():
    eb = ET.TreeBuilder()
    eb.start("toolchains", {})
    eb.end("toolchains")
    return eb.close()


def new_toolchain(toolchain):
    elems = []
    for version in toolchain.versions:
        eb = ET.TreeBuilder()
        eb.start("toolchain", {})
        eb.start("type", {})
        eb.data("jdk")
        eb.end("type")
        eb.start("provides", {})
        eb.start("version", {})
        eb.data(version)
        eb.end("version")
        eb.start("vendor", {})
        eb.data(toolchain.vendor)
        eb.end("vendor")
        eb.end("provides")
        eb.start("configuration", {})
        eb.start("jdkHome", {})
        eb.data(toolchain.jdk_home)
        eb.end("jdkHome")
        eb.end("configuration")
        eb.end("toolchain")
        elems.append(eb.close())
    return elems


def existing_toolchains(toolchains, drop_if_missing=False):
    """
    Find existing toolchains in the xml file and drop them if they're missing.
    """
    assert toolchains.tag == "toolchains"
    jdk_homes = set()
    drops = []
    for toolchain in toolchains:
        if toolchain.tag != "toolchain":
            continue
        tc_type = toolchain.find("type")
        if tc_type is None or tc_type.text != "jdk":
            continue
        jdk_home = toolchain.find("configuration/jdkHome")
        if jdk_home is None:
            continue
        jdk_home = jdk_home.text
        jdk_homes.add(jdk_home)
        if drop_if_missing and confirm_jdk(jdk_home) is None:
            drops.append(toolchain)
    for drop in drops:
        toolchains.remove(drop)
    return jdk_homes


def standard_locations():
    """
    Look in standard locations for JVMs.
    """
    java_home = environ.get("JAVA_HOME")
    if java_home:
        yield java_home

    if sys.platform == "darwin":
        yield from iglob("/Library/Java/JavaVirtualMachines/*/Contents/Home")
        yield from iglob(
            expanduser("~/Library/Java/JavaVirtualMachines/*/Contents/Home")
        )
    elif sys.platform == "win32":
        for pf_var in "ProgramFiles", "ProgramFiles(x86)", "ProgramW6432":
            pf = environ.get(pf_var)
            if pf:
                # Seems like many JVMs live elsewhere
                yield from iglob(join(pf, "Java", "*"))
    else:
        # Unix?
        yield from iglob("/usr/lib/jvm/*")
        yield from iglob("/usr/lib/java/*")


dot_exe = ".exe" if sys.platform == "win32" else ""
java_version_regex = re.compile(r'(\w+) version "([\d\.]+)')


def version_aliases(version):
    """
    Create proper aliases for JDK versions.
    """
    version_parts = version.split(".", 2)
    if version_parts[0] == "1":
        # For 1.2, etc, just keep the version number
        if len(version_parts) > 1:
            yield version_parts[1]
    elif int(version_parts[0]) < 9:
        # For x < 9, also emit 1.x
        yield "1." + version_parts[0]
    # Otherwise for major.minor.patch, include major, major.minor, major.minor.patch
    for idx in range(2, len(version_parts)):
        yield ".".join(version_parts[: idx + 1])


def confirm_jdk(jdk_home):
    java = join(jdk_home, "bin", "java" + dot_exe)
    javac = join(jdk_home, "bin", "javac" + dot_exe)
    if not exists(java) or not exists(javac):
        return None
    try:
        check_call([javac, "-version"])  # Make sure javac runs
        version_output = check_output([java, "-version"], stderr=STDOUT, text=True)
    except CalledProcessError:
        return None
    cmd_output = java_version_regex.search(version_output)
    if not cmd_output:
        return None
    # Before openjdk et al, the "vendor" field was just "java"; we don't try to correct that
    vendor = cmd_output.group(1)
    version = cmd_output.group(2)

    return Toolchain(
        versions=list(version_aliases(version)), vendor=vendor, jdk_home=jdk_home
    )


@click.command(
    short_help="Updates the ~/.m2/toolchains.xml file for use with Maven.",
    help=command_help,
    epilog=command_epilog,
)
@click.option("--path", "-p", type=click.Path(exists=False))
def main(path=None):
    if path is None:
        m2 = expanduser("~/.m2")
        toolchains_xml = join(m2, "toolchains.xml")
    else:
        toochains_xml = path
        m2 = dirname(path)
    try:
        tree = ET.ElementTree(file=toolchains_xml)
    except OSError:
        tree = ET.ElementTree(element=minimal_toolchains())
    root = tree.getroot()
    homes = existing_toolchains(root)
    for std_home in standard_locations():
        if std_home in homes:
            continue
        toolchain_info = confirm_jdk(std_home)
        if not toolchain_info:
            continue
        homes.add(toolchain_info.jdk_home)
        for elem in new_toolchain(toolchain_info):
            root.append(elem)
    if not exists(m2):
        mkdir(m2)
    ET.indent(tree, level=0)
    tree.write(toolchains_xml, xml_declaration=True, encoding="UTF8")

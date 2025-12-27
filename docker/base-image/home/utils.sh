# Helper functions, mostly for the installation process.

# Get the version (git tag) of ASDF to install.
get-asdf-version() {
    echo "${ASDF_VERSION:-v0.13.1}"
}

get-asdf-repo() {
    echo "${ASDF_REPO:-https://github.com/asdf-vm/asdf.git}"
}

# Get the version of the ASDF plugin manager plugin to install.
get-asdf-plugin-manager-tool-version() {
    local ver
    ver=$(awk '/^asdf-plugin-manager/ { print $2; exit 0 }' < "$HOME"/.tool-versions)
    echo "${ver:-1.1.1}"
}

get-asdf-plugin-manager-plugin-version() {
    local ver
    ver=$(awk '/^asdf-plugin-manager/ { print $3; exit 0 }' < "$HOME"/.plugin-versions)
    echo "${ver:-1.1.1}"
}

get-asdf-plugin-manager-plugin-repo() {
    local ver
    ver=$(awk '/^asdf-plugin-manager/ { print $2; exit 0 }' < "$HOME"/.plugin-versions)
    echo "${ver:-https://github.com/asdf-community/asdf-plugin-manager.git}"
}

# Install ASDF from scratch. To reconstruct a .asdf directory:
#   asdf-bootstrap
#   asdf-plugin-manager update-all
#   asdf install
#   post-asdf-helpers
asdf-bootstrap() {
    local asdf_repo asdf_version pm_tool_version pm_plugin_version pm_plugin_repo
    asdf_repo=$(get-asdf-repo) || return
    asdf_version=$(get-asdf-version) || return
    pm_plugin_repo=$(get-asdf-plugin-manager-plugin-repo)
    pm_plugin_version=$(get-asdf-plugin-manager-plugin-version) || return
    pm_tool_version=$(get-asdf-plugin-manager-tool-version) || return
    echo "bootstrap: Install asdf $asdf_version from $asdf_repo" &&
        git clone -c advice.detachedHead=false "$asdf_repo" ~/.asdf --branch "$asdf_version" &&
        echo "bootstrap: activate asdf" &&
        source "$HOME/.asdf/asdf.sh" &&
        echo "bootstrap: add asdf-plugin-manager" &&
        asdf plugin add asdf-plugin-manager "$pm_plugin_repo"  &&
        echo "bootstrap: update asdf-plugin-manager to version $pm_plugin_version" &&
        asdf plugin update asdf-plugin-manager "$pm_plugin_version" &&
        echo "bootstrap: install asdf-plugin-manager" &&
        asdf install asdf-plugin-manager "$pm_tool_version" &&
        echo "bootstrap: successful"
}

asdf-setup() {
    # LUAROCKS_EXTRA_CONFIGURE_OPTIONS='--with-lua'
    echo "asdf-setup: update plugins" &&
        asdf-plugin-manager add-all &&
        echo "asdf-setup: update tools" &&
        asdf install &&
        echo "asdf-setup: successful"
}

# After updating everything,
post-asdf-helpers() {
    echo "post-asdf-helpers: update .m2/toolchains.xml" &&
        mkdir -p "$HOME"/.m2 &&
        maven-generate-toolchains-xml > "$HOME"/.m2/toolchains.xml &&
        # TODO check if https://github.com/asdf-vm/asdf/issues/929 is fixed
        echo "post-asdf-helpers: add poetry" &&
        asdf install poetry 1.7.1 &&
        asdf global poetry 1.7.1
}

# Writes a toolchains.xml file to stdout.
# To update your .m2/toolchains.xml file if you add a different java version,
# just run this and redirect to ~/.m2/toolchains.xml
maven-generate-toolchains-xml() {
    local current_version jdk_home
    echo "<?xml version='1.0' encoding='UTF8'?>"
    echo "<toolchains>"
    asdf list java | while read current_version ; do
        [[ -n $current_version ]] || continue
        jdk_home=$(asdf where java "$current_version") || return
        "$jdk_home"/bin/java -XshowSettings:properties -version 2>&1 |
            awk -v jdkHome="$jdk_home" '
                BEGIN {
                    print "  <toolchain>"
                    print "    <type>jdk</type>"
                    print "    <provides>"
                }
                /java\.vendor = / {
                    split($0, a, " = ");
                    print "    <vendor>" a[2] "</vendor>"
                }
                /java\.specification\.version = / {
                    split($0, a, " = ");
                    if(a[2] == "1.8") {
                        print "    <version>8</version>"
                    } else {
                        print "    <version>" a[2] "</version>"
                    }
                }
                END {
                    print "    </provides>"
                    print "    <configuration>"
                    print "      <jdkHome>" jdkHome "</jdkHome>"
                    print "    </configuration>"
                    print "  </toolchain>"
                }
            '
    done
    echo "</toolchains>"
}

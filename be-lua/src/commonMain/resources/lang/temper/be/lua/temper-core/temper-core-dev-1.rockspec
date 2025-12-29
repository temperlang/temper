package = "temper-core"
-- File renamed on build to match version number.
version = "0.6.0-1"

description = {
    summary = "Runtime support for Temper-built Lua",
    detailed = "Authors: Temper Contributors",
    homepage = "https://github.com/temperlang/temper",
    license = "Apache-2.0 OR MIT"
}

build = {
    type = "builtin",
    copy_directories = {"."},
    modules = {}
}

dependencies = {"lua >= 5.1"}

source = {
    -- URL replaced on build to absolute path.
    url = "file://temper-core.zip"
}

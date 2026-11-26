package = "temper-core"
-- File renamed on build to match version number.
version = "0.0.0-1"

description = {
    summary = "Runtime support for Temper-built Lua",
    detailed = "Authors: Temper Contributors",
    homepage = "https://github.com/temperlang/temper",
    license = "Apache-2.0 OR MIT"
}

build = {
    type = "builtin",
    -- By the names generated code requires them by.
    modules = {
        ["temper-core"] = "init.lua",
        ["temper-core.intnew"] = "intnew.lua",
        ["temper-core.intold"] = "intold.lua",
        ["temper-core.powersort"] = "powersort/init.lua",
        ["temper-core.powersort.init-internal"] = "powersort/init-internal.lua",
        ["temper-core.regex.runtime"] = "regex/runtime.lua"
    }
}

dependencies = {"lua >= 5.1"}

source = {
    -- URL replaced on build to absolute path.
    url = "file://temper-core.zip"
}

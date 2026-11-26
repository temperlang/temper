package = 'asserts'
version = 'dev-1'
rockspec_format = '3.0'
description = {
}
build = {
    type = 'builtin',
    modules = {
        ['asserts'] = 'init.lua',
    },
}
dependencies = {
    'lua >= 5.1',
    'temper-core == 0.0.0.alpha.0.0',
}
test_dependencies = {
    'std == 0.0.0.alpha.0.0',
}
source = {
    url = 'file://asserts.zip',
}

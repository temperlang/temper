package = 'object-literals'
version = 'dev-1'
rockspec_format = '3.0'
description = {
}
build = {
    type = 'builtin',
    modules = {
        ['object-literals'] = 'init.lua',
        ['object-literals.extra-class'] = 'extra-class.lua',
    },
}
dependencies = {
    'lua >= 5.1',
    'temper-core == 0.0.0.alpha.0.0',
}
source = {
    url = 'file://object-literals.zip',
}

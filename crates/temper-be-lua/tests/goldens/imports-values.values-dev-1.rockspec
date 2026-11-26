package = 'values'
version = 'dev-1'
rockspec_format = '3.0'
description = {
}
build = {
    type = 'builtin',
    modules = {
        ['values'] = 'init.lua',
        ['values.exporter'] = 'exporter.lua',
    },
}
dependencies = {
    'lua >= 5.1',
    'temper-core == 0.0.0.alpha.0.0',
}
source = {
    url = 'file://values.zip',
}

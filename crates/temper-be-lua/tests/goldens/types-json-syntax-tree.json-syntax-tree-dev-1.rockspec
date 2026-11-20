package = 'json-syntax-tree'
version = 'dev-1'
rockspec_format = '3.0'
description = {
}
build = {
    type = 'builtin',
    modules = {
        ['json-syntax-tree'] = 'init.lua',
    },
}
dependencies = {
    'lua >= 5.1',
    'temper-core == 0.0.0.alpha.0.0',
    'std == 0.0.0.alpha.0.0',
}
source = {
    url = 'file://json-syntax-tree.zip',
}

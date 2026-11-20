package = 'modules'
version = 'dev-1'
rockspec_format = '3.0'
description = {
}
build = {
    type = 'builtin',
    modules = {
        ['modules'] = 'init.lua',
        ['modules.helpers.deep'] = 'helpers/deep.lua',
        ['modules.helpers'] = 'helpers.lua',
        ['modules.core'] = 'core.lua',
        ['modules.state'] = 'state.lua',
        ['modules.parts.regex'] = 'parts/regex.lua',
        ['modules.machine'] = 'machine.lua',
        ['modules.client'] = 'client.lua',
    },
}
dependencies = {
    'lua >= 5.1',
    'temper-core == 0.7.0.alpha.0.0',
    'std == 0.7.0.alpha.0.0',
}
source = {
    url = 'file://modules.zip',
}

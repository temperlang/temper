package = 'host-declared-errors-several-libraries'
version = 'dev-1'
rockspec_format = '3.0'
description = {
}
build = {
    type = 'builtin',
    copy_directories = {'.'},
    modules = {},
}
dependencies = {
    'lua >= 5.1',
    'temper-core == 0.0.0.alpha.0.0',
    'count-errors',
}
source = {
    url = 'file://host-declared-errors-several-libraries.zip',
}

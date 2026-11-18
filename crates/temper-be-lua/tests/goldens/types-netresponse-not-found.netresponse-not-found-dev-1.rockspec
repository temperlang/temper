package = 'netresponse-not-found'
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
    'std == 0.0.0.alpha.0.0',
}
source = {
    url = 'file://netresponse-not-found.zip',
}

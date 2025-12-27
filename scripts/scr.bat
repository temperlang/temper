@echo off
:: Convenience to run a python script under this (the scripts) directory.
:: Requires `poetry` be installed and available in the path.

SET script_dir=%~dp0
if "%~1"=="" (
    poetry run -C "%script_dir%" help
) else (
    poetry run -C "%script_dir%" %*
)

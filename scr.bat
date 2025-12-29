:: Convenience to run a python script under the scripts directory.
:: Requires `poetry` be installed and available in the path.

SET script_dir=%~dp0
if "%~1"=="" (
    poetry run -C "%script_dir%scripts" help
) else (
    poetry run -C "%script_dir%scripts" %*
)

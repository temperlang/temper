# A specification: `NotFound` is the library's own failure class, in the
# module this code joins, as the Python backend names it.
class _connected:
    def host_lookup(key: str) -> int:
        if key == "bug":
            raise RuntimeError("host bug")
        if key == "a":
            return 1
        raise NotFound(key)  # noqa: F821

import re
import typing


def regex_compiled_find(_, compiled: re.Pattern, text: str, begin: int, regex_refs):
    match = compiled.search(text, begin)
    if match is None:
        raise RuntimeError()
    return _convert_match(match, regex_refs)


def _convert_match(match, regex_refs):
    Match = regex_refs.match_.__class__
    Group = regex_refs.match_.full.__class__
    groups = match.groupdict()
    # Python indices are already in code points.
    full = Group("full", match.group(), match.start(), match.end())
    result_groups = {}
    for name, value in groups.items():
        if value is not None:
            begin = match.start(name)
            # Presume string len is faster than end lookup by name.
            result_groups[name] = Group(name, value, begin, begin + len(value))
    return Match(full, result_groups)


def regex_compiled_found(_, compiled: re.Pattern, text: str):
    return compiled.search(text) is not None


def regex_compiled_replace(
    _,
    compiled: re.Pattern,
    text: str,
    # Should be Match instead of Any, but no temper_std access here. :(
    # TODO Put this helper code under std.
    format: typing.Callable[[typing.Any], str],
    regex_refs,
):
    def adapted_format(match):
        return format(_convert_match(match, regex_refs))

    return compiled.sub(adapted_format, text)


def regex_compiled_split(_, compiled: re.Pattern, text: str, regex_refs):
    return compiled.split(text)


def regex_compile_formatted(_, formatted: str):
    return re.compile(formatted, re.ASCII)


def regex_formatter_push_capture_name(_, out: typing.MutableSequence[str], name: str):
    out.append(rf"?P<{name}>")


def regex_formatter_push_code_to(
    _, out: typing.MutableSequence[str], code: int, insideCodeSet: bool
):
    # Ignore insideCodeSet for now.
    # TODO(tjp, regex): Get fancier, including with work in Temper.
    out.append(rf"\U{code:08x}")

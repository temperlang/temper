from collections.abc import Sequence as _Sequence
from temper_core import (
    Bubble as _Bubble,
    LabelPair as _LabelPair,
    LoggingConsole as _LoggingConsole,
    ParseError as _ParseError,
    boolean_to_string as _boolean_to_string,
    int_add as _int_add,
    int_mul as _int_mul,
    list_get as _list_get,
    string_split as _string_split,
    string_to_int32 as _string_to_int32,
)

__all__ = ()


def _step(n: int, /) -> int:
    global _steps
    _steps = _int_add(_steps, 1)
    _console.log(f'step {n}')
    return n


def _pair(a: int, b: int, /) -> int:
    return _int_add(_int_mul(a, 10), b)


def _parse(s: str, /) -> int:
    try:
        return _string_to_int32(s)
    except _ParseError:
        raise _Bubble()


def _next(y: int | None, /) -> int | None:
    x: int
    if y is None:
        return None
    else:
        x = y
    return _int_add(x, 1)


def _sum_present(ys: _Sequence[int | None], /) -> int:
    sum = 0
    i = 0
    while i < len(ys):
        t: int
        subject: int | None = _list_get(ys, i)
        if subject is None:
            i = _int_add(i, 1)
            continue
        else:
            t = subject
        sum = _int_add(sum, t)
        i = _int_add(i, 1)
    return sum


def _sum_until_bad(words: _Sequence[str], /) -> int:
    sum = 0
    i = 0
    while i < len(words):
        t: int
        held: int | None = None
        try:
            held = _parse(_list_get(words, i))
        except _Bubble:
            pass
        if held is None:  # pyright: ignore[reportUnnecessaryComparison]
            break
        else:
            t = held
        sum = _int_add(sum, t)
        i = _int_add(i, 1)
    return sum


def _parsed_or(s: str, /) -> int:
    t: int
    held: int | None = None
    try:
        held = _parse(s)
    except _Bubble:
        pass
    if held is None:  # pyright: ignore[reportUnnecessaryComparison]
        return -1
    else:
        t = held
    return t


def _after(i: int, /) -> int | None:
    if i < 3:
        return _int_add(i, 1)
    else:
        return None


def _stepped() -> str:
    seen = ''
    i = 0
    while i < 6:
        if i == 1:
            i = 9 if (subject := _after(i)) is None else subject
            continue
        seen = seen + str(i)
        i = 9 if (subject := _after(i)) is None else subject
    return seen


def _paired(y: int | None, /) -> int:
    t_0 = _step(1)
    t: int
    if y is None:
        return -2
    else:
        t = y
    return _pair(t_0, t)


def _listed(y: int | None, z: int | None, /) -> _Sequence[int]:
    t: int
    if y is None:
        return ()
    else:
        t = y
    t2: int
    if z is None:
        return (9,)
    else:
        t2 = z
    return (1, t, 3, t2)


def _shown(y: int | None, /) -> str:
    t: int
    if y is None:
        return 'none'
    else:
        t = y
    return f'y={t}'


def _both(t: bool, y: int | None, /) -> str:
    ok: bool
    if t and y is not None:
        ok = True
    else:
        return 'no'
    return f'ok {_boolean_to_string(ok)}'


def _sign(n: int, /) -> str:
    t: int
    if n > 0:
        t = 1
    elif n < 0:
        t = -1
    else:
        return 'zero'
    return str(_pair(0, t))


def _first_word(text: str | None, /) -> None:
    t: str
    if text is None:
        return
    else:
        t = text
    _console.log(f"first {_list_get(_string_split(t, ' '), 0)}")


def _early() -> int:
    return 7


def _partly() -> int:
    _step(2)
    return _step(3)


def _do_jumps(ys: _Sequence[int | None], /) -> str:
    seen = ''
    i = 0
    while i < len(ys):
        v: int
        subject: int | None = _list_get(ys, i)
        if subject is None:
            seen = f'{seen}a'
            if i == 1:
                i = _int_add(i, 1)
                continue
            seen = f'{seen}b'
            break
        else:
            v = subject
        seen = seen + str(v)
        i = _int_add(i, 1)
    return seen


def _do_return(y: int | None, z: int, /) -> str:
    t_0: int
    if y is None:
        if z > 0:
            if z > 5:
                return 'big'
            return 'pos'
        t_0 = 0
    else:
        t_0 = y
    v = t_0
    return f'v{v}'


def _trio(a: int, b: int, c: int, /) -> int:
    return _int_add(_int_add(a, b), c)


def _ordered(ys: _Sequence[int | None], /) -> int:
    sum = 0
    i = 0
    while i < len(ys):
        t_0 = _step(_int_add(10, i))
        t: int
        subject: int | None = _list_get(ys, i)
        if subject is None:
            _step(_int_add(20, i))
            i = _int_add(i, 1)
            continue
        else:
            t = subject
        sum = _int_add(sum, _trio(t_0, t, _step(_int_add(30, i))))
        i = _int_add(i, 1)
    return sum


def _nested(xs: _Sequence[int | None], ys: _Sequence[int | None], /) -> str:
    seen = ''
    i = 0
    while i < len(xs):
        v: int
        subject: int | None = _list_get(xs, i)
        if subject is None:
            subject2: int | None = _list_get(ys, i)
            if subject2 is None:
                break
            else:
                v = subject2
        else:
            v = subject
        seen = seen + str(v)
        i = _int_add(i, 1)
    return seen


def _gridded(rows: _Sequence[_Sequence[int | None]], /) -> str:
    seen = ''
    i = 0
    with _LabelPair() as outer:
        while i < len(rows):
            with outer.continuing:
                row = _list_get(rows, i)
                j = 0
                while j < len(row):
                    v: int
                    subject: int | None = _list_get(row, j)
                    if subject is None:
                        outer.break_()
                    else:
                        v = subject
                    w: int
                    subject = None if v < 0 else v
                    if subject is None:
                        seen = f'{seen}-'
                        i = _int_add(i, 1)
                        outer.continue_()
                    else:
                        w = subject
                    seen = seen + str(w)
                    j = _int_add(j, 1)
                seen = f'{seen}|'
                i = _int_add(i, 1)
    return seen


def _first_big(rows: _Sequence[_Sequence[int | None]], keys: _Sequence[int | None], /) -> str:
    seen = ''
    i = 0
    with _LabelPair() as outer_1:
        while i < len(rows):
            with outer_1.continuing:
                t_0: int
                if (subject := _list_get(keys, i)) is None:
                    row = _list_get(rows, i)
                    first = -1
                    j = 0
                    while j < len(row):
                        v: int
                        subject2: int | None = _list_get(row, j)
                        if subject2 is None:
                            i = _int_add(i, 1)
                            outer_1.continue_()
                        else:
                            v = subject2
                        if v > 5:
                            outer_1.break_()
                        if v > 2:
                            first = v
                            break
                        j = _int_add(j, 1)
                    t_0 = first
                else:
                    t_0 = subject
                k = t_0
                seen = f'{seen}{k},'
                i = _int_add(i, 1)
    return seen


def _via_orelse(words: _Sequence[str], /) -> str:
    seen = ''
    i = 0
    while i < len(words):
        n: int
        held: int | None = None
        try:
            held = _parse(_list_get(words, i))
        except _Bubble:
            pass
        if held is None:  # pyright: ignore[reportUnnecessaryComparison]
            seen = f'{seen}!'
            if i > 2:
                break
            i = _int_add(i, 1)
            continue
        else:
            n = held
        seen = seen + str(n)
        i = _int_add(i, 1)
    return seen


def _via_if(n: int, /) -> str:
    t_0: str
    if n > 0:
        t_0 = 'pos'
    else:
        if n < -5:
            return 'low'
        t_0 = 'neg'
    s = t_0
    return f's={s}'


_console: _LoggingConsole = _LoggingConsole(__name__)
_steps: int = 0
_console.log(f"{'null' if (_part := _next(1)) is None else str(_part)} {'null' if (_part2 := _next(None)) is None else str(_part2)}")
_console.log(f"{_sum_present((1, None, 3))} {_sum_until_bad(('1', '2', 'x', '4'))}")
_console.log(f"{_parsed_or('12')} {_parsed_or('x')} {_stepped()}")
_console.log(f'{_paired(3)} {_paired(None)}')
_console.log(f'{len(_listed(2, 4))} {len(_listed(None, 4))} {_list_get(_listed(2, None), 0)}')
_console.log(f'{_shown(5)} {_shown(None)}')
_console.log(f'{_both(True, 1)} {_both(True, None)} {_both(False, 1)}')
_console.log(f'{_sign(4)} {_sign(-4)} {_sign(0)}')
_first_word('hello there')
_first_word(None)
_console.log(f'{_do_jumps((1, None, 2, None, 3))} {_do_return(1, 0)} {_do_return(None, 9)} {_do_return(None, 1)} {_do_return(None, 0)}')
_console.log(str(_ordered((1, None))))
_console.log(f"{_nested((1, None, None, 4), (0, 7, None, 0))} {_via_orelse(('1', 'x', '2', 'y', 'z', '3'))}")
_console.log(f'{_gridded(((1, 2), (3, -1, 4), (5, None, 6), (7,)))} {_first_big(((1, 3), (None, 1), (4,), (9,), (1,)), (7, None, None, None, None))}')
_console.log(f'{_via_if(1)} {_via_if(-1)} {_via_if(-9)}')
_console.log(f'{_early()} {_partly()} {_steps}')

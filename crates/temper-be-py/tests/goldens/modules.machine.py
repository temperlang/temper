from modules.parts.regex import wheels as _wheels
from modules.state import (
    Engine as _Engine_,
    Parts as _Parts,
    State as _State,
    first_id as _first_id,
)
from temper_core import int_add as _int_add
from temper_std.regex import dot as _dot, one_or_more as _one_or_more

__all__ = ('describe',)


def describe() -> str:
    state = _State(_first_id)
    engine = _Engine_(_wheels())
    least = _one_or_more(_dot).min
    parts = _Parts(_int_add(engine.n, least))
    return f'state {state.id} engine {engine.n} parts {parts.count}'

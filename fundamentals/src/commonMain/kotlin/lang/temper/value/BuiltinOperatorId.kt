package lang.temper.value

/**
 * Associated with [NamedBuiltinFun]s, to let a backend easily distinguish operators that correspond
 * to builtins on many backends.
 */
enum class BuiltinOperatorId {
    BooleanNegation,
    IsNull,
    NotNull,

    BitwiseAnd32,
    BitwiseAnd64,
    BitwiseOr32,
    BitwiseOr64,
    BitwiseXor32,
    BitwiseXor64,
    BitwiseNegation32,
    BitwiseNegation64,
    BitwiseShl32,
    BitwiseShl64,
    BitwiseShr32,
    BitwiseShr64,
    BitwiseShrUnsigned32,
    BitwiseShrUnsigned64,

    DivFltFlt,
    DivIntInt,
    DivIntInt64,

    /** Like [DivIntInt] but the right operand is known to be non-zero */
    DivIntIntSafe,
    DivIntInt64Safe,
    ModFltFlt,
    ModIntInt,
    ModIntInt64,

    /** Like [ModIntInt] but the right operand is known to be non-zero */
    ModIntIntSafe,
    ModIntInt64Safe,
    MinusFlt,
    MinusFltFlt,
    MinusInt,
    MinusInt64,
    MinusIntInt,
    MinusIntInt64,
    PlusFltFlt,
    PlusIntInt,
    PlusIntInt64,
    TimesIntInt,
    TimesIntInt64,
    TimesFltFlt,
    PowFltFlt,
    LtFltFlt,
    LtIntInt,
    LtStrStr,
    LtGeneric,
    LeFltFlt,
    LeIntInt,
    LeStrStr,
    LeGeneric,
    GtFltFlt,
    GtIntInt,
    GtStrStr,
    GtGeneric,
    GeFltFlt,
    GeIntInt,
    GeStrStr,
    GeGeneric,
    EqFltFlt,
    EqIntInt,
    EqStrStr,
    EqGeneric,
    NeFltFlt,
    NeIntInt,
    NeStrStr,
    NeGeneric,
    CmpFltFlt,
    CmpIntInt,
    CmpStrStr,
    CmpGeneric,
    Bubble,
    Panic,
    Print,
    StrCat,
    Listify,

    // Coroutine related
    AdaptGeneratorFn,
    SafeAdaptGeneratorFn,

    // Asyncrony related
    Async,

    // Related to unpacking result types on backends that do not
    // treat bubbles as throwable exceptions.
    IsOkResult,
    PackOkResult,
    UnpackOkResult,
}

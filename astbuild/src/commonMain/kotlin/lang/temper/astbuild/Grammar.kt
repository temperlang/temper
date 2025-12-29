package lang.temper.astbuild

import lang.temper.ast.CstToken
import lang.temper.ast.FinishTree
import lang.temper.ast.FinishedTreeType
import lang.temper.ast.FinishedType
import lang.temper.ast.LeftParenthesis
import lang.temper.ast.NamePart
import lang.temper.ast.RightParenthesis
import lang.temper.ast.ShiftLeft
import lang.temper.ast.SoftBlock
import lang.temper.ast.SoftComma
import lang.temper.ast.StartTree
import lang.temper.ast.TokenLeaf
import lang.temper.ast.ValuePart
import lang.temper.astbuild.ProductionNames.`(`
import lang.temper.astbuild.ProductionNames.`)`
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.BuiltinFuns.vPostfixApply
import lang.temper.common.Either
import lang.temper.common.LeftOrRight
import lang.temper.common.LeftOrRight.Left
import lang.temper.common.LeftOrRight.Right
import lang.temper.common.decodeUtf16
import lang.temper.cst.extendsLikeOperators
import lang.temper.lexer.IdParts
import lang.temper.lexer.Operator
import lang.temper.lexer.OperatorType
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.lexer.closeBrackets
import lang.temper.lexer.reservedWords
import lang.temper.log.MessageTemplate
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.name.decodeName
import lang.temper.value.InnerTreeType
import lang.temper.value.LeafTreeType
import lang.temper.value.TBoolean
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.TreeType
import lang.temper.value.Value
import lang.temper.value.asSymbol
import lang.temper.value.callJoinSymbol
import lang.temper.value.caseCaseSymbol
import lang.temper.value.caseIsSymbol
import lang.temper.value.caseSymbol
import lang.temper.value.catBuiltinName
import lang.temper.value.chainNullBuiltinName
import lang.temper.value.complexArgSymbol
import lang.temper.value.condSymbol
import lang.temper.value.curliesBuiltinName
import lang.temper.value.defaultSymbol
import lang.temper.value.dotBuiltinName
import lang.temper.value.flowInitSymbol
import lang.temper.value.forbidsSymbol
import lang.temper.value.holeBuiltinName
import lang.temper.value.incrSymbol
import lang.temper.value.initSymbol
import lang.temper.value.interpolateSymbol
import lang.temper.value.labelSymbol
import lang.temper.value.ofBuiltinName
import lang.temper.value.outTypeSymbol
import lang.temper.value.quasiInnerBuiltinName
import lang.temper.value.quasiLeafBuiltinName
import lang.temper.value.regexLiteralBuiltinName
import lang.temper.value.reifiesSymbol
import lang.temper.value.squaresBuiltinName
import lang.temper.value.superSymbol
import lang.temper.value.supportsSymbol
import lang.temper.value.surpriseMeSymbol
import lang.temper.value.throwsBuiltinName
import lang.temper.value.typeArgSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.unholeBuiltinName
import lang.temper.value.vPostponedCaseMacro
import lang.temper.value.void
import lang.temper.value.wordSymbol

/** Production names as symbols. */
private object ProductionNames : DomainSpecificLanguage() {
    // Allows annotations to apply to a comma separated group of declarators.
    val Arg = Ref("Arg")
    val ArgNoInit = Ref("ArgNoInit")
    val Args = Ref("Args") // Cover grammar for both actual and formal arguments

    val BlockLambda = Ref("BlockLambda")
    val BlockLambdaBody = Ref("BlockLambdaBody")
    val BlockLambdaSignature = Ref("BlockLambdaSignature")
    val BlockLambdaSignatureAndSupers = Ref("BlockLambdaSignatureAndSupers")
    val BlockLambdaSupers = Ref("BlockLambdaSupers")

    val Call = Ref("Call")
    val CallArgs = Ref("CallArgs")
    val CallHead = Ref("CallHead")
    val CallJoiningWords = Ref("CallJoiningWords")
    val CallTail = Ref("CallTail")
    val Callee = Ref("Callee")
    val CalleeAndArgs = Ref("CalleeAndArgs")
    val CalleeAndRequiredArgs = Ref("CalleeAndRequiredArgs")
    val DecoratedLet = Ref("DecoratedLet")
    val DecoratedLetBody = Ref("DecoratedLetBody")
    val DecoratedTopLevel = Ref("DecoratedTopLevel")
    val MatchBranch = Ref("MatchBranch")
    val MatchCase = Ref("MatchCase")
    val BalancedTokenList = Ref("BalancedTokenList")
    val CommaEl = Ref("CommaEl")
    val CommaExpr = Ref("CommaExpr")
    val CommaOp = Ref("CommaOp")
    val DeclDefault = Ref("DeclDefault")
    val DeclInit = Ref("DeclInit")
    val DeclMulti = Ref("DeclMulti")
    val DeclMultiNamed = Ref("DeclMultiNamed")
    val DeclMultiNested = Ref("DeclMultiNested")
    val DeclName = Ref("DeclName")
    val DeclReifies = Ref("DeclReifies")
    val DeclType = Ref("DeclType")
    val DeclTypeNested = Ref("DeclTypeNested")
    val EmbeddedComment = Ref("EmbeddedComment")

    val Expr = Ref("Expr")

    val FailureMode = Ref("FailureMode")
    val FailureModeSecondary = Ref("FailureModeSecondary")
    val ForArgs = Ref("ForArgs")
    val ForCond = Ref("ForCond")
    val ForIncr = Ref("ForIncr")
    val ForInit = Ref("ForInit")
    val ForInitLet = Ref("ForInitLet")
    val FormalNoInit = Ref("FormalNoInit")
    val Formal = Ref("Formal")
    val Formals = Ref("Formals")
    val Id = Ref("Id")
    val Infix = Ref("Infix")
    val InfixOp = Ref("InfixOp")
    val Jump = Ref("Jump")
    val Label = Ref("Label")
    val LabelOrHole = Ref("LabelOrHole")
    val LabeledStmt = Ref("LabeledStmt")
    val LeftLabel = Ref("LeftLabel")
    val Let = Ref("Let")
    val LetArg = Ref("LetArg")
    val LetBody = Ref("LetBody")
    val LetNested = Ref("LetNested")
    val LetRest = Ref("LetRest")
    val List = Ref("List")
    val ListContent = Ref("ListContent")
    val ListElement = Ref("ListElement")
    val ListElements = Ref("ListElements")
    val ListHole = Ref("ListHole")
    val Literal = Ref("Literal")
    val Member = Ref("Member")
    val New = Ref("New")
    val NoPropClass = Ref("NoPropClass")

    val Nop = Ref("Nop")
    val OfExpr = Ref("OfExpr")
    val Obj = Ref("Obj")
    val Pattern = Ref("Pattern")
    val Postfix = Ref("Postfix")
    val PostfixOp = Ref("PostfixOp")
    val Prefix = Ref("Prefix")
    val PrefixOp = Ref("PrefixOp")
    val Prop = Ref("Prop")
    val PropName = Ref("PropName")
    val PropClass = Ref("PropClass")
    val Props = Ref("Props")
    val QuasiAst = Ref("QuasiAst")
    val QuasiHole = Ref("QuasiHole")
    val QuasiInner = Ref("QuasiInner")
    val QuasiLeaf = Ref("QuasiLeaf")
    val QuasiTree = Ref("QuasiTree")
    val Quasis = Ref("Quasis")
    val RawBlock = Ref("RawBlock")
    val RawCommaOp = Ref("RawCommaOp")
    val RegExp = Ref("RegExp")
    val RegularDot = Ref("RegularDot")
    val AwaitReturnThrowYield = Ref("AwaitReturnThrowYield")
    val Root = Ref("Root")
    val SpecialDot = Ref("SpecialDot")
    val Specialize = Ref("Specialize")
    val Spread = Ref("Spread")
    val Stmt = Ref("Stmt")
    val StmtBlock = Ref("StmtBlock")
    val StringGroup = Ref("StringGroup")
    val StringGroupTagged = Ref("StringGroupTagged")
    val StringHole = Ref("StringHole")
    val StringHoleRaw = Ref("StringHoleRaw")
    val StringLiteral = Ref("StringLiteral")
    val StringPart = Ref("StringPart")
    val StringPartRaw = Ref("StringPartRaw")
    val SymbolLiteral = Ref("SymbolLiteral")
    val SymbolValue = Ref("SymbolValue")
    val Throws = Ref("Throws")
    val TopLevel = Ref("TopLevel")
    val TopLevelNoGarbage = Ref("TopLevelNoGarbage")
    val TopLevelNoGarbageNoComment = Ref("TopLevelNoGarbageNoComment")
    val TopLevels = Ref("TopLevels")
    val TopLevelsInSemi = Ref("TopLevelsInSemi")
    val TrailingSemi = Ref("TrailingSemi")
    val Type = Ref("Type")
    val TypeArgument = Ref("TypeArgument")
    val TypeArgumentName = Ref("TypeArgumentName")
    val TypeArguments = Ref("TypeArguments")
    val UnicodeRun = Ref("UnicodeRun")
    val UnicodeRunPart = Ref("UnicodeRunPart")
    val UnicodeRunPartRaw = Ref("UnicodeRunPartRaw")
    val UnicodeRunRaw = Ref("UnicodeRunRaw")

    // Allow reusing the same machinery to parse Json metadata
    val Json = Ref("Json")
    val JsonArray = Ref("JsonArray")

    val JsonBoolean = Ref("JsonBoolean")
    val JsonNull = Ref("JsonNull")
    val JsonNumber = Ref("JsonNumber")
    val JsonObject = Ref("JsonObject")
    val JsonProperty = Ref("JsonProperty")
    val JsonString = Ref("JsonString")
    val JsonValue = Ref("JsonValue")
}

/**
 * This file specifies a grammar using a DSL implemented with Kotlin extension functions.
 *
 * The following conventions apply herein:
 * - All production names are defined in [ProductionNames].
 * - NAME `：＝` PATTERN declares that de-referencing NAME will apply PATTERN
 * - Every PATTERN is a [Combinator] which consumes input pseudo-tokens to emit a flattened subtree
 *   to the output that can then be [lift]ed into an AST.
 * - (P y Q) is pattern concatenation.
 * - (P / Q) means try P, and use its result if it succeeds; else try Q.
 * - opt(P) means (P / epsilon) where *epsilon* always succeeds and consumes no input.
 *   It's easiest to think of *epsilon* as matching the empty string though that isn't quite right
 *   since `""` would mean "match a token that has zero characters".
 * - many(P) matches P one or more times.
 * - any(P) matches P zero or more times.
 * - `(` is a pattern that matches [LeftParenthesis], a pseudo-token that corresponds to the start
 *   of a [lang.temper.cst.CstInner].  See [flatten]
 * - `)` matches [RightParenthesis], a pseudo-token that corresponds to the end of a
 *   [lang.temper.cst.CstInner]
 * - [Operator] `y` `(` ... `)` provides a lookahead asking for the associated operator for the CST group.
 * - `callTree(...)`, `blockTree(...)` etc. are signals that the containing productions start and
 *   end trees.  The prefixes correspond to [TreeType]s.
 * - `shiftLeft` is used to reorder operands.  The output tree for an input like `x + y` is a
 *   [lang.temper.value.CallTree] whose first argument corresponds to `+` and whose second
 *   corresponds to `x`.
 *
 * If you find yourself having to debug this, [Debug] might come in handy.
 */
val grammar = ProductionNames.run {
    val grammar = Productions(::lift)

    @Suppress("NonAsciiCharacters", "FunctionName")
    infix fun (Ref).`：＝`(combinator: Combinator) {
        grammar.declare(this.name, combinator)
    }

    // ////////////////////////////////////// START GRAMMAR ////////////////////////////////////////
    /**
     * The root of a Temper module is a sequence of top-levels followed by an end of file marker.
     */
    Root `：＝` (
        ((Operator.Root y `(` y TopLevels y `)`) / TopLevels) y Eof
        )
    /** Top-levels are separated by semicolons in a module body or block. */
    TopLevels `：＝` any(
        // We allow embedded comments before
        (Operator.Semi y `(` y TopLevelsInSemi y `)`) /
            TopLevel,
    )
    /** Top levels in the context of a larger semicolon separated run. */
    TopLevelsInSemi `：＝` (any(any(Nop) y TopLevel) y TrailingSemi)
    /**
     * A top-level is roughly a whole declaration, or expression.
     * Temper is an expression language, so most statement-like constructs can
     * also nest.
     */
    TopLevel `：＝`
        TopLevelNoGarbage /
        (`(` y Garbage("TopLevel", stopAfter = setOf(";"), stopBefore = setOf("}"), requireSome = true) y `)`) /
        Garbage("TopLevel", requireSome = true)
    TopLevelNoGarbage `：＝` (opt(EmbeddedComment) y TopLevelNoGarbageNoComment)
    TopLevelNoGarbageNoComment `：＝`
        DecoratedTopLevel / Let / Stmt / QuasiHole

    /**
     * Semicolons (`;`) are allowed at the end of a block or module body.
     * An expression followed by a semicolon is not implicitly the result of
     * the containing block or module.
     *
     * Trailing semicolons are never [inserted][snippet/semicolon-insertion].
     */
    TrailingSemi `：＝` (opt(";".revalue(void)) y any(Nop))

    /**
     * Comments are not semantically significant but nor are they filtered
     * out entirely.
     *
     * Temper tries to preserve them when translating documentation, and
     * they are available to backends; for example, the Python backend turns
     * autodoc comments before declarations into Python doc strings.
     */
    EmbeddedComment `：＝` ExtractCommentsToCalls

    /**
     * Statements are not a large syntactic category, but include labeled
     * statements (like `someName: /* loop or block */`), jumps (`break`,
     * `continue`, `return`, etc.) which go to the end or beginning of
     * a containing statement and which may refer to a label.
     *
     * Besides those, any expression may appear in statement position.
     */
    Stmt `：＝` LabeledStmt /
        MatchBranch /
        StmtBlock /
        // This means we need to consume an indent pseudo-token, match expression,
        // consume a literal ';' token, consume a dedent pseudo-token.
        CommaExpr

    /** A `{` ... `}` delimited block of statements. */
    StmtBlock `：＝` (Operator.CurlyGroup y `(` y RawBlock y `)`)

    /**
     * Declares a label and associates it as markers for the beginning and
     * end of a statement, so that `break`s and `continue`s within that
     * statement may refer to it explicitly.
     *
     * Unlike TypeScript, we do not allow labeling any statement.
     * This allows conveying property declarations like the `p: T` in
     *
     * ```temper inert
     * interface I {
     *   p: T;
     * }
     * ```
     *
     * to the disAmbiguate stage with that property declaration
     * treated as a (Call (Name ":") (Name "p") (Name "T"))
     *
     * Otherwise, we would have to treat `class` bodies as a special syntactic
     * category to avoid ambiguity with
     *
     * ```temper inert
     * do {
     *   p: T;
     * }
     * ```
     *
     * or the disambiguation would need to convert `T`'s from statement
     * context to expression context.
     */
    LabeledStmt `：＝` blockTree(
        Operator.LowColon y `(` y
            `(` y (LeftLabel / QuasiHole) y `)` y ":".asSymbol(labelSymbol) y shiftLeft y
            (Call / StmtBlock) y
            `)`,
    )

    /**
     * Decorations transform declarations and function and type definitions at compile time.
     *
     * `@SomeName` followed by an optional argument list
     *
     * When a `let` declaration declares multiple names, any decoration before the `let`
     * applies to all the names, but declarations immediately before a declared name affect
     * only that name.
     */
    DecoratedTopLevel `：＝`
        // `let`s are special because in a `;` or root context, we need the `@` to distribute
        // over comma.
        DecoratedLet / (
            Operator.At y (
                callTree(
                    `(` y "@".rename("@") y (
                        // There are two forms to handle:
                        // - `@` with two operands
                        // - `@` with a call with a single argument where that argument should
                        //   have been treated as a second, parenthesized operand.
                        // After trying the first form above, we look for the overall closing
                        // pseudo-paren, and fail over to the second if it's no there, because
                        // there's more  content which means we also have to look for a closing
                        // pseudo-paren in the second branch.
                        (
                            (Expr y TopLevelNoGarbageNoComment y `)`) /
                                (
                                    Operator.Paren y `(` y Expr y
                                        "(" y TopLevelNoGarbageNoComment y ")" y `)` y `)`
                                    )
                            )
                        ),
                )
                )
            )

    /**
     * The call expression includes simple function calls (`f(args)`) as well as calls with
     * Ruby-style block functions (`f { ... }`) and flow-control like
     * `if (condition) { t } else { e }` because [`if` is a macro][builtin/if].
     */
    Call `：＝` callTree(
        (
            Operator.CallJoin y `(` y CallHead y
                Operator.CallJoin.textNotNull.asSymbol(callJoinSymbol) y CallTail y `)`
            ) /
            CallHead,
    )

    /** The function called, its arguments, and any block lambda */
    CallHead `：＝`
        (Operator.Curly y `(` y CalleeAndArgs y BlockLambda y `)`) /
        ((Operator.HighColon / Operator.Paren) y CalleeAndRequiredArgs)

    CallTail `：＝` (
        // An intermediate chain element
        (
            Operator.CallJoin y `(` y
                Operator.Curly y `(` y (
                    (Operator.Paren y `(` y CallJoiningWords y CallArgs y `)`) /
                        CallJoiningWords
                    ) y BlockLambda y `)` y
                Operator.CallJoin.textNotNull.asSymbol(callJoinSymbol) y CallTail y `)`
            ) /
            // The terminal element with a passed block
            (
                Operator.Curly y `(` y (
                    (Operator.Paren y `(` y CallJoiningWords y CallArgs y `)`) /
                        CallJoiningWords
                    ) y BlockLambda y `)`
                ) /
            // The terminal element without a passed block as in
            //     do {...} while (c)
            (Operator.Paren y `(` y CallJoiningWords y CallArgs y `)`)
        )

    // Changes to this may need to be reflected in CalleeAndRequiredArgs
    /**
     * Captures low precedence operators that may follow a parenthesized argument list.
     *
     * - `: ReturnType` desugars to `\outType`, `ReturnType`.
     * - `extends SuperType` and `implements SuperType* desugars to `\super`, `SuperType`.
     */
    CalleeAndArgs `：＝` (
        // The "return type" desugars to a declaration of an output parameter
        // named "return"
        (
            Operator.HighColon y `(` y CalleeAndArgs y
                ":".asSymbol(outTypeSymbol) y Type y `)`
            ) /
            (
                setOf(Operator.ExtendsComma, Operator.ImplementsComma) y `(` y
                    CalleeAndArgs y
                    (
                        "extends".asSymbol(superSymbol) /
                            "implements".asSymbol(superSymbol)
                        ) y
                    Type y any(",".asSymbol(superSymbol) y Type) y
                    `)`
                ) /
            (
                setOf(Operator.ForbidsComma) y `(` y
                    CalleeAndArgs y
                    "forbids".asSymbol(forbidsSymbol) y
                    Type y any(",".asSymbol(forbidsSymbol) y Type) y
                    `)`
                ) /
            (
                supports y `(` y
                    CalleeAndArgs y
                    "supports".asSymbol(supportsSymbol) y
                    Type y any(",".asSymbol(supportsSymbol) y Type) y
                    `)`
                ) /
            (Operator.Paren y `(` y Callee y CallArgs y `)`) /
            Callee
        )

    /**
     * This is like CalleeAndArgs but is used in contexts where we're not sure yet
     * whether this is a call.  A call requires at least one of
     *
     * - Parenthesized arguments as in  `callee()`
     * - Semi-ed arguments       as in  `loopMacro (initialization; condition; increment)`
     * - A template string       as in  `callee"foo ${bar}"`
     * - A trailing block        as in  `callee {}`
     *
     * This production succeeds is entered where we may not have a trailing block
     * so must have one of the others.
     */
    CalleeAndRequiredArgs `：＝` (
        (
            Operator.HighColon y `(` y CalleeAndRequiredArgs y
                ":".asSymbol(outTypeSymbol) y Type y `)`
            ) /
            (Operator.Paren y `(` y Callee y CallArgs y `)`)
        )

    /**
     * When the call continues with something like `} else if (c) {...}` we need to include
     * `\else_if = fn {...}` as a final named parameter to the call that receives the block
     * just closed, so that the called function can delegate its result to later segments.
     * This joins words like `else if` into the `\else_if` symbol which follows the call join
     * symbol.  A late parse stage pass finds those and groups everything following the joining
     * words into a trailing block function so that the contents of the parentheses and brackets
     * can match their own signature elements based on the joining words.
     */
    CallJoiningWords `：＝`
        CompactWordsToSymbol(
            Operator.Leaf y `(` y many(name(unreservedWordMatcher)) y `)`,
        )

    /**
     * The callee in a function application is a tad complicated.
     *
     * Our OPP grammar covers many constructs that are bespoke constructs
     * in many languages, so `class C extends A, B { ... }` is parsed as an
     * application of a block lambda (later turned into a member block) like
     * `class(\word, C, \super, A, \super B, fn { .... })`.
     *
     * This production desugars various parts into a combination of the
     * callee `class`, and symbol/argument pairs.
     *
     * The `\word` argument is also used in constructs like function declaration
     * `let f<T>(arg: Type) { ... }` where the `let` macro is what is invoked to
     * build a named function declaration.
     *
     * This production allows a callee to have:
     *
     * - an expression specifying the called macro or function,
     * - an accompanying word,
     * - type parameters like `<T, U>` (whether the type parameters are actual
     *   parameters or formal parameters is determined by the Disambiguate stage),
     */
    Callee `：＝` (
        // Two words like "function name" contribute a name and a named parameter.
        (Operator.Leaf y `(` y Id y wordSymbol y leftName(unreservedWordMatcher) y `)`) /
            (Operator.Angle y `(` y Callee y TypeArguments y `)`) /
            Expr
        )

    /**
     * Arguments to a function call.
     *
     * A function call's arguments may be one of:
     *
     * - a parenthesized, comma separated list of arguments like `(a, b, c)`.  See Args
     * - a parenthesized, semicolon separated list of 2 or three arguments
     *   with a specific purpose.  As in `(let x = 1; x < 2; ++x)` which is what the
     *   `for` loop macro expects.
     * - a string group as in a tagged string template like `callee"foo ${ bar }"`.
     */
    CallArgs `：＝` (
        (syntheticToken y StringGroupTagged) /
            ("(" y (ForArgs / opt(Args)) y ")")
        )

    /**
     * Relates a match case, e.g. a pattern, to a consequence of matching that pattern.
     */
    MatchBranch `：＝` (
        // Put else first to special case it before general expressions. //
        (
            Operator.ThinArrow y `(` y Operator.Leaf y `(` y "else".asSymbol(defaultSymbol) y `)` y
                "->" y Expr y `)`
            ) /
            (Operator.ThinArrow y `(` y MatchCase y "->" y Expr y `)`) /
            (
                Operator.ThinArrow y `(` y
                    Operator.Comma y `(` y MatchCase y any("," y MatchCase) y opt(",") y `)` y
                    "->" y Expr y
                    `)`
                )
        )

    /**
     * There are two kinds of match cases: run-time type checks that use keyword `is`, and a value to match.
     */
    MatchCase `：＝` (
        (Operator.PreIs y `(` y "is".asSymbol(caseIsSymbol) y Expr y `)`) /
            (
                Operator.PreCase y `(` y impliedValue(Value(caseCaseSymbol)) y
                    callTree(
                        "case".revalue(vPostponedCaseMacro) y
                            callTree(impliedValue(BuiltinFuns.vListifyFn) y BalancedTokenList) y `)`,
                    )
                ) /
            (impliedValue(Value(caseSymbol)) y Expr)
        )
    BalancedTokenList `：＝` (
        any(
            (`(` y BalancedTokenList y `)`) /
                value(Match(GrammarDoc.Comment("any token"), true) { true }),
        )
        )

    ForArgs `：＝` (
        (
            Operator.Semi y `(` y
                opt(ForInit) y
                ";" y
                opt(ForCond) y
                ";" y
                opt(ForIncr) y
                `)`
            ) / (
            Operator.SemiSemi y `(` y
                opt(ForInit) y
                ";;" y
                opt(ForIncr) y
                `)`
            )
        )

    ForInit `：＝` (flowInitSymbol y (ForInitLet / CommaExpr))
    ForInitLet `：＝` (softCommaTree(Let) / softCommaTree(DecoratedLet))
    ForCond `：＝` (condSymbol y CommaExpr)
    ForIncr `：＝` (incrSymbol y CommaExpr)

    /**
     * `await`, `return`, `throw`, and `yield` are operators which affect
     * control flow and operate on expressions.
     *
     * `return(42);` for example, looks like a function call but the parentheses
     * are not required:
     *
     * - `return;` is an application of an operator even though there
     *   are no parentheses.
     * - `return 42;` is an application of the operator to the arguments
     *   `(42)` even though there are no explicit parentheses.
     */
    AwaitReturnThrowYield `：＝` (
        setOf(Operator.Await, Operator.Return, Operator.Throw, Operator.Yield) y callTree(
            `(` y (
                name("await") / name("return") / name("throw") / name("yield")
                ) y
                opt(
                    Expr /
                        Garbage("Argument", stopBefore = closeBrackets + ";", requireSome = true),
                ) y
                `)`,
        )
        )

    /**
     * A semicolon used to separate statements.
     * Since our parser is built around an operator precedence parser, and semicolon is a low
     * precedence operator, this grammar consumes them, but does not require them.
     *
     * Not all semicolons need to appear explicitly in program text.
     *
     * ⎀ semicolon-insertion
     */
    Nop `：＝` (epsilon y ";")

    LabelOrHole `：＝` (labelSymbol y (Label / QuasiHole))
    /** A label that can be jumped to as by `break` and `continue`. */
    Label `：＝` name(unreservedWordMatcher)
    /**
     * A label that that can be jumped to as by `break` and `continue`.
     * This is *left* in the left-hand-side sense: it is a declaration, not a use.
     */
    LeftLabel `：＝` leftName(unreservedWordMatcher)

    /**
     * A jump to a location within the same function body that does not skip over
     * any necessary variable initializations.
     */
    Jump `：＝` (
        callTree(
            `(` y
                (name("break") / name("continue")) y
                opt(LabelOrHole) y
                `)`,
        )
        )

    /**
     * A block lambda is a `{...}` block that specifies a function value and which
     * cna appear as part of a function call as below:
     *
     *     someFunction { ... }
     *
     * Optionally, a signature is needed to specify argument names, and may specify
     * the function type wholly or partially.
     *
     * It may be followed by an `extends` clause that specifies marker interfaces
     * that are super-types for the produced function value.
     *
     * The signature is followed by the double-semicolon token (`;;`) which is
     * distinct from two, space separated semicolons (`; ;`).
     *
     * ```temper inert
     * someFunction /* <- callee */ {
     *   (x): ReturnType               // <- Optional signature
     *   extends SomeInterfaceType     // <- super types
     *   ;;                            // <- double semicolon separator
     *
     *   body
     * }
     * ```
     */
    BlockLambda `：＝` (
        blockPreceder y funTree(
            // `;;` indicates we need to look for a signature
            (
                epsilon y "{" y Operator.Arrow y `(` y
                    BlockLambdaSignatureAndSupers y "=>" y BlockLambdaBody y
                    `)` y "}"
                ) / (
                epsilon y "{" y Operator.Arrow y `(` y
                    Formals y "=>" y BlockLambdaBody y
                    `)` y "}"
                ) /
                // If the only thing in the {...} is body instructions because
                // there's no `before`, then parse as a raw block so that the
                // brackets are included in the position metadata
                RawBlock,
        )
        )
    /**
     * The signature of a block lambda explains the names of arguments
     * visible within the body, optionally their types and return type.
     *
     * The signature also includes other interfaces that the lambda must
     * implement.  For example, a function that might pause execution
     * could use a signature line as below:
     *
     *     (x: Int): Int extends GeneratorFn =>
     *
     * That describes a function that takes an integer `x` and which
     * also is a sub-type of [snippet/type/GeneratorFn].
     *
     * The `extends` clause may be left off entirely if no super-types
     * are desired, or multiple super-types may be specified:
     * `extends First & Second`.
     *
     * Unlike in a function type, when a name is specified for a
     * block lambda argument, it is the name of the argument,
     * not its type.
     *
     * ```temper inert
     * let f: fn (Int): Void;
     * //         ⇧⇧⇧ word is a type
     *
     * let g(myLambda: fn (Int): Void): Void { myLambda(1); }
     *
     * g { (x): Void =>
     *   // ⇧ word is an argument name.
     *   // In this case, the type is inferred from g's signature.
     *   doSomethingWith(x + 1);
     * }
     * ```
     */
    BlockLambdaSignatureAndSupers `：＝` (
        (
            extendsLikeOperators y (
                `(` y BlockLambdaSignatureAndSupers y BlockLambdaSupers y `)`
                )
            ) /
            BlockLambdaSignature /
            Garbage(
                "BlockLambdaSignature",
                stopBefore = setOf("}"),
            )
        )

    /**
     * A block lambda signature line like `(x: Int): ReturnType` or just
     * `(x)` to take advantage of the more aggressive type inference for block
     * lambdas than for declared functions.
     *
     * This is often followed by `;;` as it is part of
     * [snippet/syntax/BlockLambdaSignatureAndSupers].
     *
     * These syntactic constructs are interpreted as if preceded by
     * [snippet/builtin/fn] but the meaning is subtly different.
     *
     * - `(x: Int)` is equivalent to `fn (x: Int)` where the return type must
     *   later be inferrable from the calling context and the body.
     * - `(x)` is equivalent to `fn (x)` where the argument and return type
     *   must later be inferrable.
     * - (x): ReturnType` is equivalent to `fn (x): RT` where only argument types
     *   must later be inferrable.
     */
    BlockLambdaSignature `：＝` (
        // With a return type
        Operator.HighColon y `(` y
            Operator.ParenGroup y `(` y "(" y
            opt(Formals) y
            ")" y `)` y
            // The return type
            ":".asSymbol(outTypeSymbol) y Type y
            `)`
        ) / ( // Or without
        Operator.ParenGroup y `(` y "(" y
            (
                // A placeholder signature that serves to allow an `extends` clause
                (Operator.Ellipsis y `(` y "..." y `)`) /
                    // or actual formals
                    opt(Formals)
                ) y
            ")" y `)`
        )

    BlockLambdaSupers `：＝` (
        ("extends".asSymbol(superSymbol) / "implements".asSymbol(superSymbol)) y
            Type y any(",".asSymbol(superSymbol) y Type) y
            opt(",")
        )

    BlockLambdaBody `：＝` blockTree(
        (Operator.Semi y `(` y TopLevelsInSemi y `)`) /
            opt(TopLevel),
    )

    RawBlock `：＝` (blockPreceder y blockTree("{" y TopLevels y "}"))
    // Matches a JS style {propertyName:value} expr.
    Obj `：＝` (
        propertyBagPreceder y
            callTree(
                "{".rename("new") y (Props / NoPropClass) y "}",
            )
        )
    Props `：＝` (
        (
            Operator.Comma y `(` y
                (PropClass / (NoPropClass y Prop)) y any("," y Prop) y
                opt(",") y
                `)`
            ) /
            PropClass /
            (NoPropClass y Prop)
        )
    // `new` expects a type hint as its first argument.
    // If there is a `this: type` property, then use its value as the
    // type to construct.  Otherwise, use `void` to indicate that the
    // type needs to be inferred.
    NoPropClass `：＝` impliedValue(void, Left)
    PropClass `：＝` (infixColons y `(` y `(` y "class" y `)` y ":" y Type y `)`)
    Prop `：＝` (
        (infixColons y `(` y PropName y ":" y Expr y `)`) /
            // If there is no expression, treat it as a pun;
            // `{ x }` is shorthand for `{ x: x }` where the `x` before the colon is a left
            // hand, and the one to the right is a right hand that should bind to an `x`
            // defined outside the bag.
            (PropName y callTree(impliedValue(BuiltinFuns.vDesugarPun, bias = Left)))
        )
    PropName `：＝` (
        Problem(`(` y "class" y `)`, MessageTemplate.ClassPropertyMustAppearFirst) /
            (`(` y value(unreservedWordMatcher) y `)`)
        )

    DecoratedLet `：＝` Counter(
        DecoratedLet, // Count the levels of decoration nesting
        (
            Operator.Comma y (
                `(` y Operator.At y
                    DecoratedLetBody y many("," y declTree(LetRest)) y
                    // We start a call for each nested annotation after its argument in
                    // DecoratedLetBody, so we need to end it in each branch here
                    finishSplitCommaSoft() y CountForEach(DecoratedLet, finishSplitCall()) y
                    `)`
                )
            ) /
            (
                Operator.At y DecoratedLetBody y finishSplitCommaSoft() y
                    CountForEach(DecoratedLet, finishSplitCall())
                ),
    )

    Let `：＝` (
        (Operator.Comma y `(` y declTree(LetBody) y many("," y declTree(LetRest)) y `)`) /
            declTree(LetBody)
        )

    DecoratedLetBody `：＝` (
        (
            Operator.At y `(` y
                // Count the number of calls to at so that we can finish them after all the
                // declarations have been nested in them
                startSplitTree(
                    /* call to @ */
                ) y CountUp(DecoratedLet) y
                "@".rename("@") y (
                    // We need to start a comma call tree after the annotation argument in both
                    // branches here so that the annotation applies both to the declaration here
                    // and any in the declarations following commas.
                    // In DecoratedLet, we finish the split block.
                    (Expr y DecoratedLetBody y `)`) /
                        (
                            Operator.Paren y `(` y Expr y "(" y
                                NegLA(epsilon y Operator.Comma) y DecoratedLetBody y ")" y `)` y `)`
                            )
                    )
            ) / (
            startSplitTree(
                /*Block*/
            ) y declTree(LetBody)
            )
        )

    LetBody `：＝` (
        (Operator.Leaf y `(` y "let" y DeclName y `)`) /
            (Operator.Square y `(` y Operator.Leaf y `(` y "let" y `)` y DeclMulti y `)`) /
            (Operator.Curly y `(` y Operator.Leaf y `(` y "let" y `)` y DeclMultiNamed y `)`) /
            (Operator.Eq y `(` y LetBody y DeclInit y `)`) /
            (Operator.HighColon y `(` y LetBody y DeclType y `)`)
        )

    LetNested `：＝` (
        (Operator.Leaf y `(` y DeclName y `)`) /
            (Operator.Ellipsis y `(` y "...".asSymbol(surpriseMeSymbol) y `)`) /
            (Operator.SquareGroup y `(` y DeclMulti y `)`) /
            (Operator.CurlyGroup y `(` y DeclMultiNamed y `)`) /
            (Operator.Eq y `(` y LetNested y DeclInit y `)`) /
            (infixColons y `(` y LetNested y DeclMultiNested y `)`) /
            (Operator.As y `(` y LetNested y "as".asSymbol(asSymbol) y `(` y DeclName y `)` y `)`) /
            (Operator.Is y `(` y LetNested y DeclTypeNested y `)`)
        )

    LetRest `：＝` (
        (Operator.Leaf y `(` y DeclName y `)`) /
            (Operator.SquareGroup y `(` y DeclMulti y `)`) /
            (Operator.CurlyGroup y `(` y DeclMultiNamed y `)`) /
            (Operator.Eq y `(` y LetRest y DeclInit y `)`) /
            (Operator.HighColon y `(` y LetRest y DeclType y `)`)
        )

    // A let in a formal argument list.
    LetArg `：＝` (
        (Operator.Leaf y `(` y "let" y DeclName y `)`) /
            (Operator.Eq y `(` y LetArg y DeclDefault y `)`) /
            (Operator.HighColon y `(` y LetArg y DeclType y `)`) /
            (supports y `(` y LetArg y DeclReifies y `)`)
        )

    DeclName `：＝` leftName(unreservedWordMatcher)
    DeclReifies `：＝` (":".asSymbol(reifiesSymbol) y Type)
    DeclType `：＝` (":".asSymbol(typeSymbol) y Type)
    DeclTypeNested `：＝` ("is".asSymbol(typeSymbol) y Type)
    DeclMultiNested `：＝` (":".asSymbol(asSymbol) y (Operator.CurlyGroup y `(` y DeclMultiNamed y `)`))
    DeclInit `：＝` ("=".asSymbol(initSymbol) y Expr)
    DeclDefault `：＝` ("=".asSymbol(defaultSymbol) y Expr)

    DeclMulti `：＝` callTree(
        "[" y
            (
                (
                    Operator.Comma y `(` y
                        LetNested y ",".revalue(commaFnValue) y shiftLeft y
                        opt(
                            LetNested y any("," y LetNested) y opt(","),
                        ) y
                        `)`
                    ) /
                    (impliedValue(commaFnValue, Left) y LetNested)
                ) y
            "]",
    )

    DeclMultiNamed `：＝` callTree(
        "{".rename(curliesBuiltinName) y
            (
                (Operator.Comma y `(` y LetNested y any("," y LetNested) y opt(",") y `)`) /
                    LetNested
                ) y
            "}",
    )

    Type `：＝` Expr

    // During the DisAmbiguate stage, we need to distinguish between type formals and type actuals.
    // A type formal like those in `let f<T extends AnyValue>(x: T): T { ... }` must fit one of
    // several patterns
    // - `T extends SuperType1 & SuperType2`: a name with default variance and
    // - `in T`: has a variance indicator
    // - `@Decorator T`: a decorated
    //
    // A type actual like those in `f<Foo>(foo)` can be an arbitrary expression.
    // In practice, it's either a type expression or a "constant" expression.
    // But constant expressions are not easily bounded at parse time.
    //
    // The only kind of formal that does not fit in the actual grammar is the variance annotations.
    //     <@Decorator in T extends Super1 & Super2 = DefaultTypeExpressionForT>
    // In this case the `in T` is a variance annotation
    // `in` is an infix operator, and parsing this when the `in T` is deeply nested in other
    // constructs is complicated.
    // So `Expr` has a special case, TypeArgumentName, just for this case.
    TypeArguments `：＝` (
        "<" y (
            (Operator.Comma y `(` y TypeArgument y any("," y TypeArgument) y `)`) /
                TypeArgument
            ) y
            ">"
        )

    TypeArgument `：＝` (typeArgSymbol y Expr)

    TypeArgumentName `：＝` (
        Operator.Leaf y (
            callTree(
                `(` y
                    ("in".rename(ParsedName("@in")) / "out".rename(ParsedName("@out"))) y
                    Id y `)`,
            )
            )
        )

    CommaExpr `：＝` (Operator.Comma y callTree(CommaOp)) / Expr

    CommaOp `：＝` (
        Operator.Comma y `(` y
            CommaEl y ",".revalue(commaFnValue) y shiftLeft y
            opt(
                CommaEl y any("," y CommaEl) y opt(","),
            ) y `)`
        )

    // A RawCommaOp is a comma separated group of expressions.
    // So [a, b, c] uses RawCommaOp to parse the elements of a list constructor call, but [(a, b, c)] is a list
    // constructor call whose sole element is a CommaOp.
    RawCommaOp `：＝` (
        Operator.Comma y `(` y
            CommaEl y any("," y CommaEl) y opt(",") y
            `)`
        ) /
        CommaEl

    CommaEl `：＝` (
        declTree(LetBody) /
            Expr /
            Garbage("ListElement", stopBefore = closeBrackets + ",", requireSome = true)
        )

    /** An expression is evaluated to produce a result and/or a side effect. */
    Expr `：＝`
        QuasiTree /
        QuasiAst /
        QuasiHole /
        Jump /
        AwaitReturnThrowYield /
        Id /
        TypeArgumentName / // Hack for ambiguity resolved later.  See notes on TypeArgument.
        SymbolLiteral /
        List /
        New /
        Prefix /
        SpecialDot /
        RegularDot /
        Call /
        StringLiteral /
        // Parentheses for grouping
        (
            Operator.ParenGroup y `(` y "(" y
                (
                    (NegLA(Operator.Comma y `(`) y TopLevelNoGarbageNoComment) /
                        // Comma syntax in parentheses is reserved for tuple syntax.
                        (
                            Operator.Comma y
                                Garbage(
                                    messageTemplate = MessageTemplate.SyntaxReservedForTuples,
                                    messageValues = listOf(),
                                    stopBefore = setOf(")"),
                                )
                            ) /
                        Garbage("Expression", stopBefore = setOf(")"))
                    ) y
                ")" y `)`
            ) /
        OfExpr /
        Throws /
        Infix /
        Postfix /
        Member /
        Specialize /
        Literal /
        RegExp /
        (Operator.CurlyGroup y `(` y (Obj / RawBlock) y `)`) /
        (
            `(` y "(" y
                Garbage( // TODO: is this ever reached given garbage handling above?
                    "Expression",
                    stopBefore = setOf(",", ";") + closeBrackets,
                    stopAfter = setOf(")"),
                ) y
                `)`
            )

    Literal `：＝` ((`(` y value(litMatcher) y `)`) / value(litMatcher))
    RegExp `：＝` callTree(`(` y regexLiteralBuiltinName y RegexToArgs y `)`)
    Id `：＝` ((`(` y name(unreservedWordMatcher) y `)`) / name(unreservedWordMatcher))
    List `：＝` (
        Operator.SquareGroup y callTree(
            `(` y "[".revalue(BuiltinFuns.vListifyFn) y (
                // [T; a, b]  means a List[T] with 2 elements: a and b
                (Operator.Semi y `(` y typeSymbol y Type y ";" y ListContent y `)`) /
                    ListContent
                ) y "]" y `)`,
        )
        )

    ListContent `：＝` (
        ListElements /
            ListElement /
            opt(Garbage("List", stopBefore = setOf("]")))
        )

    ListElements `：＝` (
        Operator.Comma y `(` y
            // An array consists of holes and expressions that either are followed by a comma
            // or by the end of the array.
            any(
                ListHole / (
                    ListElement y (
                        "," / Lookahead(GrammarDoc.Skip) { it.next is RightParenthesis }
                        )
                    ),
            ) y
            // An array may end with a trailing comma which contributes no element.
            opt(",") y
            `)`
        )
    ListElement `：＝` (
        (
            Spread /
                Expr /
                Garbage(
                    "ListElement",
                    stopBefore = closeBrackets + ",",
                    requireSome = true,
                )
            )
        )
    Spread `：＝` (Operator.Ellipsis y callTree(`(` y name("...") y Expr y `)`))
    ListHole `：＝` callTree(
        ",".rename(holeBuiltinName) y Lookahead(GrammarDoc.Skip) {
            // Not a comma that is the last in the array.
            it.next !is RightParenthesis
        },
    )

    /**
     * `of` can take a declaration as its left side as in
     * `for (let element of elements) {}`.
     */
    OfExpr `：＝` (
        Operator.Of y callTree(
            `(` y ForInitLet y "of".rename(ofBuiltinName) y shiftLeft y Expr y `)`,
        )
        )

    /** Converts to a *Result* type. */
    Throws `：＝`
        (
            Operator.Throws y callTree(
                `(` y Type y "throws".rename(throwsBuiltinName) y shiftLeft y
                    FailureMode y any("|" y FailureModeSecondary) y `)`,
            )
            )

    FailureMode `：＝` Type
    FailureModeSecondary `：＝` Type

    Infix `：＝` (
        // Some operators take types as right-hand-arguments.
        (
            setOf(Operator.LowColon, Operator.HighColon, Operator.Instanceof, Operator.Is) y
                callTree(`(` y Expr y name(InfixOp) y shiftLeft y Type y `)`)
            ) /

            (
                OperatorType.Infix y callTree(
                    `(` y
                        Expr y
                        name(InfixOp) y shiftLeft y
                        Expr y
                        `)`,
                )
                )
        )
    InfixOp `：＝` isOperator(OperatorType.Infix, exclude = stmtContinueOps)

    Prefix `：＝` (OperatorType.Prefix y callTree(`(` y name(PrefixOp) y Expr y `)`))
    PrefixOp `：＝` isOperator(OperatorType.Prefix, exclude = setOf(Operator.New, Operator.PreCase))

    Postfix `：＝` (
        OperatorType.Postfix y callTree(
            // For postfix ++ and --, postfixApply is a macro that propagates the desired result.
            (
                (setOf(Operator.PostDecr, Operator.PostIncr) y impliedValue(vPostfixApply, Left)) /
                    epsilon
                ) y
                // Grab the operand which precedes the operator.  Later we shift the
                // operator function left over it.
                `(` y Expr y
                PostfixOp.rename(
                    mapOf(
                        Operator.PostBang.text!! to Either.Left(BuiltinName("_!")),
                        Operator.PostQuest.text!! to Either.Left(BuiltinName("?")),
                    ),
                ) y shiftLeft y
                `)`,
        )
        )
    PostfixOp `：＝` isOperator(OperatorType.Postfix, exclude = emptySet())

    New `：＝` (
        Operator.Paren y callTree(
            `(` y
                Operator.New y `(` y
                name("new") y
                Type y
                `)` y
                "(" y opt(Args) y ")" y
                `)`,
        )
        )
    Member `：＝` (
        Operator.Square y callTree(
            `(` y
                Expr y "[".rename(squaresBuiltinName) y shiftLeft y RawCommaOp y "]" y
                `)`,
        )
        )

    SpecialDot `：＝` (
        (
            // `builtins.foo` parses to the BuiltinName "foo"
            Operator.Dot y `(` y `(` y "builtins" y `)`
                y "." y `(` y name(builtinNameMatcher) y `)` y `)`
            ) /
            // `this` is a special name that refers to the enclosing class instance
            (Operator.Leaf y `(` y callTree("this".revalue(thisFnValue)) y `)`) /
            // `this` is a special name that refers to the enclosing class instance
            (
                Operator.Leaf y `(` y
                    Lookahead(GrammarDoc.Terminal("super")) {
                        it.nextToken?.tokenText == "super"
                    } y
                    Garbage("Word (not \"super\")") y `)`
                )
        )

    RegularDot `：＝` (
        // Make symbol operand optional for tooling's sake. It's validated elsewhere.
        setOf(Operator.Dot, Operator.ChainNull) y
            callTree(
                `(` y Expr y
                    (".".rename(dotBuiltinName) / "?.".rename(chainNullBuiltinName)) y
                    shiftLeft y
                    opt(SymbolValue) y `)`,
            )
        )

    Specialize `：＝` (
        Operator.Angle y callTree(
            `(` y
                Expr y "<".revalue(BuiltinFuns.vAngleFn) y shiftLeft y RawCommaOp y ">" y
                `)`,
        )
        )

    StringLiteral `：＝` (
        Operator.ParenGroup y callTree(`(` y catBuiltinName y StringGroup y `)`)
        )

    StringGroup `：＝` (
        syntheticToken y "(" y Operator.QuotedGroup y `(` y (
            (TokenType.LeftDelimiter y delimiterMatcher) y
                any(StringPart) y
                (TokenType.RightDelimiter y delimiterMatcher) y
                `)` y ")"
            )
        )

    StringGroupTagged `：＝` (
        syntheticToken y "(" y Operator.QuotedGroup y callTree(
            `(` y BuiltinFuns.vInterpolateMacro y (
                (TokenType.LeftDelimiter y delimiterMatcherBackticks) y
                    any(StringPartRaw) y
                    (TokenType.RightDelimiter y delimiterMatcherBackticks) y
                    `)`
                ) y ")",
        )
        )

    StringHole `：＝` (
        Operator.DollarCurly y `(` y "\${" y
            (
                CommaExpr /
                    Garbage("Hole", stopBefore = setOf(")"))
                ) y
            "}" y `)`
        )

    /** For custom usage, insert interpolation markers. */
    StringHoleRaw `：＝` (
        Operator.DollarCurly y `(` y "\${".asSymbol(interpolateSymbol) y
            (
                CommaExpr /
                    Garbage("Hole", stopBefore = setOf(")"))
                ) y
            "}" y `)`
        )

    UnicodeRun `：＝` (
        (Operator.UnicodeRun y `(` y "\\u{" y argsActualOrFormalNoTrailingComma(UnicodeRunPart) y "}" y `)`) /
            // opt above doesn't seem to work.
            (Operator.UnicodeRun y `(` y "\\u{" y "}" y `)`)
        )

    UnicodeRunPart `：＝` (`(` y many(TokenType.QuotedString y value(TokenToCodePoint)) y `)`)

    UnicodeRunRaw `：＝` (
        Operator.UnicodeRun y `(` y "\\u{".asValue() y (
            (Operator.Comma y `(` y any(UnicodeRunPartRaw) y `)`) /
                UnicodeRunPartRaw /
                epsilon /
                Garbage("Arguments", stopBefore = setOf(")"), requireSome = true)
            ) y "}".asValue() y `)`
        )

    UnicodeRunPartRaw `：＝` (
        (`(` y many(TokenType.QuotedString y value(TokenToRawString)) y `)`) /
            ",".asValue() /
            StringHoleRaw
        )

    /**
     * <!-- snippet: syntax/string/interpolation -->
     * # String interpolation
     *
     * Strings may contain embedded expressions.  When a
     * string contains a `${` followed by an expression,
     * followed by a `}`, the resulting string value
     * is the concatenation of the content before,
     * content from the expression, and the content after.
     *
     * ```temper
     * "foo ${"bar"} baz"
     * == "foo bar baz"
     * ```
     *
     * Interpolated values that aren't *String* instances
     * have `.toString()` called on them automatically,
     * which is convenient for types that define *toString*,
     * such as *Int*.
     *
     * ```temper
     * let two = 2;
     * "one ${two} three"
     * == "one 2 three"
     * ```
     *
     * An empty interpolation contributes no characters,
     * which means it may be used to embed meta-characters.
     *
     * ```temper
     * "$${}{}" == "\$\{\}"
     * ```
     *
     * (This mostly comes in handy with tagged strings to
     * give fine-grained control over what the tag receives.)
     *
     * Empty interpolations can also be used to wrap a long
     * string across multiple lines.
     *
     * ```temper
     * "A very long string ${
     *   // Breaking this string across multiple lines.
     * }that runs on and on"
     * == "A very long string that runs on and on"
     * ```
     *
     * Empty interpolations also let you include spaces at
     * the end of a line in a multi-quoted string.
     *
     * ```temper
     * """
     * "Line 1
     * "Line 2 ${}
     * == "Line 1\nLine 2 "
     * ```
     */
    StringPart `：＝` (
        (
            `(` y
                // If there are embedded multi-quote sequences, there may be multiple elements in one leaf.
                many(
                    TokenType.QuotedString y
                        value(Match(GrammarDoc.NonTerminal("ContentChars"), emit = true) { true }),
                ) y
                `)`
            ) / StringHole / UnicodeRun
        )

    /**
     * Parallels [ProductionNames.StringPart] but emits a [ValuePart] instead
     * of routing a string token to [lang.temper.lexer.unpackQuotedString] so
     * that the tag expression gets string content without escape sequences
     * decoded.
     */
    StringPartRaw `：＝` (
        (
            `(` y
                // If there are embedded multi-quote sequences, there may be multiple elements in one leaf.
                many(TokenType.QuotedString y value(TokenToRawString)) y
                `)`
            ) / StringHoleRaw / UnicodeRunRaw
        )

    Args `：＝` argsActualOrFormal(Arg)
    Formals `：＝` argsActualOrFormal(Formal)

    // This block is erased by the argument disambiguation stage.
    // It serves to group a pattern with the \type, and \init symbols so that
    //     name: type = init
    // which desugars to
    //     (Block name \type typeExpr \init initExpr)
    // and which the arg disambiguation stage (assuming its treated as a formal) converts to
    //     (Decl name \type typeExpr \init initExpr)
    // is not ambiguous with the multiple args
    //     name, type = type, init = init
    // which desugars to
    //     name (Block \type typeExpr) (Block \init initExpr)
    // which the arg disambiguation stage would treat either as the formals
    //     (Decl name) (Decl type \init typeExpr) (Decl init \init initExpr)
    // or the actuals
    //     name \type typeExpr \init initExpr
    fun argActualOrFormal(argNoInit: Ref, withDefault: Ref = argNoInit) =
        DecorateWithDocCommentCombinator(
            // See DecoratedTopLevel for why there are two `@` cases
            (
                Operator.At y callTree(
                    `(` y "@".rename("@") y (
                        (
                            Operator.Paren y `(` y
                                callTree(Expr y "(" y TopLevelNoGarbageNoComment y ")") y
                                `)` y Arg
                            ) /
                            (Expr y Arg)
                        ) y `)`,
                )
                ) /
                softBlockTree( // This block is eliminated during Stage.DisAmbiguate
                    opt(
                        (
                            (setOf(Operator.Eq, Operator.HighColon, Operator.LowColon, Operator.Ellipsis) + supports) /
                                NegLA(NegLA(Operator.Leaf y `(` y "let"))
                            ) y
                            // This doesn't apply to plain right names for some reason.
                            complexArgSymbol,
                    ) y (
                        (Operator.Eq y `(` y withDefault y "=".asSymbol(defaultSymbol) y Expr y `)`) /
                            argNoInit /
                            (
                                Operator.Ellipsis y `(` y
                                    "...".asSymbol(surpriseMeSymbol) y impliedValue(valueTrue, Left) y
                                    `)`
                                )
                        ) /
                        declTree(LetArg),
                ),
        ) /
            Garbage("Argument", stopBefore = closeBrackets + ",", requireSome = true)
    Arg `：＝` argActualOrFormal(ArgNoInit)
    Formal `：＝` argActualOrFormal(FormalNoInit, withDefault = ArgNoInit)

    fun argNoInitActualOrFormal(typeless: Combinator) = (
        ((Operator.HighColon / Operator.LowColon) y `(` y Pattern y ":".asSymbol(typeSymbol) y Type y `)`) /
            (
                supports y `(` y
                    // Having `reifies` and `:` on the same definition is redundant, but let's recognize it
                    // in the grammar
                    (
                        (
                            (Operator.HighColon / Operator.LowColon) y `(` y Pattern y
                                ":".asSymbol(typeSymbol) y Type y `)`
                            ) / Pattern
                        ) y
                    "supports".asSymbol(reifiesSymbol) y Type y `)`
                ) /
            typeless
        )
    ArgNoInit `：＝` argNoInitActualOrFormal(Pattern)
    // We need to impose extra complexArgSymbol for plain vars for some reason.
    FormalNoInit `：＝` argNoInitActualOrFormal(complexArgSymbol y Pattern)

    Pattern `：＝` (
        Operator.Ellipsis y `(` y "...".asSymbol(surpriseMeSymbol)
            // shift the expression left to get the symbol in the expected metadata location
            y Expr y shiftLeft
            y impliedValue(void) y `)`
        ) /
        Expr

    SymbolLiteral `：＝` (Operator.Esc y `(` y "\\" y SymbolValue y `)`)
    SymbolValue `：＝` (Operator.Leaf y `(` y value(wordMatcher) y `)`)

    QuasiTree `：＝` (Operator.EscCurly y `(` y "\\{" y any(QuasiHole / QuasiInner) y "}" y `)`)
    QuasiAst `：＝` (Operator.EscParen y escTree(`(` y "\\(" y Expr y ")" y `)`))
    Quasis `：＝` any(QuasiHole / QuasiInner / QuasiLeaf)
    QuasiHole `：＝` (
        Operator.DollarCurly y callTree(
            `(` y
                // Unescape can be recognized as a hole by a template processor.
                // TODO: Make sure this isn't ambiguous if a template mentions Unescape.
                "\${".rename(unholeBuiltinName) y funTree(CommaExpr) y "}" y
                `)`,
        )
        )
    QuasiInner `：＝` callTree(`(` y quasiInnerBuiltinName y Quasis y `)`)
    QuasiLeaf `：＝` callTree(
        quasiLeafBuiltinName y
            value(Match(GrammarDoc.Comment("any token"), true) { true }),
    )

    Json `：＝` (JsonValue / Garbage("Json"))
    JsonValue `：＝` (JsonObject / JsonArray / JsonString / JsonNumber / JsonBoolean / JsonNull)
    JsonObject `：＝` (
        Operator.CurlyGroup y callTree(
            `(` y "{".rename("{}") y
                (
                    (
                        Operator.Comma y `(` y
                            JsonProperty y any("," y JsonProperty) y opt(",") y `)`
                        ) /
                        opt(JsonProperty)
                    )
                y "}" y `)`,
        )
        )
    JsonProperty `：＝` (Operator.HighColon y `(` y JsonString y ":" y JsonValue y `)`)
    JsonArray `：＝` (
        Operator.SquareGroup y callTree(
            `(` y "[".rename("[]") y
                (
                    (Operator.Comma y `(` y JsonValue y any("," y JsonValue) y opt(",") y `)`) /
                        opt(JsonValue)
                    )
                y "]" y `)`,
        )
        )
    JsonString `：＝` (
        Operator.ParenGroup y `(` y syntheticToken y "(" y Operator.QuotedGroup y `(` y
            TokenType.LeftDelimiter y "\"" y
            (
                (Operator.Leaf y `(` y value(JoinStrings) y `)`) /
                    impliedValue(Value("", TString))
                ) y
            TokenType.RightDelimiter y "\"" y
            `)` y ")" y `)`
        )
    /** Truth values are represented using the keywords `false` and `true`. */
    JsonBoolean `：＝` (
        Operator.Leaf y `(` y (
            "false".revalue(TBoolean.valueFalse) /
                "true".revalue(TBoolean.valueTrue)
            ) y `)`
        )
    JsonNull `：＝` (Operator.Leaf y `(` y "null".revalue(TNull.value) y `)`)
    JsonNumber `：＝` (
        (Operator.PreDash y callTree(`(` y "-".rename("-") y `(` y value(numMatcher) y `)` y `)`)) /
            (Operator.Leaf y `(` y value(numMatcher) y `)`)
        )

    // /////////////////////////////////////// END GRAMMAR /////////////////////////////////////////

    grammar
}

// DSL definition

// A string with the operators below matches an exact token without emitting it.
private fun toCombinator(tokenText: String): Match =
    Match(GrammarDoc.Terminal(tokenText), emit = false) {
        it.tokenText == tokenText && it.tokenType != TokenType.Error
    }

// A lookahead for an open parenthesis with that type.
private fun toCombinator(t: OperatorType) = Lookahead(GrammarDoc.Skip) { (next) ->
    next is LeftParenthesis && next.operator.operatorType == t
}

// A lookahead for a token with that that type.
private fun toCombinator(t: TokenType) = Lookahead(GrammarDoc.Skip) { (next) ->
    next is CstToken && next.tokenType == t
}

// Emits an implied symbol without consuming any input.
private fun toCombinator(name: TemperName) = Implied { pos, output ->
    output.add(StartTree(pos))
    output.add(NamePart(pos = pos, name = name))
    output.add(FinishTree(pos, LeafTreeType.RightName))
}

// A lookahead for an open parenthesis with that type.
private fun toCombinator(o: Operator) = Lookahead(GrammarDoc.Skip) { (next) ->
    next is LeftParenthesis && next.operator == o
}
private fun toCombinator(os: Set<Operator>) = Lookahead(GrammarDoc.Skip) { (next) ->
    next is LeftParenthesis && next.operator in os
}
private fun toCombinator(symbol: Symbol) = Implied { pos, output ->
    output.add(StartTree(pos))
    output.add(ValuePart(Value(symbol), pos))
    output.add(FinishTree(pos, LeafTreeType.Value))
}
private fun toCombinator(value: Value<*>) = Implied { pos, output ->
    output.add(StartTree(pos))
    output.add(ValuePart(value, pos))
    output.add(FinishTree(pos, LeafTreeType.Value))
}

// The below define a DSL for declaring a grammar.
// The `/` operator means "or" in an analytic grammar.
private operator fun (Combinator).div(combinator: Combinator): Combinator =
    Or.new(listOf(this, combinator))
private operator fun (Combinator).div(tokenText: String) = this.div(toCombinator(tokenText))
private operator fun (String).div(combinator: Combinator) = toCombinator(this).div(combinator)
private operator fun (String).div(combinator: String) = toCombinator(this).div(toCombinator(combinator))
private operator fun (OperatorType).div(t: OperatorType) = toCombinator(this).div(toCombinator(t))
private operator fun (Operator).div(o: Operator) = toCombinator(this).div(toCombinator(o))
private operator fun (Set<Operator>).div(combinator: Combinator) =
    toCombinator(this).div(combinator)
private operator fun (Combinator).div(name: TemperName) = this.div(toCombinator(name))
private operator fun (Combinator).div(o: Operator) = this.div(toCombinator(o))

// We use infix `y` to mean concatenation; think the Spanish conjunction.
private infix fun (Combinator).y(combinator: Combinator): Combinator =
    Cat.new(listOf(this, combinator))
private infix fun (Combinator).y(tokenText: String): Combinator = this.y(toCombinator(tokenText))
private infix fun (String).y(c: Combinator): Combinator = toCombinator(this).y(c)
private infix fun (TemperName).y(c: Combinator): Combinator = toCombinator(this).y(c)
private infix fun (Symbol).y(c: Combinator): Combinator = toCombinator(this).y(c)

// An OperatorType asserts that the next part is a left parenthesis with that as its operator.
// TODO: more efficient to judiciously use a switch over the operator type.
private infix fun (OperatorType).y(c: Combinator): Combinator = toCombinator(this).y(c)
private infix fun (Operator).y(c: Combinator): Combinator = toCombinator(this).y(c)
private infix fun (TokenType).y(c: Combinator): Combinator = toCombinator(this).y(c)
private infix fun (Set<Operator>).y(c: Combinator): Combinator = toCombinator(this).y(c)
private infix fun (Combinator).y(o: Operator): Combinator = this.y(toCombinator(o))
private infix fun (Combinator).y(t: TokenType): Combinator = this.y(toCombinator(t))

// Adding a name that emits an implicit name that starts where the next token consumed ends.
private infix fun (Combinator).y(name: TemperName): Combinator = this.y(toCombinator(name))
private infix fun (Combinator).y(symbol: Symbol): Combinator = this.y(toCombinator(symbol))
private infix fun (Combinator).y(value: Value<*>): Combinator = this.y(toCombinator(value))

// We use `!` to mean negative lookahead.
@Suppress("UnusedPrivateMember") // misfire
private operator fun (Combinator).not(): Combinator = NegLA(this)

/** many(x) acts like Kleene-plus: one or more. */
private fun many(c: Combinator) = Rep(c)

/** any(x) acts like Kleene-star: zero or more. */
private fun any(c: Combinator) = opt(many(c))

// opt(x) means optional; equivalent to (x / epsilon)
private fun opt(combinator: Combinator): Combinator = Or.new(listOf(combinator, epsilon))
private fun opt(tokenText: String): Combinator = opt(toCombinator(tokenText))

// Make a matcher from a regex.
private fun patternMatcher(pattern: String, emit: Boolean): Match {
    val re = Regex(pattern)
    val description = GrammarDoc.Comment("/${re.pattern}/")
    return Match(description, emit = emit) {
        it.tokenType != TokenType.Error && re.matches(it.tokenText)
    }
}
private const val MAX_AUTO_CHOICE_SIZE = 8
private fun isOperator(type: OperatorType, exclude: Set<Operator>): Match {
    val matching = Operator.entries.filter { it.operatorType == type && it !in exclude }
    val commentary = buildString {
        append(type.name).append(" operators")
        if (exclude.isNotEmpty()) {
            append(" but not ")
            exclude.joinTo(this, separator = ", ") {
                it.text?.let { text -> "`$text`" } ?: it.name
            }
        }
    }
    val description = GrammarDoc.Group(
        if (matching.size <= MAX_AUTO_CHOICE_SIZE && matching.all { it.text != null }) {
            val operators = Operator.entries.mapNotNull {
                if (it.operatorType != type || it in exclude) { return@mapNotNull null }
                GrammarDoc.Terminal(it.text!!, title = it.name)
            }
            GrammarDoc.Choice(0, operators)
        } else {
            GrammarDoc.NonTerminal("${type}Operators")
        },
        GrammarDoc.Comment(commentary),
    )

    return Match(description, true) {
        Operator.matching(it.tokenText, it.tokenType, type).any { o -> o !in exclude }
    }
}

/**
 * Bundle a bunch of variables into a class so that we can extend it in [ProductionNames] and have
 * them available via `this` in `ProductionNames.run { ... }` above.
 */
@Suppress("UnnecessaryAbstractClass")
internal abstract class DomainSpecificLanguage {
    val commaFnValue = BuiltinFuns.vCommaFn
    val thisFnValue = BuiltinFuns.vThis
    val valueTrue = TBoolean.valueTrue
    val valueFalse = TBoolean.valueFalse

    val stmtContinueOps = Operator.entries.filter { it.continuesStatement }.toSet()

    // A matcher for identifier tokens that need to be part of a leaf.
    val unreservedWordMatcher = Match(GrammarDoc.NonTerminal("UnreservedWord"), emit = true) {
        it.tokenType == TokenType.Word && it.tokenText !in reservedWords
    }
    val wordMatcher = Match(GrammarDoc.NonTerminal("Word"), emit = true) {
        it.tokenType == TokenType.Word
    }
    val delimiterMatcher = patternMatcher("^\"+|'$", emit = false)
    val delimiterMatcherBackticks = patternMatcher("^\"+|['`]$", emit = false)
    val litMatcher = Match(
        GrammarDoc.NonTerminal("Number"),
        emit = true,
    ) {
        it.tokenType == TokenType.Number
    }
    val numMatcher = Match(GrammarDoc.NonTerminal("Number"), true) {
        it.tokenType == TokenType.Number
    }

    val builtinNameMatcher = object : Combinator {
        override fun apply(context: CombinatorContext<*>, position: Int): Int {
            val part = context.input.getOrNull(position)
            if (part is CstToken && part.tokenType == TokenType.Word) {
                val parsedName = decodeName(part.tokenText)
                if (parsedName != null) {
                    context.output.add(NamePart(BuiltinName(parsedName.nameText), part.pos))
                    return position + 1
                }
            }
            return -1
        }

        override fun toGrammarDocDiagram(
            g: Productions<*>,
            inlinable: (Ref) -> Boolean,
        ): GrammarDoc.Component = GrammarDoc.NonTerminal("BuiltinName")

        override val children: Iterable<Combinator> get() = emptyList()
    }

    val syntheticToken = Lookahead(GrammarDoc.Skip) {
        val next = it.next
        next is CstToken && next.synthetic
    }

    fun startSplitTree() = EmitBefore(epsilon) { p -> StartTree(p) }
    private fun finishSplitTree(tt: FinishedType) = EmitBefore(epsilon) { p -> FinishTree(p, tt) }
    fun finishSplitCall() = finishSplitTree(FinishedTreeType(InnerTreeType.Call))
    fun finishSplitCommaSoft() = finishSplitTree(SoftComma)

    private fun tree(combinator: Combinator, finishedType: FinishedType): Combinator =
        EmitAfter(
            EmitBefore(combinator) { p ->
                StartTree(p)
            },
        ) { _, p ->
            listOf(FinishTree(p, finishedType))
        }

    private fun tree(combinator: Combinator, treeType: TreeType): Combinator =
        tree(combinator, FinishedTreeType(treeType))

    fun blockTree(combinator: Combinator) = tree(combinator, InnerTreeType.Block)
    fun callTree(combinator: Combinator) = tree(combinator, InnerTreeType.Call)
    fun declTree(combinator: Combinator) = tree(combinator, InnerTreeType.Decl)
    fun escTree(combinator: Combinator) = tree(combinator, InnerTreeType.Esc)
    fun funTree(combinator: Combinator) = tree(combinator, InnerTreeType.Fun)

    fun softBlockTree(combinator: Combinator) = tree(combinator, SoftBlock)
    fun softCommaTree(combinator: Combinator) = tree(combinator, SoftComma)

    fun leftName(combinator: Combinator) = tree(combinator, LeafTreeType.LeftName)
    fun name(combinator: Combinator) = tree(combinator, LeafTreeType.RightName)
    fun value(combinator: Combinator) = tree(combinator, LeafTreeType.Value)

    fun impliedValue(v: Value<*>, bias: LeftOrRight = Right) = value(
        Implied(bias = bias) { p, out ->
            out.add(ValuePart(v, p))
        },
    )

    fun Combinator.rename(values: Map<String, Either<TemperName, Value<*>>>): Combinator = name(
        Where(
            describe = this::toGrammarDocDiagram,
            filter = this,
        ) { text, p ->
            when (val v = values[text]) {
                null -> NamePart(ParsedName(text), p)
                is Either.Left -> NamePart(v.item, p)
                is Either.Right -> ValuePart(v.item, p)
            }
        },
    )
    fun name(name: String) =
        name(Match(GrammarDoc.Terminal(name), true) { it.tokenText == name })

    fun String.rename(name: TemperName): Combinator = name(
        Where(tokenText = this) { _, p ->
            NamePart(name, p)
        },
    )

    fun String.asSymbol(symbol: Symbol): Combinator {
        val symbolValue = Value(symbol)
        return value(
            Where(tokenText = this) { _, p ->
                ValuePart(symbolValue, p)
            },
        )
    }

    fun String.asValue(): Combinator = value(
        Where(tokenText = this) { _, p ->
            ValuePart(Value(this, TString), p)
        },
    )

    fun String.rename(name: String): Combinator {
        val tt = if (decodeUtf16(name, 0) in IdParts.Start) {
            TokenType.Word
        } else {
            TokenType.Punctuation
        }
        return name(
            Where(tokenText = this) { _, pos ->
                TokenLeaf(
                    CstToken(
                        TemperToken(pos, name, tt, mayBracket = false, synthetic = false),
                    ),
                )
            },
        )
    }

    fun String.revalue(value: Value<*>): Combinator = value(
        Where(tokenText = this) { _, p ->
            ValuePart(value, p)
        },
    )

    // `(` and `)` are how we express pseudo tokens input matches.
    // @JsName("open")
    @Suppress("PropertyName", "VariableNaming", "Unused")
    val `(` = Match(GrammarDoc.Skip) { it is LeftParenthesis }

    // @JsName("close")
    @Suppress("PropertyName", "VariableNaming", "Unused")
    val `)` = Match(GrammarDoc.Skip) { it is RightParenthesis }

    val shiftLeft = EmitAfter(Cat.empty) { _, pos -> listOf(ShiftLeft(pos)) }

    /**
     * <!-- snippet: syntax/bag-preceders -->
     * # Blocks vs bags
     * Putting `do` before curly brackets, `do {⋯}` specifies a block of statements, not a bag of
     * key/value properties à la JSON.
     *
     * ```temper inert
     * // A bag of properties
     * {
     *    key₀: value₀,
     *    key₁: value₁
     * }
     *
     * // A series of statements
     * do {
     *   statement₀;
     *   label: statement₁
     * }
     * ```
     *
     * Temper uses a simple rule to decide whether `{⋯}` surrounds properties or statements because
     * there can be confusion otherwise.
     *
     * ```temper inert
     * { labelOrKey: valueOrStatement } // ?
     *
     * { /* are empty brackets a group of zero properties or zero statements? */ }
     *
     * // This is complicated with "punning": when a key and value have the same text.
     * { x } // may be equivalent to `{ x: x }` in a property context.
     * // Within small or heavily punned brackets, there are fewer cues like colons,
     * // commas, or semicolons.
     * ```
     *
     * Whether a `{` is followed by properties or statements is made based on the preceding
     * non-comment & non-space token.
     *
     * | Preceding Token Text        | Contains   | Classification                              |
     * | --------------------------- | ---------- | ------------------------------------------- |
     * | `let`, `var`, `const`       | Properties | declaration                                 |
     * | `new`                       | Properties | new value context                           |
     * | *no preceding token*        | Properties | the start of a module                       |
     * | `]`, `)`, *Angle* `>`, `=>` | Statements | block or block lambda                       |
     * | *word*                      | Statements | block or block lambda                       |
     * | *anything else*             | Properties | default is bag                              |
     */
    val isBagPreceder = isBagPreceder@{ prefix: Prefix ->
        val prevToken = prefix.prevToken
        when (val tokenText = prevToken?.tokenText) {
            null -> true // As if curlies at start of file start a bag.
            // Since TokenSourceAdapter inserts "let" tokens after "var" and "const", those are
            // effectively bag preceders.
            // "await", "return", "throw", and "yield" denote operators that expect expressions.
            "orelse", "let", "new", "await", "return", "throw", "yield" -> true
            "=>" -> false
            // Distinguish greater than from angle brackets before we get to general `closeBrackets`
            // The actual operator might be Angle or ExtendsComma (or others?).
            ">" -> !prevToken.token.mayBracket
            else -> when {
                tokenText in closeBrackets -> false
                tokenText in typePostFixers -> false
                prevToken.tokenType == TokenType.Word -> false
                else -> true
            }
        }
    }

    val propertyBagPreceder = Lookbehind(GrammarDoc.NonTerminal("BagPreceder"), isBagPreceder)
    val blockPreceder = Lookbehind(GrammarDoc.NonTerminal("!BagPreceder")) { !isBagPreceder(it) }
    val infixColons = setOf(Operator.LowColon, Operator.HighColon)
    val supports = setOf(Operator.SupportsNoComma, Operator.SupportsComma)
}

// Some versions of Kotlin fail on `Operator.EnumValue.text!!`
// https://github.com/temperlang/temper/runs/2128539272
// This works around.
private val (Operator).textNotNull: String get() = this.text!!

/** Either single arg or else nested comma expression with multiple args and optional trailing comma. */
private fun argsActualOrFormal(arg: Ref) = (
    (Operator.Comma y `(` y opt(arg y any("," y arg)) y opt(",") y `)`) /
        arg /
        Garbage("Arguments", stopBefore = setOf(")", "=>"), requireSome = true)
    )

/** Either single arg or else nested comma expression with multiple args but no trailing comma. */
private fun argsActualOrFormalNoTrailingComma(arg: Ref) = (
    (Operator.Comma y `(` y opt(arg y any("," y arg)) y `)`) /
        arg /
        Garbage("Arguments", stopBefore = setOf(")", "=>"), requireSome = true)
    )

private val typePostFixers = setOf("!", "?")

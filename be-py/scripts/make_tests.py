#!/usr/bin/env python3

"""
This script generates the python OutGrammarTest.kt file given a list of example Python snippets.
It parses each snippet using the standard `ast` module, modifies the AST tree to conform to our out-grammar,
and generates a test to confirm that the out-grammar generates the same Python.
"""

import ast
from dataclasses import dataclass
import sys
import json
from itertools import chain
from enum import Enum


def kt_str(val):
    assert isinstance(val, str), f"Got {cn(val)} instead of str"
    return json.dumps(val)


def kt_str_long(val):
    assert isinstance(val, str), f"Got {cn(val)} instead of str"
    if len(val) > SPLIT_LONG_STR and "\n" in val:
        return KtCall("joinLines", [kt_str(line) for line in val.split("\n") if line])
    else:
        return json.dumps(val)


def kt_num(val):
    assert not isinstance(val, bool) and isinstance(
        val, (float, int)
    ), f"Got {cn(val)} instead of number"
    return json.dumps(val)


def kt_bool(val):
    assert isinstance(val, bool), f"Got {cn(val)} instead of boolean"
    return json.dumps(val)


MAX_LINE_LENGTH = 120
SPLIT_LONG_STR = 90
INDENT = 4


class Emission:
    def elems(self, indent):
        yield from ()
        return NotImplemented

    def flatten(self, indent, pre="", post=""):
        parts = list(self.elems(indent))
        size = (
            indent * INDENT + len(pre) + sum(len(s) + 1 for _, s in parts) + len(post)
        )
        if size <= MAX_LINE_LENGTH:
            return [(indent, pre + " ".join(s for _, s in parts) + post)]
        else:
            if pre:
                parts[0] = parts[0][0], pre + parts[0][1]
            if post:
                parts[-1] = parts[-1][0], parts[-1][1] + post
            return parts

    @staticmethod
    def upgrade(indent, item):
        if isinstance(item, Emission):
            return item.elems(indent)
        elif isinstance(item, str):
            return [(indent if item else 0, item)]
        else:
            raise TypeError(f"Not an Emission: {cn(item)}")

    def __str__(self):
        return " ".join(s for _, s in self.elems(0))


@dataclass
class KtTest(Emission):
    name: str
    actual: Emission
    expected: str
    imports: list[str]
    exports: list[str]

    def elems(self, i):
        ii = i + 1
        yield i, f"private fun ast{self.name}() ="
        yield from self.actual.flatten(ii)
        yield 0, ""
        yield i, "@Test"
        yield i, f"fun test{self.name}Out() {{"
        yield from KtVal("expected", self.expected).flatten(ii)
        yield ii, f"assertEqualCode(expected, ast{self.name}())"
        yield i, "}"
        yield 0, ""
        yield i, "@Test"
        yield i, f"fun test{self.name}Exports() {{"
        yield from KtVal.set_of(
            "expected", "String", map(kt_str, self.exports)
        ).flatten(ii)
        yield ii, f"assertExports(expected, ast{self.name}())"
        yield i, "}"
        yield 0, ""
        yield i, "@Test"
        yield i, f"fun test{self.name}Imports() {{"
        yield from KtVal.set_of(
            "expected", "String", map(kt_str, self.imports)
        ).flatten(ii)
        yield ii, f"assertImports(expected, ast{self.name}())"
        yield i, "}"


@dataclass
class KtVal(Emission):
    name: str
    expr: Emission

    def elems(self, i):
        if isinstance(self.expr, str):
            yield (i, f"val {self.name} = {self.expr}")
        else:
            yield from self.expr.flatten(i, pre=f"val {self.name} = ")

    @classmethod
    def set_of(cls, name, typ, elems):
        if elems := list(elems):
            head = "setOf"
        else:
            head = f"setOf<{typ}>"
        return cls(name, KtCall(head, elems))


@dataclass
class KtCall(Emission):
    head: str
    args: list

    def __post_init__(self):
        assert isinstance(self.args, list)

    def elems(self, i):
        args = self.args
        if not args:
            yield (i, f"{self.head}()")
            return

        def do_arg(arg, comma):
            pre = ""
            if isinstance(arg, tuple):
                name, arg = arg
                pre = f"{name} = "
            if isinstance(arg, str):
                yield i + 1, f"{pre}{arg}{comma}"
            elif isinstance(arg, Emission):
                yield from arg.flatten(i + 1, pre, comma)
            else:
                raise TypeError(f"Can't process {cn(arg)}")

        yield i, f"{self.head}("
        for arg in args[:-1]:
            yield from do_arg(arg, ",")
        yield from do_arg(args[-1], "")
        yield i, f")"


def try_dump(val):
    if isinstance(val, ast.AST):
        return ast.dump(val)
    else:
        return repr(val)


def cn(o):
    return o.__class__.__name__


class redict(dict):
    "Rename dictionary that returns the original if it's not renamed."

    def __missing__(self, key):
        return key


class PosStash:
    "Save positions as a list of private vals for brevity in the AST."

    def __init__(self):
        self.stash = set()

    def name(self, key):
        if key:
            ln, co, el, ec = key
            name = f"l{ln}c{co}"
            if el != ln:
                name += f"l{el}"
            if ec != (co + 1):
                name += f"c{ec}"
            return name
        return "p0"

    def get(self, n):
        try:
            ln, co = n.lineno, n.col_offset
            key = ln, co, getattr(n, "end_lineno", ln), getattr(n, "end_col_offset", co)
        except AttributeError:
            key = ()
        else:
            self.stash.add(key)
        return self.name(key)

    def invoke(self, key):
        if key:
            ln, co, el, ec = key
            return f"ktPos({ln}, {co}, {el}, {ec})"
        else:
            return "p0"

    def __missing__(self, key):
        val = self[key] = self.name(key)
        return val

    def definition(self):
        for key in sorted(self.stash):
            yield f"private val {self.name(key)} = {self.invoke(key)}"


class FakeAst:
    "Sometimes it's convenient to fake a bit of AST to Convert.node()."

    def __init__(self, src, **kw):
        for a in "lineno", "col_offset", "end_lineno", "end_col_offset":
            if a in kw:
                v = kw[a]
            elif hasattr(src, a):
                v = getattr(src, a)
            else:
                continue
            setattr(self, a, v)


class Decorator(FakeAst):
    """
    PEP 614 / Python 3.9 relaxed grammar restrictions on decorators, but we want to be compatible.
    If we need this functionality, we can hack it as described in the pep, e.g.

        def identity(arg):
            return arg

        @identity(any_expression)
    """

    def __init__(self, node):
        super().__init__(node)

        def msg():
            return f"Couldn't downgrade a decorator expression: {try_dump(node)}"

        def unroll_attributes(n):
            "Convert a.b.c.d to ('a', 'b', 'c', 'd')"
            if isinstance(n, ast.Name):
                return (n.id,)
            elif isinstance(n, ast.Attribute):
                return unroll_attributes(n.value) + (n.attr,)
            else:
                raise TypeError(msg())

        if isinstance(node, ast.Call):  # @foo() style.
            self.name = unroll_attributes(node.func)
            self.args = CallArg.list_of(node)
            self.called = True
        elif isinstance(node, (ast.Name, ast.Attribute)):
            self.name = unroll_attributes(node)
            self.args = []
            self.called = False
        else:
            raise TypeError(msg())

    _fields = ["name", "args", "called"]


ArgPrefix = Enum("ArgPrefix", ["None", "Star", "DoubleStar"])


class Arg(FakeAst):
    "A signature argument."

    def __init__(self, src, arg=None, ann=None, prefix=ArgPrefix["None"]):
        super().__init__(src)
        self.arg = arg or getattr(src, "arg", None)
        self.annotation = ann or getattr(src, "annotation", None)
        self.defaultValue = None
        self.prefix = prefix

    _fields = ["arg", "annotation", "defaultValue", "prefix"]

    @classmethod
    def _set_defaults(cls, arglist, deflist):
        i = iter(reversed(arglist))
        for d in reversed(deflist):
            next(i).defaultValue = d

    @classmethod
    def list_of(cls, node):
        "Given an ast.Arguments, construct a list of Arg instances."
        pos_args = [cls(arg) for arg in chain(node.posonlyargs, node.args)]
        cls._set_defaults(pos_args, node.defaults)

        vararg = [
            cls(arg, prefix=ArgPrefix.Star) for arg in [node.vararg] if arg is not None
        ]

        kw_only = [cls(arg) for arg in node.kwonlyargs]
        cls._set_defaults(kw_only, node.kw_defaults)

        kwarg = [
            cls(arg, prefix=ArgPrefix.DoubleStar)
            for arg in [node.kwarg]
            if arg is not None
        ]

        return pos_args + vararg + kw_only + kwarg


class CallArg(FakeAst):
    def __init__(self, src, arg, value, keyword):
        super().__init__(src)
        self.arg = arg
        self.value = value
        self.prefix = (
            ArgPrefix.DoubleStar if keyword and arg is None else ArgPrefix["None"]
        )

    _fields = ["arg", "value", "prefix"]

    @classmethod
    def list_of(cls, node, args="args", keywords="keywords"):
        "Given an ast.Call construct a list of CallArg instances."
        posargs = [cls(arg, None, arg, False) for arg in getattr(node, args)]
        keywords = [cls(kw, kw.arg, kw.value, True) for kw in getattr(node, keywords)]
        return posargs + keywords


class Elif(FakeAst):
    """
    We generally want ifs nested in orelse to become elifs.
    If(A, _, orelse=If(B, _, orelse=If(C, _, orelse=X)) ->
    If(A, elifs=[Elif(B, _), Elif(C, _)], orelse=X)
    """

    def __init__(self, src):
        super().__init__(src)
        self.test = src.test
        self.body = src.body

    _fields = ["test", "body"]

    @classmethod
    def extract(cls, node):
        elifs = []
        node = node.orelse
        while len(node) == 1 and isinstance(node[0], ast.If):
            elifs.append(cls(node[0]))
            node = node[0].orelse
        return elifs, node


class Convert:
    def __init__(self, namespace="Py"):
        self.namespace = namespace  # Our py.out-grammar is compiling to this Py class.
        self.pos_stash = PosStash()
        self._src = ""

    def go(self, src):
        "Entry point. Parses src and converts to Kotlin AST constructors."
        self._src = src
        return self.node(ast.parse(src))

    def node(self, node):
        "Dispatches based on the node type. See the n_AST method for the generic approach."
        for klass in type(node).mro():
            method_name = "n_" + klass.__name__
            method = getattr(self, method_name, None)
            if method:
                try:
                    return method(node)
                except Exception as exc:
                    print(
                        f"{cn(self)}.{method_name} failing on {try_dump(node)}",
                        file=sys.stderr,
                    )
                    raise
        raise TypeError("Pretty sure we defined an object method.")

    def py(self, name, pos=None, *args, **kw):
        "Gets a Py.[name] constructor."
        assert isinstance(name, str)
        a = [self.pos(pos)]
        a.extend(list(args))
        a.extend((k.rstrip("_"), v) for k, v in kw.items())
        return KtCall(f"{self.namespace}.{name}", a)

    def pin(self, val, src=None):
        "Convenience to construct a simple name"
        if val.startswith('"'):
            raise TypeError("Usually shouldn't be quoted")
        # if '.' in val:
        #    raise TypeError("Hmmm dots aren't gud")
        # print(f'self.pin({val!r})', file=sys.stderr)
        return f"PyIdentifierName({kt_str(val)})"

    def identifier(self, val):
        "Some output nodes embed a PIN in an Identifier node."
        return self.py("Identifier", id=self.pin(val))

    def _mod_ref(self, val):
        return f"PyDottedIdentifier({kt_str(val)})"

    def pos(self, node=None):
        return self.pos_stash.get(node)

    renamed_types = redict(
        {
            "List": "ListExpr",
            "Set": "SetExpr",
            "Expr": "ExprStmt",
            "IfExp": "IfExpr",
            "UnaryOp": "UnaryExpr",
            "BinOp": "BinExpr",
            "alias": "ImportAlias",
            "arg": "Arg",
            "withitem": "WithItem",
        }
    )

    renamed_fields = redict(
        {
            "decorator_list": "decoratorList",
            "context_expr": "contextExpr",
            "optional_vars": "optionalVars",
            "orelse": "orElse",  # orelse is now reserved by grammar
        }
    )

    enum_renames = redict(
        {
            "UAdd": "UnaryAdd",
            "USub": "UnarySub",
            "Invert": "UnaryInvert",
            "BitOr": "BitwiseOr",
            "BitXor": "BitwiseXor",
            "BitAnd": "BitwiseAnd",
            "And": "BoolAnd",
            "Not": "BoolNot",
            "Or": "BoolOr",
        }
    )

    def con_name(self, node):
        return self.renamed_types[cn(node)]

    def field(self, node, value):
        if value is None:
            return None
        return self.node(value)

    def _node(self, node, _con=None, _pos=None, **extra):
        args = [node] if _pos is None else [_pos]
        for f, v in extra.items():
            args.append((self.renamed_fields[f.rstrip("_")], v))

        nn = cn(node)

        for f in node._fields:
            if f in extra:
                continue
            v = getattr(node, f, None)
            method = getattr(self, f"f_{nn}_{f}", None)
            if method is None:
                method = getattr(self, f"f_{f}", self.field)
            v = method(node, v)
            if v:
                args.append((self.renamed_fields[f], v))
        if _con is None:
            _con = self.con_name(node)
        return self.py(_con, *args)

    def _field_ignore(self, node, value):
        return None

    f_type_ignores = f_type_comment = f_ctx = f_AnnAssign_simple = _field_ignore

    def _field_pin(self, node, value):
        return self.pin(value)

    def _field_identifier(self, node, value):
        assert value is not None, f"bad node: {try_dump(node)}"
        return self.identifier(value)

    def _field_nullable_identifier(self, node, value):
        if value is None:
            return "null"
        return self.identifier(value)

    f_id = _field_pin
    f_name = _field_identifier
    f_alias_asname = (
        f_CallArg_arg
    ) = f_Arg_arg = f_ExceptHandler_name = _field_nullable_identifier
    f_Attribute_attr = _field_identifier

    def f_slice(self, node, value):
        return self.node(value if isinstance(value, list) else [value])

    def _field_list_ids(self, node, value):
        return self.kt_list_of(map(self.identifier, value))

    f_Global_names = f_Nonlocal_names = _field_list_ids

    def f_AugAssign_op(self, node, value):
        return f"AugAssignOpEnum.{self.enum_renames[cn(value)]}.atom({self.pos(node)})"

    def f_ImportFrom_module(self, node, value):
        return self.py(
            "ImportDotted", node, module=self._mod_ref(("." * node.level) + value)
        )

    def f_alias_name(self, node, value):
        return self.py("ImportDotted", node, module=self._mod_ref(value))

    f_ImportFrom_level = _field_ignore

    def _legacy_decorator_list(self, node, value):
        return self.kt_list_of(self.node(Decorator(dec)) for dec in value)

    f_decorator_list = _legacy_decorator_list

    def f_Decorator_name(self, node, value):
        "We get a tuple ('foo', 'bar', 'qux') and need a list of Identifiers."
        return self.kt_list_of(map(self.identifier, value))

    def n_object(self, node):
        "Other objects just return their stringified form."
        print(f"Fallback to repr for {node!r}; {cn(node)}", file=sys.stderr)
        return str(node)

    def n_Enum(self, node):
        "Py enums can translate straight to enums in the outgrammar, e.g. ArgPrefix"
        return f"{self.namespace}.{cn(node)}.{node.name}"

    def n_Name(self, node):
        if node.id == "NotImplemented":
            return self.py("Constant", node, value=f"PyConstant.NotImplemented")
        else:
            return self._node(node)

    def n_str(self, node):
        return kt_str(node)

    def n_bool(self, node):
        return kt_bool(node)

    def n_int(self, node):
        return kt_num(node)

    def kt_list_of(self, elems):
        return KtCall("listOf", list(elems))

    def n_list(self, node):
        "Use Kotlin listOf"
        return self.kt_list_of(map(self.node, node))

    def n_AST(self, node):
        # All AST nodes inherit from this.
        return self._node(node)

    def n_FakeAst(self, node):
        # All AST nodes synthesized here inherit from this.
        return self._node(node)

    def n_arguments(self, node):
        """
        Arguments in Python are a list of first positional, then varargs, the kwargs, etc.
        This is annoying to represent, so I just have Arg objects that can have prefixes,
        and correctness is enforced by the constructor.
        """
        arg_list = self.node(Arg.list_of(node))
        return self.py("Arguments", node, args=arg_list)

    def n_Call(self, node):
        arg_list = self.node(CallArg.list_of(node))
        return self.py("Call", node, func=self.node(node.func), args=arg_list)

    def n_AsyncFor(self, node):
        return self._node(node=node, _con="For", async_="true")

    def n_AsyncWith(self, node):
        return self._node(node=node, _con="With", async_="true")

    def n_AsyncFunctionDef(self, node):
        return self._node(node=node, _con="FunctionDef", async_="true")

    def n_ClassDef(self, node):
        return self._node(
            node=node, args=self.node(CallArg.list_of(node, args="bases"))
        )

    f_ClassDef_bases = f_ClassDef_keywords = _field_ignore

    def n_Compare(self, node):
        assert len(node.ops) == 1, "We don't generate chained comparisons"
        left = self.node(node.left)
        op = self.node(node.ops[0])
        right = self.node(node.comparators[0])
        return self.py("BinExpr", node, left=left, op=op, right=right)

    def n_BoolOp(self, node):
        # Boolean operations are unrolled in the AST.
        op = self.node(node.op)
        # a and b and c and d = ((a and b) and c) and d
        result = self.node(node.values[0])
        for val in node.values[1:]:
            result = self.py("BinExpr", val, op=op, left=result, right=self.node(val))
        return result

    def n_Constant(self, node):
        val = node.value
        if isinstance(val, bool) or val is None or val is Ellipsis:
            return self.py("Constant", node, value=f"PyConstant.{val!r}")
        if isinstance(val, (int, float)):
            return self.py("Num", node, n=kt_num(val))
        elif isinstance(val, str):
            return self.py("Str", node, s=kt_str(val))
        else:
            raise TypeError(f"Unknown Constant type {val!r}; {cn(val)}")

    def n_If(self, node):
        "Flatten an elif chain."
        elifs, orelse = Elif.extract(node)
        return self._node(node=node, elifs=self.node(elifs), orelse=self.node(orelse))

    def n_Module(self, node):
        "Fix the module's position."
        src = self._src
        if not src.endswith("\n"):
            src += "\n"
        lines = src.split("\n")[:-1]
        assert lines, f"No code in {source!r}"
        return self._node(
            node=node,
            _con="Program",
            _pos=FakeAst(
                None,
                lineno=1,
                end_lineno=len(lines),
                col_offset=0,
                end_col_offset=len(lines[-1]),
            ),
        )

    def n_unaryop(self, node):
        return f"UnaryOpEnum.{self.enum_renames[cn(node)]}.atom({self.pos(node)})"

    # Comparison operators do chaining, but I don't understand why Python has three forms of binary operator.
    def n_binaryop(self, node):
        return f"BinaryOpEnum.{self.enum_renames[cn(node)]}.atom({self.pos(node)})"

    def n_boolop(self, node):
        return f"BinaryOpEnum.{self.enum_renames[cn(node)]}.atom({self.pos(node)})"

    def n_operator(self, node):
        return f"BinaryOpEnum.{self.enum_renames[cn(node)]}.atom({self.pos(node)})"

    def n_cmpop(self, node):
        return f"BinaryOpEnum.{self.enum_renames[cn(node)]}.atom({self.pos(node)})"


class Tests:
    def __init__(self, outfh=sys.stdout, namespace="Py"):
        self.convert = Convert(namespace=namespace)
        self.outfh = outfh
        self.source = "<no test>"

    def emit(self, *items):
        fh = self.outfh
        indent = 0
        for item in items:
            if isinstance(item, int):
                indent = item
            elif isinstance(item, (str, Emission)):
                for i, elem in Emission.upgrade(indent, item):
                    print(" " * (i * INDENT) + elem.rstrip("\n"), file=fh)
            else:
                raise TypeError(f"Can't emit {cn(item)}")

    def __enter__(self):
        self.emit(
            0,
            '@file:lang.temper.common.Generated("make_tests.py")',
            '@file:Suppress("UnderscoresInNumericLiterals", "SpellCheckingInspection")',
            "",
            "package lang.temper.be.py",
            "",
            "import kotlin.test.Test",
            "import lang.temper.log.unknownPos as p0",
            "",
            "/** Runs some simple tests of the correctness of py.out-grammar */",
            "class OutGrammarTest {",
        )
        return self

    def __call__(self, name, source, extra):
        self.source = source
        actual = self.convert.go(source)
        self.emit(
            1,
            KtTest(
                name,
                actual,
                expected=kt_str_long(source),
                imports=extra.get("import", []),
                exports=extra.get("export", []),
            ),
        )
        self.source = "<no test>"
        print(f"doing {name}")

    def __exit__(self, etype, exc, tb):
        if etype is not None:
            print(f"failing on {self.source}", file=sys.stderr)
            return
        self.emit("", *self.convert.pos_stash.definition(), 0, "}")


class Unique:
    def __init__(self):
        self.used = set()

    def find(self, name):
        for tail in range(1, 100):
            if (cand := f"{name}{tail}") not in self.used:
                self.used.add(cand)
                return cand
        raise ValueError("Lots of conflicting names")

    def __call__(self, name):
        return self.find(name)


unique_name = Unique()


def process(infh, handle):
    name = None
    extra = {}
    code = []
    for line in infh:
        words = line.split()
        if not words or words[0] == "##":
            continue
        if words[0].startswith("#:"):
            extra[words[0][2:]] = [word.strip(",") for word in words[1:]]
            continue
        if words[0] == "#":
            if name:
                handle(name, "".join(code), extra)
            name = unique_name("".join(word.capitalize() for word in words[1:]))
            code = []
            extra = {}
        else:
            code.append(line)
    if name:
        handle(name, "".join(code), extra)


if __name__ == "__main__":
    with open(sys.argv[2], "w") as outfh:
        with Tests(outfh=outfh) as handler, open(sys.argv[1]) as infh:
            process(infh, handler)

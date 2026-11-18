//! The core constructs that macros reduce everything to
//! (notes/history/milestone-3.md), as node kinds of the shared tree, with their
//! layouts:
//!
//! | Kind | Children | Data |
//! |---|---|---|
//! | `Block` | statements; its value is the last one's | |
//! | `If` | condition, then, else (an empty block if none) | |
//! | `Loop` | body, then `next` if any, run after the body and on `continue` | [`LOOP_NEXT`], [`LOOP_EACH`] |
//! | `Break`, `Continue` | | the target `Block` or `Loop`, or for `continue` in `for … of`, its body `Fn` |
//! | `Return` | value | the `Fn` |
//! | `Yield` | value (null for a bare `yield`) | the generator `Fn` |
//! | `Await` | promise | the generator `Fn` (an `async` block's) |
//! | `Orelse` | body, fallback | |
//! | `Fail` | | |
//! | `Fn` | type parameters, parameters (each a `Let`), return type if any, supertypes, body unless [`FN_NO_BODY`] | parameter count, supertype count << [`FN_SUPERS_SHIFT`], type parameter count << [`TYPE_PARAMS_SHIFT`], [`FN_OUT_TYPE`], [`FN_LOOP_BODY`], [`FN_NO_BODY`] |
//! | `Let` | `LeftName`, type if any, initial value if any | [`LET_VAR`], [`LET_TYPE`], [`LET_INIT`] |
//! | `Assign` | `RightName` or `obj.name` (a `.` call), value | |
//! | `Call` | callee, arguments, with type arguments as `\typeArg T` pairs | |
//! | `Class` | `LeftName`, type parameters, supertypes, members (each a `Let`) | supertype count, type parameter count << [`TYPE_PARAMS_SHIFT`], [`CLASS_INTERFACE`], [`CLASS_AUTO_EQ`], [`CLASS_FUN`], [`CLASS_CONNECTED`], [`CLASS_CONNECTED_LIST`], [`CLASS_SEALED`] |
//!
//! Plus names (`RightName` resolved to a symbol), `Value` and `Error`.
//!
//! A member is a `Let` whose data also has a role ([`ROLE_MASK`]) and maybe
//! a visibility ([`MEMBER_PUBLIC`], [`MEMBER_PROTECTED`] or
//! [`MEMBER_PRIVATE`], none if not given), [`MEMBER_STATIC`], [`MEMBER_CONNECTED`]
//! and [`MEMBER_OPERATOR_EQ`] or [`MEMBER_OPERATOR_CMP`]. A field's value
//! is its initializer, and a method's,
//! getter's, setter's or constructor's is its `Fn`, without a body
//! ([`FN_NO_BODY`]) if abstract or implemented outside Temper
//! ([`MEMBER_CONNECTED`]); [`member_fn`] gives only one with a body.
//!
//! A type parameter is a `Let` with the role [`ROLE_TYPE_PARAM`], whose
//! type, if any, is its bound (`T extends Bound`).

use temper_syntax::tree::{NodeId, Tree};

/// A `Let` that may be assigned again.
pub const LET_VAR: u32 = 1;
pub const LET_TYPE: u32 = 2;
pub const LET_INIT: u32 = 4;

/// A `Loop` with a part to run after each pass.
pub const LOOP_NEXT: u32 = 1;
/// A `for (let x of xs) { body }` loop, which Kotlin Temper lowers to
/// `xs.forEach(fn (x) { body })`. Here that call is wrapped as `Loop({
/// call; Break })`, so that `break` has a loop to leave; the body `Fn` is
/// marked [`FN_LOOP_BODY`].
pub const LOOP_EACH: u32 = 2;

/// A `Class` that's an interface.
pub const CLASS_INTERFACE: u32 = 1 << 16;
/// A class with `@auto("==")`: its instances are equal when their public
/// fields are, as Kotlin Temper's `AddAutoMembers` makes them.
pub const CLASS_AUTO_EQ: u32 = 1 << 17;
/// An interface marked `@fun`, as `@fun interface Pred(x: String):
/// Boolean;`: a named function type, whose values are functions of its
/// `apply`'s signature (notes/typing.md, "Fun interfaces").
pub const CLASS_FUN: u32 = 1 << 18;
/// A class marked `@connected`, whose host code each backend supplies, or,
/// in user code, whose Temper implementation, if complete, serves as the
/// fallback for the interpreter and for backends without host code
/// (notes/kotlin-differences.md, "A user's `@connected` type").
pub const CLASS_CONNECTED: u32 = 1 << 19;
/// A `@connected` class that lists the backends with host code, as
/// `@connected(["cpp", "rust"])`, so the others need its Temper
/// implementation. Above the type parameter count.
pub const CLASS_CONNECTED_LIST: u32 = 1 << 28;
/// A `sealed interface`, whose subtypes are the classes and interfaces
/// of its library that extend it, so `when` checks that cover them all
/// need no `else` (notes/typing.md, "Sealed types and `when`").
pub const CLASS_SEALED: u32 = 1 << 29;

/// A member `Let`'s role: a field (0) or one of these.
pub const ROLE_MASK: u32 = 7 << 8;
/// A field declared in the class's header, a constructor parameter.
pub const ROLE_PARAM: u32 = 1 << 8;
pub const ROLE_METHOD: u32 = 2 << 8;
pub const ROLE_GETTER: u32 = 3 << 8;
pub const ROLE_SETTER: u32 = 4 << 8;
pub const ROLE_CONSTRUCTOR: u32 = 5 << 8;
/// A class's or function's type parameter, not a member.
pub const ROLE_TYPE_PARAM: u32 = 6 << 8;
/// A member only its class may use, on any of its instances.
pub const MEMBER_PRIVATE: u32 = 1 << 11;
/// A member of the class itself, not its instances.
pub const MEMBER_STATIC: u32 = 1 << 12;
/// A `@connected` member, which backends implement; a bodiless one runs
/// in the interpreter only if it has its own implementation, as
/// `Date.today()` does. Also a bodiless `@connected` function's `Let`,
/// whose type is its signature, as std `net`'s `sendRequest`.
pub const MEMBER_CONNECTED: u32 = 1 << 13;
/// A member declared `protected`, or `public`. A class's members must
/// say which (Kotlin's `MissingMemberVisibility`); an interface's are
/// public by default.
pub const MEMBER_PROTECTED: u32 = 1 << 14;
pub const MEMBER_PUBLIC: u32 = 1 << 15;
/// A method marked `@operator("==")`, which `a == b` and `a != b` call
/// when `a` is of its class.
pub const MEMBER_OPERATOR_EQ: u32 = 1 << 16;
/// A method marked `@operator("<=>")`, which `a <=> b`, and so `a < b`
/// and the like, call when `a` is of its class.
pub const MEMBER_OPERATOR_CMP: u32 = 1 << 17;
/// A class header's `@noProperty` input: a constructor parameter that
/// only the class's initializers read, by name, ahead of a member of the
/// same name, such as a getter. It's no member to anything else, and no
/// field in tmpl; the interpreter keeps it in a private one.
pub const MEMBER_NO_PROPERTY: u32 = 1 << 19;

/// A type parameter marked `@out` or `@in`, which Kotlin Temper honors
/// and tempest ignores (notes/typing.md, "Type parameters are
/// invariant"), so that checks can leave alone what only variance
/// accepts.
pub const TYPE_PARAM_VARIANT: u32 = 1 << 18;

/// Where a `Fn`'s or `Class`'s data counts its type parameters, in 8 bits.
pub const TYPE_PARAMS_SHIFT: u32 = 20;

fn type_param_count(tree: &Tree, node: NodeId) -> usize {
    ((tree.data(node) >> TYPE_PARAMS_SHIFT) & 0xff) as usize
}

/// A `Class`'s name, supertypes and members.
pub fn class_parts(tree: &Tree, node: NodeId) -> (NodeId, &[NodeId], &[NodeId]) {
    let count = (tree.data(node) & 0xffff) as usize;
    let children = &tree.children(node)[1 + type_param_count(tree, node)..];
    (
        tree.children(node)[0],
        &children[..count],
        &children[count..],
    )
}

/// A `Fn`'s or `Class`'s type parameters.
pub fn type_params(tree: &Tree, node: NodeId) -> &[NodeId] {
    let count = type_param_count(tree, node);
    let start = usize::from(tree.kind(node) == temper_syntax::tree::NodeKind::Class);
    &tree.children(node)[start..start + count]
}

/// Where a `Fn`'s data counts its supertypes, in 4 bits: `extends
/// GeneratorFn` makes it a generator's factory.
pub const FN_SUPERS_SHIFT: u32 = 16;
/// A `Fn` with a declared return type.
pub const FN_OUT_TYPE: u32 = 1 << 31;
/// A `for … of` loop's body: `break` and `return` in it reach past it, to
/// the loop and the function around it, and `continue` ends the call, as
/// Kotlin Temper's `@inlineUnrealizedGoal` on `forEach` allows.
pub const FN_LOOP_BODY: u32 = 1 << 30;
/// A method's, getter's or setter's signature without a body: abstract,
/// or `@connected`.
pub const FN_NO_BODY: u32 = 1 << 29;

/// A `Let`'s name, type and initial value.
pub fn let_parts(tree: &Tree, node: NodeId) -> (NodeId, Option<NodeId>, Option<NodeId>) {
    let flags = tree.data(node);
    let children = tree.children(node);
    let mut i = 1;
    let mut next = |present: bool| {
        present.then(|| {
            i += 1;
            children[i - 1]
        })
    };
    let ty = next(flags & LET_TYPE != 0);
    let init = next(flags & LET_INIT != 0);
    (children[0], ty, init)
}

/// A `Fn`'s parameters, return type and body, if it has one.
pub fn fn_parts(tree: &Tree, node: NodeId) -> (Vec<NodeId>, Option<NodeId>, Option<NodeId>) {
    let data = tree.data(node);
    let count = (data & 0xffff) as usize;
    let children = &tree.children(node)[type_param_count(tree, node)..];
    let out_type = (data & FN_OUT_TYPE != 0).then(|| children[count]);
    let body = (data & FN_NO_BODY == 0).then(|| *children.last().unwrap());
    (children[..count].to_vec(), out_type, body)
}

/// A `Fn`'s supertypes, as `GeneratorFn` in `fn (): T extends GeneratorFn`.
pub fn fn_supers(tree: &Tree, node: NodeId) -> &[NodeId] {
    let count = ((tree.data(node) >> FN_SUPERS_SHIFT) & 0xf) as usize;
    let children = tree.children(node);
    let end = children.len() - usize::from(tree.data(node) & FN_NO_BODY == 0);
    &children[end - count..end]
}

/// A method's, getter's, setter's or constructor's `Fn`, if it has a
/// body: none if it's abstract or `@connected` without one.
pub fn member_fn(tree: &Tree, member: NodeId) -> Option<NodeId> {
    let f = let_parts(tree, member).2?;
    (tree.kind(f) == temper_syntax::tree::NodeKind::Fn && tree.data(f) & FN_NO_BODY == 0)
        .then_some(f)
}

/// A class's constructor parameters, each a `Let`: its constructor's, or
/// if it has none, its header's (`class C(public x: Int)`), which the
/// implied constructor takes. Fields declared in the body aren't among
/// them, as in Kotlin Temper.
pub fn constructor_params(tree: &Tree, class: NodeId) -> Vec<NodeId> {
    let (_, _, members) = class_parts(tree, class);
    let role = |m: NodeId| tree.data(m) & ROLE_MASK;
    let is_static = |m: NodeId| tree.data(m) & MEMBER_STATIC != 0;
    if let Some(&c) = members
        .iter()
        .find(|&&m| role(m) == ROLE_CONSTRUCTOR && !is_static(m))
    {
        return let_parts(tree, c)
            .2
            .map(|f| fn_parts(tree, f).0)
            .unwrap_or_default();
    }
    members
        .iter()
        .copied()
        .filter(|&m| role(m) == ROLE_PARAM && !is_static(m))
        .collect()
}

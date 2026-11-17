//! The naming pass: every declaration of the program's own gets an output
//! name, pretty and numbered only to resolve a real conflict
//! (notes/tmpl/names.md, "The naming pass"; design.md section 8).
//!
//! For function-scoped targets, a function's parameters and locals share one scope, as
//! Python's do, and two of them may share a name when neither is alive
//! while the other is (and, where the target's checkers want it, as
//! Python's, when they have the same type, with or without null). For
//! block-scoped targets without shadowing (JS's
//! `let` and `const`, [`NamingRules::block_scoped`]), two may share a name
//! only when declared in blocks that don't nest. The init block's locals live in the module's
//! scope, as Python's module-level code does. Labels and other
//! libraries' declarations get no name: labels aren't printed by
//! function-scoped targets so far, and external declarations are named by
//! their own library or their variant's connection table.

use crate::analysis::{module_imports, module_refs, test_imports, walk};
use crate::decls::{DeclKind, Facts};
use crate::ids::{DeclId, NodeId};
use crate::qname::{QNames, is_external, library_of, qname_library};
use crate::schema::{Family, Node};
use crate::{Module, Program};
use std::collections::{HashMap, HashSet};

/// A target's case for a kind of name.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Case {
    /// As the source has it.
    Keep,
    /// `snake_case`, by Kotlin Temper's `IdentStyle.Camel` to
    /// `IdentStyle.Snake` conversion: `halfAndHalf` is `half_and_half`,
    /// `HTMLParser` `html_parser`, and `a1b` `a1_b`.
    Snake,
    /// `PascalCase`, by Kotlin Temper's `IdentStyle.Camel` to
    /// `IdentStyle.Pascal` conversion ([`pascal_case`]): `halfAndHalf` is
    /// `HalfAndHalf`, `HTMLParser` `HtmlParser`, and `a98Rgb` `A98_Rgb`.
    Pascal,
}

/// A target's naming conventions, as data (notes/tmpl/names.md,
/// "`NamingRules`"). Only what's honored so far; block scoping, class
/// bodies, and shadowing rules come with the targets that need them.
pub struct NamingRules {
    /// Lists of keywords, and of names the translator prints (builtins,
    /// runtime helpers), which no declaration may take.
    pub reserved: &'static [&'static [&'static str]],
    /// Lists of names a class's or interface's members may not take, in
    /// place of [`reserved`](Self::reserved), since a member's name is
    /// looked up on its object or class, not in the scopes the
    /// translator's printed names are: keywords, and only those printed
    /// names a member could hide or clash with, as Python's annotations
    /// and decorators in class bodies (`int`, `property`). A builtin's
    /// name is free, as Kotlin's members have it (JS `get`, Python `map`).
    pub member_reserved: &'static [&'static [&'static str]],
    /// Lists of names a function's parameters and source-named locals may
    /// not take, in place of [`reserved`](Self::reserved), where the
    /// translator reaches what such a name would hide another way: Python
    /// keeps a parameter named `help` or `len`, and reaches a builtin a
    /// function's own name hides through `builtins`, so a constructor's
    /// parameters, which callers may pass by name, never depend on its
    /// body. Compiler-made locals still avoid all of `reserved`, being
    /// free to take another name. `None`: `reserved`.
    pub local_reserved: Option<&'static [&'static [&'static str]]>,
    /// The case for a kind of declaration, exported or not.
    pub case: fn(DeclKind, bool) -> Case,
    /// Put before module-level names that aren't exported: `_` in Python.
    pub private_prefix: &'static str,
    /// Put after a name that's reserved: `_` in Python (`from_`).
    pub escape_suffix: &'static str,
    /// Keywords escaped by [`raw_prefix`](Self::raw_prefix) rather than
    /// reserved: Rust's, as raw identifiers (`r#type`). Empty in Python.
    pub raw_keywords: &'static [&'static str],
    /// Put before a raw keyword: `r#` in Rust.
    pub raw_prefix: &'static str,
    /// Whether a character may be in a name, first or not; others become
    /// `_` and their hex code.
    pub identifier: fn(char, bool) -> bool,
    /// Whether a function's locals are block-scoped and may not shadow one
    /// another, as JS's `let` and `const`: two may share a name only when
    /// declared in blocks that don't nest, since a name declared twice in
    /// one block is an error, and an inner declaration would shadow an
    /// outer one from the start of its block. Otherwise (Python, Rust)
    /// two may share a name when neither is alive while the other is.
    pub block_scoped: bool,
    /// Whether two of a function's locals that share a name must have
    /// the same type, with or without null, as for Python's checkers,
    /// which give a name one declared type in a function (though not a
    /// parameter and a nullable local of its type, as the parameter's
    /// annotation is the name's type); and a local
    /// declared inside another's initializer can't share its name, as a
    /// walrus would assign it before the other's annotation. Only for
    /// function-scoped targets.
    pub same_type_shares: bool,
    /// Whether a block value's statements run before the statement
    /// holding it, as Python's, which has no block expressions: then the
    /// locals its value reads stay alive until that statement reads them,
    /// after every block value in it has run, so `x++ + y++` keeps its
    /// two temporaries apart. Only for function-scoped targets.
    pub hoists_block_values: bool,
    /// Whether the module's init block is one function in the output,
    /// whose locals the function literals in it can't shadow, as Java's
    /// static initializer's lambdas can't, and C++'s `-Wshadow` reports:
    /// then those literals' names avoid the module's `let`s' and those of
    /// the init's locals whose blocks hold them.
    pub init_literals_avoid_module: bool,
    /// Whether a class's fields and private and protected members print
    /// with a prefix that its other members don't take, as Python's
    /// storage `_name` and `_method`: then a private field (`_messages`,
    /// styled `messages`) doesn't clash with a public method of its
    /// styled name (`messages()`). A public field is both, as its
    /// storage is prefixed and its property isn't.
    pub members_prefixed: bool,
    /// Whether a class's fields have names apart from its other members',
    /// as Rust's struct fields and methods: then a field shares a name with
    /// a method (`messages` and `messages()`), and only fields clash with
    /// fields. Functions the backend makes for fields, such as Rust's
    /// private fields' accessors, are the backend's to name.
    pub fields_apart: bool,
    /// Whether a module's classes and interfaces have names apart from
    /// its functions and `let`s, as C#'s and Java's, whose types are
    /// their namespace's or package's and whose functions and `let`s are
    /// static members of the module's class: then std's regex class
    /// `Digit` is named as its module `let` `Digit` is
    /// (`RegexGlobal.Digit`), as Kotlin's, and its support code names it.
    pub types_apart: bool,
    /// Whether every module's module-level declarations share one
    /// namespace, as C++'s share the library's: then a private one
    /// avoids the names of other modules' declarations, so the private
    /// `class Engine` of one doesn't redefine another's exported
    /// `Engine`, and only exports can clash.
    pub modules_share_names: bool,
}

/// Output names, by [`DeclId`].
#[derive(Clone, Debug, Default)]
pub struct NameMap {
    names: Vec<String>,
    public: HashMap<DeclId, String>,
    suffixed: Vec<DeclId>,
    clashes: Vec<String>,
    missing: Vec<String>,
}

impl NameMap {
    /// A declaration's name; empty for one the pass doesn't name.
    pub fn get(&self, decl: DeclId) -> &str {
        self.names.get(decl.index()).map_or("", String::as_str)
    }

    /// An export's public name, as its module's table or namespace keys
    /// it: its name escaped only by the rules' `member_reserved`, as a
    /// member's is, where that differs from its own (Lua's `local
    /// string_`, exported as `string`); else [`get`](Self::get)'s. Another
    /// library's, from its [`Interface`].
    pub fn public(&self, decl: DeclId) -> &str {
        self.public
            .get(&decl)
            .map_or_else(|| self.get(decl), String::as_str)
    }

    /// Whether any declaration has this name: for a target that names
    /// what the pass doesn't, as Python's labels, without a clash.
    pub fn has_name(&self, name: &str) -> bool {
        self.names.iter().any(|n| n == name)
    }

    /// The declarations that took a numbered suffix, which only a real
    /// conflict causes.
    pub fn suffixed(&self) -> &[DeclId] {
        &self.suffixed
    }

    /// Exports, connected, and `@keep` declarations whose names clash:
    /// errors, since those names can't change. Each is still given a
    /// suffixed name, so output stays valid.
    pub fn clashes(&self) -> &[String] {
        &self.clashes
    }

    /// Other libraries' declarations that no [`Interface`] names, by
    /// qname: errors, since their names must be their own library's.
    pub fn missing(&self) -> &[String] {
        &self.missing
    }
}

/// What a library's dependents need of its naming for one backend: the
/// name its own naming gave each declaration they may refer to, its
/// module-level declarations and their members, by qname
/// (notes/tmpl/open-questions.md, 46 and 47). A dependent's naming pass
/// spells those names, never deriving them again, so conflict renames,
/// connected names, and any later naming override carry over. Built by
/// [`Interface::of`] from the library's own program and [`NameMap`],
/// whether it was built in the same run or read from the library cache;
/// a build gives a program its dependencies' as [`Program::interfaces`].
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Interface {
    /// The library, as qnames write it (`std` for `temper-std`).
    pub library: String,
    /// Names by qname, without a disambiguation index; `None` for a qname
    /// two declarations share, which a dependent can't tell apart.
    names: HashMap<String, Option<String>>,
    /// Public names ([`NameMap::public`]) by qname, where they differ.
    public: HashMap<String, String>,
}

impl Interface {
    /// A library's interface: what its naming gave its own module-level
    /// declarations and their members.
    pub fn of(p: &Program, names: &NameMap) -> Interface {
        let qnames = QNames::derive(p);
        let mut out = Interface {
            library: qname_library(&p.library.name).to_string(),
            names: HashMap::new(),
            public: HashMap::new(),
        };
        for d in p.decls.ids() {
            if !is_shared(p, d) || is_external(p, d) || names.get(d).is_empty() {
                continue;
            }
            let Some(key) = qname_key(&qnames, d) else {
                continue;
            };
            if names.public(d) != names.get(d) {
                out.public.insert(key.clone(), names.public(d).to_string());
            }
            let name = names.get(d).to_string();
            out.names
                .entry(key)
                .and_modify(|n| *n = None)
                .or_insert(Some(name));
        }
        out
    }

    /// The name of the declaration with this qname (without its index).
    pub fn get(&self, qname: &str) -> Option<&str> {
        self.names.get(qname)?.as_deref()
    }

    /// The public name of the declaration with this qname, where it
    /// differs from [`get`](Self::get)'s.
    pub fn public(&self, qname: &str) -> Option<&str> {
        self.public.get(qname).map(String::as_str)
    }

    /// Gives a declaration this name, as a library cache would read it
    /// back, or a test would make it.
    pub fn insert(&mut self, qname: &str, name: &str) {
        self.names.insert(qname.to_string(), Some(name.to_string()));
    }
}

/// Whether a declaration is one a dependent may refer to, if exported: a
/// module-level function, `let`, class, or interface, or a member.
fn is_shared(p: &Program, d: DeclId) -> bool {
    matches!(
        p.decls.get(d).kind,
        DeclKind::Function
            | DeclKind::ModuleLet
            | DeclKind::Class
            | DeclKind::Interface
            | DeclKind::Method
            | DeclKind::Getter
            | DeclKind::Setter
            | DeclKind::Constructor
            | DeclKind::Field
            | DeclKind::StaticField
    )
}

/// A declaration's qname as interfaces key it: without its index, which
/// depends on what else the program declares.
fn qname_key(qnames: &QNames, d: DeclId) -> Option<String> {
    let mut q = qnames.get(d)?.clone();
    q.index = None;
    Some(q.to_string())
}

/// A word's kind, as Kotlin Temper's `Tok` in its identifier styles.
#[derive(Clone, Copy, PartialEq, Eq)]
enum Tok {
    Lower,
    Upper,
    Caseless,
    Digit,
    Delimiter,
    Other,
    Nil,
}

impl Tok {
    fn of(c: char) -> Tok {
        match c {
            '_' | '-' | '\u{b7}' | '\u{2027}' | '\u{318d}' | '\u{30fb}' | '\u{ff65}' => {
                Tok::Delimiter
            }
            _ if c.is_lowercase() => Tok::Lower,
            _ if c.is_uppercase() => Tok::Upper,
            _ if c.is_alphabetic() => Tok::Caseless,
            _ if c.is_numeric() => Tok::Digit,
            _ => Tok::Other,
        }
    }

    fn letters(self) -> bool {
        matches!(self, Tok::Lower | Tok::Upper | Tok::Caseless)
    }

    fn category(self) -> u8 {
        match self {
            Tok::Lower | Tok::Upper | Tok::Caseless => 0,
            Tok::Digit => 1,
            Tok::Delimiter | Tok::Other => 2,
            Tok::Nil => 3,
        }
    }
}

/// A camelCase name's words, as Kotlin's `breakChimeric` breaks them: at
/// case changes, delimiters (`_` and `-`, which are dropped), and digits;
/// each with the kind of its last character.
fn words(name: &str) -> Vec<(Tok, String)> {
    let mut words: Vec<(Tok, String)> = Vec::new();
    let (mut word_tok, mut word) = (Tok::Nil, String::new());
    for c in name.chars() {
        let t = Tok::of(c);
        if t == Tok::Delimiter {
            if !word.is_empty() {
                words.push((word_tok, std::mem::take(&mut word)));
            }
            word_tok = Tok::Nil;
            continue;
        }
        if t != word_tok {
            if word_tok == Tok::Upper && t == Tok::Lower {
                let count = word.chars().count();
                if count > 1 {
                    let last = word.pop().unwrap_or_default();
                    words.push((word_tok, std::mem::take(&mut word)));
                    word.push(last);
                }
            } else if !word.is_empty() {
                words.push((word_tok, std::mem::take(&mut word)));
            }
        }
        word.push(c);
        word_tok = t;
    }
    if !word.is_empty() {
        words.push((word_tok, word));
    }
    words
}

/// Converts a camelCase name to `snake_case` as Kotlin Temper does
/// (`IdentStyle.Camel.convertTo(IdentStyle.Snake, …)`): words break at
/// case changes, delimiters (`_` and `-`), and digits, are lowercased,
/// and are joined with `_` where they'd otherwise run together. A leading
/// digit or other non-letter gets a leading `_`.
pub fn snake_case(name: &str) -> String {
    // Join, as Kotlin's `mapLower` and `delimitUnderscore`.
    let mut out = String::new();
    let mut prior = Tok::Nil;
    for (t, w) in words(name) {
        let middle = prior != Tok::Nil;
        if middle && prior == Tok::Caseless && t == Tok::Caseless {
            out.push('\u{b7}');
        } else if (middle
            && (prior.category() == t.category() || (prior == Tok::Digit && t.letters())))
            || (!middle && !t.letters())
        {
            out.push('_');
        }
        if t.letters() {
            out.extend(w.chars().flat_map(char::to_lowercase));
        } else {
            out.push_str(&w);
        }
        prior = t;
    }
    out
}

/// Converts a camelCase name to `PascalCase` as Kotlin Temper does
/// (`IdentStyle.Camel.convertTo(IdentStyle.Pascal, …)`), as its C#
/// backend names functions and exported values: words break as
/// [`snake_case`]'s do, each is lowercased but for its first letter, and
/// `_` joins two words only where they'd otherwise run together: digits
/// before letters (`a98Rgb` is `A98_Rgb`, Kotlin's), or two runs of the
/// same uncased kind. A leading digit or other non-letter gets a leading
/// `_`.
pub fn pascal_case(name: &str) -> String {
    // Join, as Kotlin's `mapPascal` and `delimitChimeric`.
    let mut out = String::new();
    let mut prior = Tok::Nil;
    for (t, w) in words(name) {
        let middle = prior != Tok::Nil;
        let cased = matches!(t, Tok::Lower | Tok::Upper);
        if middle && prior == Tok::Caseless && t == Tok::Caseless {
            out.push('\u{b7}');
        } else if (middle && ((!cased && prior == t) || (prior == Tok::Digit && t.letters())))
            || (!middle && !t.letters())
        {
            out.push('_');
        }
        if t.letters() {
            let lower: String = w.chars().flat_map(char::to_lowercase).collect();
            let mut chars = lower.chars();
            if let Some(first) = chars.next() {
                out.extend(first.to_uppercase());
                out.push_str(chars.as_str());
            }
        } else {
            out.push_str(&w);
        }
        prior = t;
    }
    out
}

/// Converts a camelCase or snake_case name to `dash-case` as Kotlin
/// Temper does (`IdentStyle.Camel.convertTo(IdentStyle.Dash, …)`), as
/// `temper init` names a library after its directory: words break as
/// [`snake_case`]'s do, are lowercased, and are joined with `-` where
/// they'd otherwise run together (`fooBar2` is `foo-bar2`, `XMLParser`
/// `xml-parser`). A leading digit or other non-letter gets a leading `_`.
pub fn dash_case(name: &str) -> String {
    // Join, as Kotlin's `mapLower` and `delimitDash`.
    let mut out = String::new();
    let mut prior = Tok::Nil;
    for (t, w) in words(name) {
        let middle = prior != Tok::Nil;
        if middle && prior == Tok::Caseless && t == Tok::Caseless {
            out.push('\u{b7}');
        } else if middle
            && (prior.category() == t.category() || (prior == Tok::Digit && t.letters()))
        {
            out.push('-');
        } else if !middle && !t.letters() {
            out.push('_');
        }
        if t.letters() {
            out.extend(w.chars().flat_map(char::to_lowercase));
        } else {
            out.push_str(&w);
        }
        prior = t;
    }
    out
}

/// A dashed name as a title, as Kotlin Temper's `dashToTitle` makes one
/// for `temper init`'s files (`IdentStyle.Dash` to `Pascal`, then
/// `Pascal` to `Human`): `my-proj` is `My Proj`, `foo-bar2` `Foo Bar 2`,
/// and, as in Kotlin, `a-b` is `AB`, one-letter words running together.
pub fn dashed_title(name: &str) -> String {
    // Dash to Pascal: words broken at delimiters and at changes of kind
    // (a lowercase letter after an uppercase one continues a word), as
    // Kotlin's `breakOnDelimiter`, then joined as `pascal_case` joins.
    let mut dashed: Vec<(Tok, String)> = Vec::new();
    let (mut word_tok, mut word) = (Tok::Nil, String::new());
    for c in name.chars() {
        let t = Tok::of(c);
        if t == Tok::Delimiter {
            if !word.is_empty() {
                dashed.push((word_tok, std::mem::take(&mut word)));
            }
            word_tok = Tok::Nil;
            continue;
        }
        let same = t == word_tok || (t == Tok::Lower && word_tok == Tok::Upper);
        if !word.is_empty() && !same {
            dashed.push((word_tok, std::mem::take(&mut word)));
        }
        word.push(c);
        word_tok = t;
    }
    if !word.is_empty() {
        dashed.push((word_tok, word));
    }
    let mut pascal = String::new();
    let mut prior = Tok::Nil;
    for (t, w) in dashed {
        let middle = prior != Tok::Nil;
        let cased = matches!(t, Tok::Lower | Tok::Upper);
        if middle && prior == Tok::Caseless && t == Tok::Caseless {
            pascal.push('\u{b7}');
        } else if (middle && ((!cased && prior == t) || (prior == Tok::Digit && t.letters())))
            || (!middle && !t.letters())
        {
            pascal.push('_');
        }
        if t.letters() {
            let lower: String = w.chars().flat_map(char::to_lowercase).collect();
            let mut chars = lower.chars();
            if let Some(first) = chars.next() {
                pascal.extend(first.to_uppercase());
                pascal.push_str(chars.as_str());
            }
        } else {
            pascal.push_str(&w);
        }
        prior = t;
    }
    // Pascal to Human: words as camelCase breaks them, joined with a
    // space between words of one kind, or between letters and digits.
    let mut out = String::new();
    let mut prior = Tok::Nil;
    for (t, w) in words(&pascal) {
        let middle = prior != Tok::Nil;
        if middle && prior == Tok::Caseless && t == Tok::Caseless {
            out.push('\u{b7}');
        } else if middle
            && (prior.category() == t.category()
                || (prior == Tok::Digit && t.letters())
                || (prior.letters() && t == Tok::Digit))
        {
            out.push(' ');
        }
        out.push_str(&w);
        prior = t;
    }
    out
}

/// Candidate names for an expression a pass or backend stores in a
/// temporary, best first (notes/tmpl/analyses.md, "Naming hints"): the
/// name of the declaration it reads or calls, a callee's with a leading
/// `get` or `to` dropped (`toInt32` gives `int32`); its type's name in
/// lower camel case (`int32`, `string`); then `value`. Duplicates are
/// dropped. Not yet: the parameter it feeds.
pub fn hints(p: &Program, expr: NodeId) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    let mut push = |name: String| {
        if !name.is_empty() && !out.contains(&name) {
            out.push(name);
        }
    };
    let lower_first = |name: &str| {
        let mut chars = name.chars();
        chars.next().map_or(String::new(), |first| {
            first.to_lowercase().chain(chars).collect::<String>()
        })
    };
    match p.nodes.get(expr) {
        Node::Local { local: d } | Node::GetModuleLet { target: d } => push(p.name(d).into()),
        Node::CallFunction { callee: d, .. } | Node::CallMethod { method: d, .. } => {
            let name = p.name(d);
            let name = name.strip_prefix("get ").unwrap_or(name);
            let dropped = ["get", "to"].iter().find_map(|prefix| {
                name.strip_prefix(prefix)
                    .filter(|rest| rest.starts_with(|c: char| c.is_uppercase()))
            });
            push(match dropped {
                Some(rest) => lower_first(rest),
                None => name.to_string(),
            });
        }
        _ => {}
    }
    let ty = p.nodes.ty(expr);
    let type_name = match p.types.get(p.types.fallback(p.types.non_null(ty))) {
        crate::TypeData::Scalar(s) => s.name().to_string(),
        crate::TypeData::Nominal { decl, .. } => p.name(decl).to_string(),
        crate::TypeData::TypeParam { .. }
        | crate::TypeData::Function { .. }
        | crate::TypeData::Nullable(_)
        | crate::TypeData::Never(_) => String::new(),
    };
    push(lower_first(&type_name));
    push("value".into());
    out
}

/// [`assign`], or the first name that can't be had, which a backend
/// doesn't cover yet: an export's that clashes, or another library's
/// that no interface gives.
pub fn assign_checked(p: &Program, rules: &NamingRules) -> Result<NameMap, String> {
    let names = assign(p, rules);
    if let Some(clash) = names.clashes().first() {
        return Err(format!("renaming an export: {clash}"));
    }
    if let Some(missing) = names.missing().first() {
        return Err(format!("another library's name: {missing}"));
    }
    Ok(names)
}

/// Gives every declaration of the program's own a name, by `rules`.
pub fn assign(p: &Program, rules: &NamingRules) -> NameMap {
    let mut map = NameMap {
        names: vec![String::new(); p.decls.len()],
        ..NameMap::default()
    };
    let reserved: HashSet<&str> = rules
        .reserved
        .iter()
        .flat_map(|l| l.iter().copied())
        .collect();
    let member_reserved: HashSet<&str> = rules
        .member_reserved
        .iter()
        .flat_map(|l| l.iter().copied())
        .collect();
    let local_reserved: HashSet<&str> = match rules.local_reserved {
        Some(lists) => lists.iter().flat_map(|l| l.iter().copied()).collect(),
        None => reserved.clone(),
    };
    // Other libraries' declarations, but core's, which backends reach
    // through their runtimes: as their own library's naming named them,
    // from its interface, std's too.
    let externals: Vec<DeclId> = (p.decls.ids())
        .filter(|&d| is_shared(p, d) && is_external(p, d) && library_of(p, d) != "core")
        .collect();
    let qnames = if externals.is_empty() {
        QNames::default()
    } else {
        QNames::derive(p)
    };
    for d in externals {
        let library = library_of(p, d);
        let interface = p.interfaces.iter().find(|i| i.library == library);
        let key = qname_key(&qnames, d);
        let found = interface.zip(key.as_deref()).and_then(|(i, k)| i.get(k));
        let public = interface.zip(key.as_deref()).and_then(|(i, k)| i.public(k));
        if let Some(public) = public {
            map.public.insert(d, public.to_string());
        }
        map.names[d.index()] = match found {
            Some(name) => name.to_string(),
            None => {
                let what = key.unwrap_or_else(|| p.name(d).to_string());
                map.missing.push(match interface {
                    Some(_) => format!("{what}, which {library} doesn't name"),
                    None => format!("{what}, without {library}'s names"),
                });
                let cands = candidates(p, rules, d, true);
                escape(rules, &reserved, cands[0].clone())
            }
        };
    }
    // Where modules share names, each module's exports, which others'
    // private declarations avoid, and those private declarations' names,
    // as they're given.
    let exports: Vec<HashSet<String>> = match rules.modules_share_names {
        true => (p.modules.iter())
            .map(|m| {
                (module_level(p, m).into_iter())
                    .filter(|&d| claims_first(p, d))
                    .map(|d| escape(rules, &reserved, candidates(p, rules, d, true)[0].clone()))
                    .collect()
            })
            .collect(),
        false => Vec::new(),
    };
    let mut privates: HashSet<String> = HashSet::new();
    for (i, module) in p.modules.iter().enumerate() {
        let mut shared = HashSet::new();
        if rules.modules_share_names {
            shared.extend(privates.iter().cloned());
            for (j, names) in exports.iter().enumerate() {
                if j != i {
                    shared.extend(names.iter().cloned());
                }
            }
        }
        name_module(
            p,
            rules,
            [&reserved, &local_reserved],
            &member_reserved,
            module,
            &shared,
            &mut map,
        );
        if rules.modules_share_names {
            for d in module_level(p, module) {
                if !claims_first(p, d) {
                    privates.insert(map.get(d).to_string());
                }
            }
        }
    }
    map
}

/// A module's functions, `let`s, classes, and interfaces.
fn module_level(p: &Program, module: &Module) -> Vec<DeclId> {
    (module.items.iter())
        .filter_map(|&item| match p.nodes.get(item) {
            Node::Function { decl, .. }
            | Node::BodilessFunction { decl, .. }
            | Node::ModuleLet { decl }
            | Node::Class { decl, .. }
            | Node::Interface { decl, .. } => Some(decl),
            _ => None,
        })
        .collect()
}

/// Whether a declaration's name is fixed, so it claims its name before
/// others: exported, connected, or kept, for host code or its tests'.
fn claims_first(p: &Program, d: DeclId) -> bool {
    let facts = p.decls.get(d).facts;
    facts.contains(Facts::EXPORTED)
        || facts.contains(Facts::CONNECTED)
        || facts.contains(Facts::KEEP)
        || facts.contains(Facts::KEEP_TEST)
}

/// The module-level declarations a function refers to, as
/// [`module_refs`] gives them, and other libraries' but core's.
fn referred(p: &Program, function: NodeId) -> Vec<DeclId> {
    let mut found = module_refs(p, function);
    walk(p, function, &mut |id| {
        let decl = match p.nodes.get(id) {
            Node::CallFunction { callee, .. } => callee,
            Node::GetModuleLet { target } | Node::SetModuleLet { target, .. } => target,
            _ => return,
        };
        if is_external(p, decl) && library_of(p, decl) != "core" && !found.contains(&decl) {
            found.push(decl);
        }
    });
    found
}

/// A declaration's candidates, styled for the target: its name, then a
/// compiler-made declaration's further hints.
fn candidates(p: &Program, rules: &NamingRules, decl: DeclId, module_level: bool) -> Vec<String> {
    let d = p.decls.get(decl);
    // Another library's declarations are its exports.
    let exported = d.facts.contains(Facts::EXPORTED) || is_external(p, decl);
    let mut raw: Vec<&str> = vec![p.name(decl)];
    raw.extend(p.decls.alts(decl).iter().map(|&s| p.strings.get(s)));
    raw.into_iter()
        .filter(|r| !r.is_empty())
        .map(|r| {
            let styled = match (rules.case)(d.kind, exported) {
                Case::Keep => r.to_string(),
                Case::Snake => snake_case(r),
                Case::Pascal => pascal_case(r),
            };
            let mut name = String::new();
            for (i, c) in styled.chars().enumerate() {
                if (rules.identifier)(c, i == 0) {
                    name.push(c);
                } else {
                    name.push_str(&format!("_{:x}", c as u32));
                }
            }
            if name.is_empty() {
                name.push('_');
            }
            if module_level && !exported {
                name.insert_str(0, rules.private_prefix);
            }
            name
        })
        .collect()
}

fn escape(rules: &NamingRules, reserved: &HashSet<&str>, mut name: String) -> String {
    if rules.raw_keywords.contains(&name.as_str()) {
        return format!("{}{name}", rules.raw_prefix);
    }
    while reserved.contains(name.as_str()) {
        name.push_str(rules.escape_suffix);
    }
    name
}

/// The first of `candidates` that `free` accepts, else the first with the
/// shortest numbered suffix that it accepts, and whether it's suffixed.
fn choose(
    rules: &NamingRules,
    reserved: &HashSet<&str>,
    candidates: &[String],
    free: impl Fn(&str) -> bool,
) -> (String, bool) {
    for c in candidates {
        let c = escape(rules, reserved, c.clone());
        if free(&c) {
            return (c, false);
        }
    }
    let first = escape(rules, reserved, candidates[0].clone());
    let sep = if first.ends_with(|c: char| c.is_ascii_digit()) {
        "_"
    } else {
        ""
    };
    (2..)
        .map(|n| escape(rules, reserved, format!("{first}{sep}{n}")))
        .find(|c| free(c))
        .map(|c| (c, true))
        .unwrap_or_default()
}

fn name_module(
    p: &Program,
    rules: &NamingRules,
    [reserved, local_reserved]: [&HashSet<&str>; 2],
    member_reserved: &HashSet<&str>,
    module: &Module,
    shared: &HashSet<String>,
    map: &mut NameMap,
) {
    // Module-level declarations: the items, then the init block's locals,
    // in the claiming order (exported, connected, and kept first, then
    // source names, then compiler-made ones).
    let mut level = module_level(p, module);
    for &test in &module.tests {
        if let Node::Test { decl, .. } = p.nodes.get(test) {
            level.push(decl);
        }
    }
    // The init block's locals, but those of the function literals in it,
    // which are named as functions are.
    let mut literals = Vec::new();
    let mut init_locals = Vec::new();
    let mut stack = vec![module.init];
    while let Some(id) = stack.pop() {
        match p.nodes.get(id) {
            Node::Let { local, .. } => {
                level.push(local);
                init_locals.push(local);
            }
            Node::FunctionLiteral { .. } => {
                literals.push(id);
                continue;
            }
            _ => {}
        }
        stack.extend(p.nodes.kids(id).iter().rev());
    }
    let rank = |d: DeclId| {
        if claims_first(p, d) {
            0
        } else if p.decls.get(d).facts.contains(Facts::COMPILER_MADE) {
            2
        } else {
            1
        }
    };
    level.sort_by_key(|&d| (rank(d), d));
    // What the module imports keeps its name here, so its own
    // declarations don't take it.
    let mut taken: HashSet<String> = HashSet::new();
    for import in module_imports(p, module)
        .into_iter()
        .chain(test_imports(p, module))
    {
        for d in import.decls {
            taken.insert(map.names[d.index()].clone());
        }
    }
    // Where types are apart, a type avoids what's imported and other
    // types' names, and anything else what's imported and the names of
    // the others but types.
    let is_type = |d: DeclId| matches!(p.decls.get(d).kind, DeclKind::Class | DeclKind::Interface);
    let imported = taken.clone();
    // Nor do its items take the names other modules' declarations share
    // with them; its tests and the init's locals are its own.
    let items: HashSet<DeclId> = module_level(p, module).into_iter().collect();
    let (mut types, mut values) = (HashSet::new(), HashSet::new());
    for d in level {
        let cands = candidates(p, rules, d, true);
        let own = if is_type(d) { &types } else { &values };
        let elsewhere = |c: &str| items.contains(&d) && shared.contains(c);
        let free = |c: &str| match rules.types_apart {
            true => !imported.contains(c) && !own.contains(c) && !elsewhere(c),
            false => !taken.contains(c) && !elsewhere(c),
        };
        let (name, suffixed) = choose(rules, reserved, &cands, free);
        if suffixed {
            map.suffixed.push(d);
            if rank(d) == 0 {
                map.clashes.push(format!(
                    "`{}` can't be named `{}`, which another declaration has",
                    p.name(d),
                    escape(rules, reserved, cands[0].clone())
                ));
            }
        }
        taken.insert(name.clone());
        if is_type(d) {
            types.insert(name.clone());
        } else {
            values.insert(name.clone());
        }
        map.names[d.index()] = name;
    }
    // Exports keep their public names, as members do, where their own
    // escape further; one that another's name or public name has clashes.
    let level = module_level(p, module);
    let mut keys: HashSet<String> = level.iter().map(|&d| map.get(d).to_string()).collect();
    for &d in &level {
        if !p.decls.get(d).facts.contains(Facts::EXPORTED) {
            continue;
        }
        let own = candidates(p, rules, d, true)[0].clone();
        let key = escape(rules, member_reserved, own);
        if key == map.get(d) {
            continue;
        }
        if keys.insert(key.clone()) {
            map.public.insert(d, key);
        } else {
            map.clashes.push(format!(
                "`{}` can't be exported as `{key}`, which another declaration has",
                p.name(d)
            ));
        }
    }

    // Where the init is one function, a literal in it avoids the names of
    // the module's `let`s and of the init's locals whose blocks hold it.
    let scopes = Scopes::of(p, module.init);
    // Under block scoping without shadowing, a literal a target declares
    // as a local of the init (Java's and C#'s hoisted `fn`s) avoids the
    // names of those named before it whose blocks nest with its own; and
    // where a name has one type in a function (Python's checkers), the
    // names of all those before it, which its module-level `def`s would
    // redefine.
    let mut before: Vec<(DeclId, Option<NodeId>)> = Vec::new();
    for literal in literals {
        let mut around = HashSet::new();
        let (decl, at) = match p.nodes.get(literal) {
            Node::FunctionLiteral { decl, .. } => (Some(decl), scopes.of.get(&decl).copied()),
            _ => (None, None),
        };
        if rules.block_scoped || rules.same_type_shares {
            for &(other, block) in &before {
                let nest = match (at, block) {
                    _ if rules.same_type_shares => true,
                    (Some(a), Some(b)) => scopes.within(a, b) || scopes.within(b, a),
                    _ => true,
                };
                if nest {
                    around.insert(map.get(other).to_string());
                }
            }
        }
        if rules.init_literals_avoid_module {
            for &item in &module.items {
                if let Node::ModuleLet { decl } = p.nodes.get(item) {
                    around.insert(map.get(decl).to_string());
                }
            }
            for &d in &init_locals {
                let block = scopes.of.get(&d).copied();
                if at.zip(block).is_some_and(|(at, b)| scopes.within(at, b)) {
                    around.insert(map.get(d).to_string());
                }
            }
        }
        name_function(p, rules, [reserved, local_reserved], literal, &around, map);
        if let Some(decl) = decl {
            before.push((decl, at));
        }
    }
    for &item in module.items.iter().chain(&module.tests) {
        match p.nodes.get(item) {
            Node::Function { .. } | Node::BodilessFunction { .. } | Node::Test { .. } => {
                let reserved = [reserved, local_reserved];
                name_function(p, rules, reserved, item, &HashSet::new(), map)
            }
            Node::Class { members, .. } | Node::Interface { members, .. } => {
                name_members(p, rules, member_reserved, p.nodes.list(members), map);
                for &member in p.nodes.list(members) {
                    if let Node::Method { .. }
                    | Node::AbstractMethod { .. }
                    | Node::Function { .. }
                    | Node::BodilessFunction { .. } = p.nodes.get(member)
                    {
                        let reserved = [reserved, local_reserved];
                        name_function(p, rules, reserved, member, &HashSet::new(), map);
                    }
                }
            }
            _ => {}
        }
    }
}

/// Names a class's or interface's members, in their own namespace: each
/// its source name, styled; a getter and setter of one property share it,
/// and so may a field and the property it implements; anything else that
/// would clash is suffixed. `reserved` is the rules' `member_reserved`.
fn name_members(
    p: &Program,
    rules: &NamingRules,
    reserved: &HashSet<&str>,
    members: &[NodeId],
    map: &mut NameMap,
) {
    let mut named: Vec<(DeclId, String)> = Vec::new();
    // Public members first, so where a name is wanted twice, a private or
    // protected member takes the suffix, never public API.
    let member_decl = |m: NodeId| match p.nodes.get(m) {
        Node::Field { decl }
        | Node::StaticField { decl }
        | Node::Method { decl, .. }
        | Node::AbstractMethod { decl, .. }
        | Node::Function { decl, .. }
        // A connected static member without a body, as std's
        // `Date.today()`, which dependents may call.
        | Node::BodilessFunction { decl, .. } => Some(decl),
        _ => None,
    };
    let hidden = |d: DeclId| {
        let facts = p.decls.get(d).facts;
        facts.contains(Facts::PRIVATE) || facts.contains(Facts::PROTECTED)
    };
    let decls: Vec<DeclId> = members.iter().filter_map(|&m| member_decl(m)).collect();
    let ordered =
        (decls.iter().filter(|&&d| !hidden(d))).chain(decls.iter().filter(|&&d| hidden(d)));
    for &decl in ordered {
        let kind = p.decls.get(decl).kind;
        let property =
            |k: DeclKind| matches!(k, DeclKind::Getter | DeclKind::Setter | DeclKind::Field);
        let cands = candidates(p, rules, decl, false);
        // Under `members_prefixed`, whether a member prints with the
        // prefix, and whether without it.
        let sides = |d: DeclId| {
            let field = matches!(p.decls.get(d).kind, DeclKind::Field);
            match rules.members_prefixed {
                true => (hidden(d) || field, !hidden(d)),
                false => (true, true),
            }
        };
        let (name, suffixed) = choose(rules, reserved, &cands, |c| {
            !named.iter().any(|&(other, ref n)| {
                let (a, b) = (sides(decl), sides(other));
                let other_kind = p.decls.get(other).kind;
                let apart = rules.fields_apart
                    && ((kind == DeclKind::Field) != (other_kind == DeclKind::Field));
                let shares = property(kind) && property(other_kind);
                n == c && ((a.0 && b.0) || (a.1 && b.1)) && !shares && !apart
            })
        });
        if suffixed {
            map.suffixed.push(decl);
        }
        map.names[decl.index()] = name.clone();
        named.push((decl, name));
    }
}

/// When each local or parameter is alive, as positions in a walk of its
/// function: from its definition to its last use, stretched over any loop
/// that uses it without defining it, since the next iteration may read it.
#[derive(Default)]
struct Live {
    pos: u32,
    start: HashMap<DeclId, u32>,
    end: HashMap<DeclId, u32>,
    uses: HashMap<DeclId, Vec<u32>>,
    loops: Vec<(u32, u32)>,
    /// Where each `let`'s initializer is, for locals declared inside it.
    inits: HashMap<DeclId, (u32, u32)>,
    /// Whether block values' statements run before the statement holding
    /// them ([`NamingRules::hoists_block_values`]).
    hoist: bool,
    /// The locals that block values' values read, by statement being
    /// walked, innermost last: read where that statement's expressions
    /// are, once its block values' statements have all run.
    held: Vec<Vec<DeclId>>,
    /// How many block values' values deep the walk is, in the innermost
    /// statement.
    in_value: usize,
}

impl Live {
    fn define(&mut self, d: DeclId) {
        self.start.insert(d, self.pos);
        self.end.insert(d, self.pos);
    }

    fn touch(&mut self, d: DeclId) {
        let end = self.end.entry(d).or_insert(self.pos);
        *end = (*end).max(self.pos);
        self.uses.entry(d).or_default().push(self.pos);
        if self.in_value > 0 {
            if let Some(held) = self.held.last_mut() {
                held.push(d);
            }
        }
    }

    /// Reads the innermost statement's held locals here.
    fn release(&mut self) {
        let held = self.held.last_mut().map(std::mem::take).unwrap_or_default();
        let in_value = std::mem::replace(&mut self.in_value, 0);
        for d in held {
            self.touch(d);
        }
        self.in_value = in_value;
    }

    /// Visits kids in order, releasing held locals before a nested
    /// statement (an `if`'s blocks), which runs after the expressions
    /// before it.
    fn visit_kids(&mut self, p: &Program, id: NodeId) {
        for &kid in p.nodes.kids(id) {
            if p.nodes.kind(kid).family() == Family::Stmt {
                self.release();
            }
            self.visit(p, kid);
        }
    }

    fn visit(&mut self, p: &Program, id: NodeId) {
        // Where block values' statements run before the statement holding
        // them, as Python's, what their values read is read there, after
        // every one of them: `x++ + y++` reads both temporaries after
        // assigning both.
        let stmt = self.hoist && p.nodes.kind(id).family() == Family::Stmt;
        let in_value = if stmt {
            self.held.push(Vec::new());
            std::mem::replace(&mut self.in_value, 0)
        } else {
            0
        };
        self.visit_node(p, id);
        if stmt {
            self.release();
            self.held.pop();
            self.in_value = in_value;
        }
    }

    fn visit_node(&mut self, p: &Program, id: NodeId) {
        self.pos += 1;
        match p.nodes.get(id) {
            Node::Let { local, init } => {
                if let Some(init) = init {
                    let start = self.pos;
                    self.visit(p, init);
                    self.release();
                    self.inits.insert(local, (start, self.pos));
                }
                self.pos += 1;
                self.define(local);
            }
            Node::BlockValue { stmts, value } => {
                for &s in p.nodes.list(stmts) {
                    self.visit(p, s);
                }
                self.in_value += 1;
                self.visit(p, value);
                self.in_value -= 1;
            }
            Node::Param { decl } => {
                self.define(decl);
            }
            Node::FunctionLiteral { decl, .. } => {
                self.define(decl);
                for &kid in p.nodes.kids(id) {
                    self.visit(p, kid);
                }
            }
            Node::Local { local } => self.touch(local),
            Node::Assign { local, value } => {
                self.visit(p, value);
                self.release();
                self.pos += 1;
                self.touch(local);
            }
            Node::While { .. } | Node::DoWhile { .. } => {
                let start = self.pos;
                self.visit_kids(p, id);
                self.release();
                self.pos += 1;
                self.loops.push((start, self.pos));
            }
            _ => self.visit_kids(p, id),
        }
    }

    /// Each declaration's live range, after stretching over loops.
    fn ranges(&self) -> HashMap<DeclId, (u32, u32)> {
        let mut ranges = HashMap::new();
        for (&d, &start) in &self.start {
            let mut end = self.end.get(&d).copied().unwrap_or(start);
            let uses = self.uses.get(&d).map_or(&[][..], Vec::as_slice);
            for &(ls, le) in &self.loops {
                if start < ls && uses.iter().any(|&u| ls < u && u < le) {
                    end = end.max(le);
                }
            }
            ranges.insert(d, (start, end));
        }
        ranges
    }
}

/// Names a function's parameters and locals, none taking a name in
/// `around`: compiler-made ones avoid `reserved`, and parameters and
/// source-named locals `local_reserved` ([`NamingRules::local_reserved`]).
fn name_function(
    p: &Program,
    rules: &NamingRules,
    [reserved, local_reserved]: [&HashSet<&str>; 2],
    function: NodeId,
    around: &HashSet<String>,
    map: &mut NameMap,
) {
    // Python decides locality per function: a local can't take the name
    // of a module-level declaration the function refers to anywhere.
    let mut outer: HashSet<String> = referred(p, function)
        .into_iter()
        .map(|d| map.get(d).to_string())
        .collect();
    outer.extend(around.iter().cloned());
    let mut live = Live {
        hoist: rules.hoists_block_values,
        ..Live::default()
    };
    live.visit(p, function);
    let mut ranges = live.ranges();
    let mut decls: Vec<DeclId> = Vec::new();
    walk(p, function, &mut |id| match p.nodes.get(id) {
        Node::Param { decl } | Node::FunctionLiteral { decl, .. } => decls.push(decl),
        Node::Let { local, .. } => decls.push(local),
        _ => {}
    });
    // A literal may run after its captures' last uses, so they stay live
    // to the end; and so does the literal's own name, where a target
    // declares it.
    for &d in &decls {
        if p.decls.get(d).kind == DeclKind::Lambda {
            for &c in p.decls.captures(d) {
                if let Some(range) = ranges.get_mut(&c) {
                    range.1 = u32::MAX;
                }
            }
            if let Some(range) = ranges.get_mut(&d) {
                range.1 = u32::MAX;
            }
        }
    }
    let scopes = Scopes::of(p, function);
    // Parameters first, then source names, then compiler-made ones.
    let made = |d: DeclId| p.decls.get(d).facts.contains(Facts::COMPILER_MADE);
    let param = |d: DeclId| p.decls.get(d).kind == DeclKind::Param;
    decls.sort_by_key(|&d| (!param(d), made(d), d));
    let mut named: Vec<(DeclId, String)> = Vec::new();
    for d in decls {
        if is_external(p, d) {
            continue;
        }
        let range = ranges.get(&d).copied().unwrap_or((0, u32::MAX));
        let cands = candidates(p, rules, d, false);
        let reserved = if made(d) { reserved } else { local_reserved };
        let (name, suffixed) = choose(rules, reserved, &cands, |c| {
            !outer.contains(c)
                && !named.iter().any(|(other, n)| {
                    if n != c {
                        false
                    } else if rules.block_scoped {
                        scopes.nest(d, *other)
                    } else {
                        let (s, e) = ranges.get(other).copied().unwrap_or((0, u32::MAX));
                        // `T` and `T?` may share, as a parameter's
                        // default's local and the parameter do; but not a
                        // parameter and a wider local, as its annotation
                        // gives the name its type.
                        let (a, b) = (p.decls.get(d).ty, p.decls.get(*other).ty);
                        let wider =
                            param(*other) && p.types.is_nullable(a) && !p.types.is_nullable(b);
                        let differ = p.types.non_null(a) != p.types.non_null(b) || wider;
                        // Nor may a local declared inside another's
                        // initializer, which Python's walrus would
                        // assign before the other's annotation.
                        let inside = |x: DeclId, y: DeclId| {
                            let start = ranges.get(&x).map_or(0, |r| r.0);
                            live.inits
                                .get(&y)
                                .is_some_and(|&(a, b)| a <= start && start <= b)
                        };
                        let nested = inside(d, *other) || inside(*other, d);
                        (s <= range.1 && range.0 <= e)
                            || (rules.same_type_shares && (differ || nested))
                    }
                })
        });
        if suffixed {
            map.suffixed.push(d);
        }
        map.names[d.index()] = name.clone();
        named.push((d, name));
    }
}

/// Where each of a function's parameters and locals is declared, for
/// block-scoped targets: the block holding its `let`, or the function
/// itself for a parameter; and each block's enclosing one.
struct Scopes {
    of: HashMap<DeclId, NodeId>,
    parent: HashMap<NodeId, NodeId>,
}

impl Scopes {
    fn of(p: &Program, function: NodeId) -> Scopes {
        fn visit(p: &Program, id: NodeId, scope: NodeId, scopes: &mut Scopes) {
            let mut scope = scope;
            match p.nodes.get(id) {
                Node::Param { decl } => {
                    scopes.of.insert(decl, scope);
                }
                Node::Let { local, .. } => {
                    scopes.of.insert(local, scope);
                }
                Node::FunctionLiteral { decl, .. } => {
                    scopes.of.insert(decl, scope);
                }
                // A block that's the root (an init block) has no parent.
                Node::Block { .. } => {
                    if id != scope {
                        scopes.parent.insert(id, scope);
                    }
                    scope = id;
                }
                _ => {}
            }
            for &kid in p.nodes.kids(id) {
                visit(p, kid, scope, scopes);
            }
        }
        let mut scopes = Scopes {
            of: HashMap::new(),
            parent: HashMap::new(),
        };
        visit(p, function, function, &mut scopes);
        scopes
    }

    /// Whether `inner` is `outer` or inside it.
    fn within(&self, mut inner: NodeId, outer: NodeId) -> bool {
        loop {
            if inner == outer {
                return true;
            }
            match self.parent.get(&inner) {
                Some(&up) => inner = up,
                None => return false,
            }
        }
    }

    /// Whether two declarations' blocks are the same, or one holds the
    /// other.
    fn nest(&self, a: DeclId, b: DeclId) -> bool {
        match (self.of.get(&a), self.of.get(&b)) {
            (Some(&x), Some(&y)) => self.within(x, y) || self.within(y, x),
            _ => true,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::{dash_case, dashed_title};

    /// As Kotlin's `temper init` names libraries after their directories
    /// (checked against its output, 2026-11-09).
    #[test]
    fn dash_case_and_titles_as_kotlin_init() {
        for (dir, dashed, title) in [
            ("my-Proj", "my-proj", "My Proj"),
            ("MyProj", "my-proj", "My Proj"),
            ("my_proj", "my-proj", "My Proj"),
            ("fooBar2", "foo-bar2", "Foo Bar 2"),
            ("XMLParser", "xml-parser", "Xml Parser"),
            ("__x", "x", "X"),
        ] {
            assert_eq!(dash_case(dir), dashed, "{dir}");
            assert_eq!(dashed_title(dashed), title, "{dir}");
        }
        assert_eq!(dash_case("123abc"), "_123-abc");
        assert_eq!(dashed_title("x-123-abc"), "X 123 Abc");
        assert_eq!(dashed_title("a-b"), "AB");
        assert_eq!(dashed_title("config-lib"), "Config Lib");
    }
}

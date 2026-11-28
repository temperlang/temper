//! A library's own host code ([`temper_tmpl::host`]), as Kotlin's C#
//! backend uses it: the library's `.cs` files are copied into its `src`
//! project, each in the directory of its module's namespace
//! ([`files`]); a connected function that takes host code is a method
//! that applies its parameters' defaults, then calls the module's
//! `NAMEConnected` class's static of its name in Pascal case
//! (`WorkConnected.Sum(i, j, bonus)`); and a connected class that does is
//! the host's own class of its name, so nothing is generated for it.
//! Otherwise each is translated from its Temper implementation, and a
//! host file named for such a class (`Counter.cs`) is left out, so the
//! class isn't defined twice. Host code calls the generated code by its
//! names (`WorkGlobal.SumOf3`, `hidden.I`), which are Kotlin's.

use super::{R, Translator, not_yet, returning};
use crate::OutFile;
use crate::tree::{self, Expr};
use temper_tmpl::analysis::defaults;
use temper_tmpl::host::{self, replaced_by_fallbacks};
use temper_tmpl::naming::pascal_case;
use temper_tmpl::{DeclId, Kids, Module, Node, NodeId, Program};

/// The names by which a connected declaration's backend list can mean this
/// backend, as Kotlin's `connectedBackendNames`.
pub const NAMES: &[&str] = &["csharp"];

impl Translator<'_> {
    /// The module's class of host code, `NAMEConnected`, as Kotlin's
    /// `makeConnectedName`.
    fn host_class(&self) -> String {
        let base = self.namespace.rsplit('.').next().unwrap_or_default();
        format!("{base}Connected")
    }

    /// A connected function whose host code runs instead of its Temper
    /// body, if any: its parameters' defaults, then a call of its host
    /// code with each parameter, defaulted, as Kotlin's.
    pub(super) fn host_wrapper(
        &mut self,
        module: &Module,
        item: NodeId,
        decl: DeclId,
        params: Kids,
        body: Option<NodeId>,
    ) -> R<tree::Method> {
        let p = self.p;
        let class = self.host_class();
        self.need_host(module, decl, &class)?;
        let (result, ps, _) = self.callable(item, decl, params, None)?;
        self.in_function = true;
        self.result = (result != "void").then(|| result.clone());
        self.begin(item);
        let defaults = body.map(|b| defaults(p, b)).unwrap_or_default();
        let mut stmts = Vec::new();
        for d in &defaults {
            self.stmt(d.stmt, &mut stmts)?;
        }
        let args = (p.nodes.list(params).iter())
            .filter_map(|&param| match p.nodes.get(param) {
                Node::Param { decl } => Some(decl),
                _ => None,
            })
            .map(|param| {
                let local = defaults.iter().find(|d| d.param == param);
                Expr::name(self.name(local.map_or(param, |d| d.local)))
            })
            .collect();
        let class = if self.taken.contains(&class) {
            format!("global::{}.{class}", self.namespace)
        } else {
            class
        };
        let call = Expr::name(class).method(pascal_case(p.name(decl)), args);
        stmts.push(returning(&result, call));
        let (name, constraints) = self.generic_name(decl, self.name(decl))?;
        Ok(tree::Method {
            public: self.exported(decl),
            result,
            name,
            params: ps,
            constraints,
            body: stmts,
        })
    }

    /// Fails unless a host file beside the module defines `class`, which
    /// host code for `decl` needs, rather than leaving that to C#'s
    /// compiler, as Kotlin's does.
    pub(super) fn need_host(&self, module: &Module, decl: DeclId, class: &str) -> R<()> {
        if defines(self.p, module, class) {
            return Ok(());
        }
        let dir = match module.path.as_str() {
            "" => String::new(),
            dir => format!("{dir}/"),
        };
        not_yet(format!(
            "{}'s host code, with no class {class} in a .cs file beside its module, \
             as {dir}{class}.cs",
            self.p.name(decl)
        ))
    }
}

/// Whether a `.cs` host file in the module's directory declares `class`,
/// by its text: `class NAME`, then something that can't continue a name.
fn defines(p: &Program, module: &Module, class: &str) -> bool {
    let needle = format!("class {class}");
    (host::files(p, &[".cs"]).into_iter())
        .filter(|f| f.path.rsplit_once('/').map_or("", |(dir, _)| dir) == module.path)
        .any(|f| {
            f.text.match_indices(&needle).any(|(i, _)| {
                let next = f.text[i + needle.len()..].chars().next();
                !next.is_some_and(|c| c.is_alphanumeric() || c == '_')
            })
        })
}

/// The library's host files, as Kotlin copies them: each in the `src`
/// project's directory of its module's namespace (`sub-dir/X.cs` as
/// `src/SubDir/X.cs`), but those named for a connected class translated
/// from its Temper implementation.
pub fn files(p: &Program) -> Vec<OutFile> {
    let replaced = replaced_by_fallbacks(p, NAMES, ".cs");
    let library = &p.library.name;
    (host::files(p, &[".cs"]).into_iter())
        .filter(|f| !replaced.contains(&f.path))
        .map(|f| {
            let (dir, file) = f.path.rsplit_once('/').unwrap_or(("", &f.path));
            let mut sub = String::new();
            for part in dir.split('/').filter(|s| !s.is_empty()) {
                sub.push_str(&crate::names::root_namespace(part));
                sub.push('/');
            }
            OutFile {
                path: format!("{library}/src/{sub}{file}"),
                text: f.text.clone(),
            }
        })
        .collect()
}

/// Whether the module's class `decl` is the host code's own.
pub(super) fn is_host_class(p: &Program, decl: DeclId) -> bool {
    host::uses_host(p, decl, NAMES)
}

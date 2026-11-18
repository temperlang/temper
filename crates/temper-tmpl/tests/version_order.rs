//! Each ecosystem's own comparator sorts the version spellings
//! (`temper_tmpl::version`) in release order: dev builds, alpha 1 and its
//! later commits, alpha 2, beta, rc, the release, and a dev build after
//! it. Each runs a tool already on the machine and skips, saying so, where
//! it's missing; `TEMPER_REQUIRE_COMPARATORS=1` makes a missing one fail,
//! for CI. Where found:
//!
//! - PEP 440: `packaging`, as pip vendors it, or on its own, with
//!   `python3` (`PYTHON`);
//! - npm: node-semver, in `NODE_SEMVER` (its directory) or the VS Code
//!   extension's `node_modules`, with `node`;
//! - Maven: `maven-artifact`'s `ComparableVersion`, its jar in
//!   `MAVEN_ARTIFACT_JAR` or beside `mvn`, with `java`;
//! - NuGet: `NuGet.Versioning`, in `NUGET_VERSIONING_DLL` or the .NET
//!   SDK's directory, with `dotnet fsi`;
//! - LuaRocks: `luarocks.core.vers`, in `LUAROCKS_MODULES` (LuaRocks'
//!   `src`, or wherever its `luarocks/` is) or beside `luarocks`, with
//!   `lua` (`LUA`).
//!
//! Cargo, Go, Gradle, vcpkg, and Conan aren't run here: Cargo and Go
//! follow SemVer's precedence as npm does, and the others are checked
//! against their documented rules only.

use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use temper_tmpl::version::{Spelling, Stage, Version};

fn sequence() -> Vec<Version> {
    let b = [0, 7, 0];
    vec![
        Version::dev(b, 5),
        Version::dev(b, 123),
        Version::new(b, Stage::Alpha(1), 0),
        Version::new(b, Stage::Alpha(1), 5),
        Version::new(b, Stage::Alpha(2), 0),
        Version::new(b, Stage::Beta(1), 0),
        Version::new(b, Stage::Beta(1), 5),
        Version::new(b, Stage::Rc(1), 0),
        Version::new(b, Stage::Rc(1), 5),
        Version::new(b, Stage::Release, 0),
        Version::new(b, Stage::Release, 5),
    ]
}

fn spelled(spelling: Spelling) -> Vec<String> {
    sequence().iter().map(|v| v.spell(spelling)).collect()
}

/// A tool's output, where it ran: each script prints `wrong: …` for a
/// version out of order or not in its canonical form, then `checked`.
/// Without `checked`, the tool couldn't run.
fn run(command: &mut Command) -> Option<String> {
    let out = command.output().ok()?;
    let text = String::from_utf8_lossy(&out.stdout).to_string();
    if text.lines().any(|l| l == "checked") {
        Some(text)
    } else {
        eprintln!(
            "{command:?} didn't run:\n{text}{}",
            String::from_utf8_lossy(&out.stderr)
        );
        None
    }
}

/// Fails on what a comparator found wrong; skips, or fails under
/// `TEMPER_REQUIRE_COMPARATORS`, where it couldn't run.
fn report(ecosystem: &str, output: Option<String>) {
    match output {
        Some(text) => {
            let wrong: Vec<&str> = text.lines().filter(|l| l.starts_with("wrong")).collect();
            assert!(wrong.is_empty(), "{ecosystem}:\n{}", wrong.join("\n"));
        }
        None if env::var_os("TEMPER_REQUIRE_COMPARATORS").is_some() => {
            panic!("{ecosystem}: its comparator isn't here")
        }
        None => eprintln!("skipped: {ecosystem}'s comparator isn't here"),
    }
}

fn tool(var: &str, name: &str) -> String {
    env::var(var).unwrap_or_else(|_| name.to_string())
}

/// A program on PATH, by its full path.
fn on_path(name: &str) -> Option<PathBuf> {
    let paths = env::var_os("PATH")?;
    env::split_paths(&paths)
        .map(|dir| dir.join(name))
        .find(|p| p.is_file())
}

fn temp_file(name: &str, text: &str) -> PathBuf {
    let path = env::temp_dir().join(format!(
        "temper-version-order-{}-{name}",
        std::process::id()
    ));
    fs::write(&path, text).unwrap();
    path
}

fn quoted_list(versions: &[String]) -> String {
    let quoted: Vec<String> = versions.iter().map(|v| format!("\"{v}\"")).collect();
    quoted.join(", ")
}

#[test]
fn python_packaging() {
    let script = format!(
        "try:
    from pip._vendor.packaging.version import Version, InvalidVersion
except ImportError:
    from packaging.version import Version, InvalidVersion
texts = [{}]
versions = []
for t in texts:
    try:
        v = Version(t)
    except InvalidVersion:
        print('wrong: invalid', t)
        continue
    if str(v) != t:
        print('wrong: not canonical', t, str(v))
    versions.append(v)
for a, b in zip(versions, versions[1:]):
    if not a < b:
        print('wrong:', a, '>=', b)
print('checked')
",
        quoted_list(&spelled(Spelling::Python))
    );
    let output = run(Command::new(tool("PYTHON", "python3")).args(["-I", "-c", &script]));
    report("PEP 440", output);
}

fn node_semver() -> Option<PathBuf> {
    if let Some(dir) = env::var_os("NODE_SEMVER") {
        return Some(PathBuf::from(dir));
    }
    let dir =
        Path::new(env!("CARGO_MANIFEST_DIR")).join("../../editors/vscode/node_modules/semver");
    dir.join("package.json").is_file().then_some(dir)
}

#[test]
fn npm_semver() {
    let Some(dir) = node_semver() else {
        return report("npm", None);
    };
    let script = format!(
        "const semver = require({dir:?});
const texts = [{}];
for (const t of texts) {{
  if (semver.valid(t) !== t) console.log('wrong: not canonical', t);
}}
for (let i = 0; i + 1 < texts.length; i++) {{
  try {{
    if (semver.compare(texts[i], texts[i + 1]) >= 0) console.log('wrong:', texts[i], '>=', texts[i + 1]);
  }} catch (e) {{
    console.log('wrong:', String(e));
  }}
}}
console.log('checked');
",
        quoted_list(&spelled(Spelling::SemVer)),
        dir = dir.to_string_lossy(),
    );
    report(
        "npm",
        run(Command::new(tool("NODE", "node")).args(["-e", &script])),
    );
}

fn maven_artifact() -> Option<PathBuf> {
    if let Some(jar) = env::var_os("MAVEN_ARTIFACT_JAR") {
        return Some(PathBuf::from(jar));
    }
    let mvn = fs::canonicalize(on_path("mvn")?).ok()?;
    let lib = mvn.parent()?.parent()?.join("lib");
    (fs::read_dir(lib).ok()?)
        .filter_map(|e| e.ok().map(|e| e.path()))
        .find(|p| {
            let name = p.file_name().unwrap_or_default().to_string_lossy();
            name.starts_with("maven-artifact-") && name.ends_with(".jar")
        })
}

#[test]
fn maven_comparable_version() {
    let Some(jar) = maven_artifact() else {
        return report("Maven", None);
    };
    let versions = spelled(Spelling::SemVer);
    let out = Command::new(tool("JAVA", "java"))
        .arg("-jar")
        .arg(&jar)
        .args(&versions)
        .output();
    // Its main prints `a < b` (or `==`, `>`) for each version and the next.
    let output = out.ok().and_then(|out| {
        let text = String::from_utf8_lossy(&out.stdout).to_string();
        let compared: Vec<&str> = (text.lines())
            .map(str::trim)
            .filter(|l| [" < ", " == ", " > "].iter().any(|op| l.contains(op)))
            .collect();
        if compared.len() != versions.len() - 1 {
            eprintln!("{} didn't run:\n{text}", jar.display());
            return None;
        }
        let mut lines: Vec<String> = (compared.iter())
            .filter(|l| !l.contains(" < "))
            .map(|l| format!("wrong: {l}"))
            .collect();
        lines.push("checked".into());
        Some(lines.join("\n"))
    });
    report("Maven", output);
}

fn nuget_versioning(dotnet: &str) -> Option<PathBuf> {
    if let Some(dll) = env::var_os("NUGET_VERSIONING_DLL") {
        return Some(PathBuf::from(dll));
    }
    let out = Command::new(dotnet).arg("--list-sdks").output().ok()?;
    // `6.0.402 [/path/to/sdk]`, a line each; the last is the newest.
    let text = String::from_utf8_lossy(&out.stdout).to_string();
    (text.lines().rev())
        .filter_map(|line| {
            let (version, dir) = line.split_once(" [")?;
            let dir = dir.strip_suffix(']')?;
            Some(Path::new(dir).join(version).join("NuGet.Versioning.dll"))
        })
        .find(|p| p.is_file())
}

#[test]
fn nuget_versioning_order() {
    let dotnet = tool("DOTNET", "dotnet");
    let Some(dll) = nuget_versioning(&dotnet) else {
        return report("NuGet", None);
    };
    let script = format!(
        "#r @\"{}\"
open NuGet.Versioning
let texts = [| {} |]
let parsed = texts |> Array.choose (fun t ->
    let mutable v = Unchecked.defaultof<NuGetVersion>
    if NuGetVersion.TryParse(t, &v) then
        if v.ToNormalizedString() <> t then printfn \"wrong: not canonical %s\" t
        Some v
    else
        printfn \"wrong: invalid %s\" t
        None)
for i in 0 .. parsed.Length - 2 do
    if VersionComparer.Default.Compare(parsed.[i], parsed.[i + 1]) >= 0 then
        printfn \"wrong: %O >= %O\" parsed.[i] parsed.[i + 1]
printfn \"checked\"
",
        dll.display(),
        spelled(Spelling::SemVer)
            .iter()
            .map(|v| format!("\"{v}\""))
            .collect::<Vec<_>>()
            .join("; ")
    );
    let file = temp_file("order.fsx", &script);
    let output = run(Command::new(&dotnet)
        .args(["fsi", "--quiet"])
        .arg(&file)
        .env("DOTNET_CLI_TELEMETRY_OPTOUT", "1")
        .env("DOTNET_NOLOGO", "1")
        .env("DOTNET_SKIP_FIRST_TIME_EXPERIENCE", "1"));
    let _ = fs::remove_file(file);
    report("NuGet", output);
}

/// LuaRocks' own Lua modules: in `LUAROCKS_MODULES`, as CI has them
/// from LuaRocks' sources, or beside its `luarocks` script.
fn luarocks_modules() -> Option<PathBuf> {
    if let Some(dir) = env::var_os("LUAROCKS_MODULES") {
        return Some(PathBuf::from(dir));
    }
    let luarocks = match env::var_os("LUAROCKS") {
        Some(path) => PathBuf::from(path),
        None => on_path("luarocks")?,
    };
    // The script names where its modules are: `package.path=[[DIR/?.lua;]]`.
    let text = fs::read_to_string(&luarocks).ok()?;
    let start = text.find("package.path=[[")? + "package.path=[[".len();
    let dir = text[start..].split("?.lua").next()?;
    let dir = PathBuf::from(dir);
    dir.join("luarocks/core/vers.lua").is_file().then_some(dir)
}

#[test]
fn luarocks_vers() {
    let Some(dir) = luarocks_modules() else {
        return report("LuaRocks", None);
    };
    let script = format!(
        "package.path = {path:?} .. package.path
local vers = require('luarocks.core.vers')
local texts = {{ {list} }}
for _, t in ipairs(texts) do
  if not t:match('^[%w.]+%-%d+$') then print('wrong: not a rockspec version', t) end
end
for i = 1, #texts - 1 do
  local ok, later = pcall(vers.compare_versions, texts[i + 1], texts[i])
  if not ok or not later then print('wrong:', texts[i], '>=', texts[i + 1]) end
end
print('checked')
",
        path = format!("{}/?.lua;", dir.display()),
        list = quoted_list(&spelled(Spelling::Rockspec)),
    );
    report(
        "LuaRocks",
        run(Command::new(tool("LUA", "lua")).args(["-e", &script])),
    );
}

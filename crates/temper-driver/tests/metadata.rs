//! A library's config metadata (its license, description, authors,
//! homepage, and repository) in each backend's package files, as Kotlin
//! writes them, and what each writes for a library without any; and each
//! backend's names for a library, in its package files and in the code
//! of a library that uses it.

use temper_tmpl::{Metadata, Program, text};

/// A library, `app`, at a version, logging nothing.
#[allow(dead_code)] // Unless every backend is left out.
const APP: &str = "tmpl 1 model 1 library app 1.2.3

module app#0 {
  export fn f#1(): Void {
  }

  init {
  }
}
";

/// `app`, with its config's metadata where `full`. The authors and
/// description have characters that each format escapes.
#[allow(dead_code)] // Unless every backend is left out.
fn program(full: bool) -> Program {
    let mut program = text::read(APP).unwrap();
    if full {
        let exports = [
            ("license", "Apache-2.0 OR MIT"),
            ("description", "Apps & more, for $5"),
            ("authors", "Jo Doe, Inc. <jo@example.com>"),
            ("homepage", "https://example.com/app"),
            ("repository", "https://example.com/app.git"),
        ];
        let exports = (exports.iter())
            .map(|(k, v)| (k.to_string(), v.to_string()))
            .collect();
        program.metadata = vec![("app".into(), Metadata { exports })];
    }
    program
}

/// A generated file's text, by its path.
#[allow(dead_code)] // Unless every backend is left out.
fn file(files: Vec<temper_tmpl::OutFile>, path: &str) -> String {
    let found = files.into_iter().find(|f| f.path == path);
    found.unwrap_or_else(|| panic!("no {path}")).text
}

#[test]
#[cfg(feature = "py")]
fn python() {
    use temper_be_py::{PY, translate};
    let text = file(
        translate(&program(true), &PY).unwrap(),
        "app/pyproject.toml",
    );
    assert!(
        text.starts_with(
            "[project]\nname = \"app\"\nversion = \"1.2.3\"\n\
             description = \"Apps & more, for $5\"\n\
             readme = {text = \"n/a\", content-type = \"text/plain\"}\n\
             requires-python = \">=3.11\"\n\
             license = { text = \"Apache-2.0 OR MIT\" }\n\
             authors = [ { name = \"Jo Doe Inc.\", email = \"jo@example.com\" } ]\n"
        ),
        "{text}"
    );
    assert!(
        text.contains(
            "]\n\n[project.urls]\nHomepage = \"https://example.com/app\"\n\
             Repository = \"https://example.com/app.git\"\n\n[build-system]\n"
        ),
        "{text}"
    );
    // Without a license, Kotlin's default.
    let text = file(
        translate(&program(false), &PY).unwrap(),
        "app/pyproject.toml",
    );
    assert!(
        text.contains("description = \"\"\n")
            && text.contains("license = { text = \"LicenseRef-Proprietary\" }\nauthors = [  ]\n")
            && !text.contains("[project.urls]"),
        "{text}"
    );
}

#[test]
#[cfg(feature = "rust")]
fn rust() {
    use temper_be_rust::{RUST, translate};
    let text = file(translate(&program(true), &RUST).unwrap(), "app/Cargo.toml");
    assert!(
        text.starts_with(
            "[package]\nname = \"app\"\nversion = \"1.2.3\"\n\
             license = \"Apache-2.0 OR MIT\"\n\
             description = \"Apps & more, for $5\"\n\
             homepage = \"https://example.com/app\"\n\
             repository = \"https://example.com/app.git\"\n\
             authors = [\"Jo Doe, Inc. <jo@example.com>\"]\n\
             edition = \"2021\"\n"
        ),
        "{text}"
    );
    let text = file(translate(&program(false), &RUST).unwrap(), "app/Cargo.toml");
    assert!(
        text.starts_with("[package]\nname = \"app\"\nversion = \"1.2.3\"\nedition = \"2021\"\n"),
        "{text}"
    );
}

#[test]
#[cfg(feature = "js")]
fn js() {
    use temper_be_js::{JS, translate};
    let text = file(translate(&program(true), &JS).unwrap(), "app/package.json");
    assert!(
        text.starts_with(
            "{\n    \"name\": \"app\",\n    \"version\": \"1.2.3\",\n    \
             \"author\": \"Jo Doe, Inc. <jo@example.com>\",\n    \
             \"description\": \"Apps & more, for $5\",\n    \
             \"homepage\": \"https://example.com/app\",\n    \
             \"license\": \"Apache-2.0 OR MIT\",\n    \
             \"repository\": { \"type\": \"git\", \"url\": \"https://example.com/app.git\" },\n    \
             \"type\": \"module\",\n"
        ),
        "{text}"
    );
    let text = file(translate(&program(false), &JS).unwrap(), "app/package.json");
    assert!(
        text.starts_with(
            "{\n    \"name\": \"app\",\n    \"version\": \"1.2.3\",\n    \"type\": \"module\",\n"
        ),
        "{text}"
    );
}

#[test]
#[cfg(feature = "java")]
fn java() {
    use temper_be_java::{JAVA, translate};
    let text = file(translate(&program(true), &JAVA).unwrap(), "app/pom.xml");
    assert!(
        text.contains(
            "  <name>app</name>\n  <description>Apps &amp; more, for $5</description>\n  \
             <developers>\n    <developer>\n      \
             <name>Jo Doe, Inc. &lt;jo@example.com&gt;</name>\n    </developer>\n  \
             </developers>\n  <url>https://example.com/app</url>\n  <licenses>\n    \
             <license>\n      <name>Apache-2.0 OR MIT</name>\n    </license>\n  \
             </licenses>\n  <scm>\n    <url>https://example.com/app.git</url>\n  </scm>\n  \
             <properties>\n"
        ),
        "{text}"
    );
    let text = file(translate(&program(false), &JAVA).unwrap(), "app/pom.xml");
    assert!(
        text.contains("  <name>app</name>\n  <description>app</description>\n  <properties>\n"),
        "{text}"
    );
}

#[test]
#[cfg(feature = "csharp")]
fn csharp() {
    use temper_be_csharp::{CSHARP, translate};
    let text = file(
        translate(&program(true), &CSHARP).unwrap(),
        "app/src/App.csproj",
    );
    assert!(
        text.contains(
            "    <Version>1.2.3</Version>\n    \
             <Authors>Jo Doe, Inc. &lt;jo@example.com&gt;</Authors>\n    \
             <Description>Apps &amp; more, for $5</Description>\n    \
             <IsPackable>true</IsPackable>\n    \
             <PackageLicenseExpression>Apache-2.0 OR MIT</PackageLicenseExpression>\n    \
             <PackageProjectUrl>https://example.com/app</PackageProjectUrl>\n    \
             <RepositoryUrl>https://example.com/app.git</RepositoryUrl>\n    \
             <Nullable>enable</Nullable>\n"
        ),
        "{text}"
    );
    let text = file(
        translate(&program(false), &CSHARP).unwrap(),
        "app/src/App.csproj",
    );
    assert!(
        text.contains(
            "    <Version>1.2.3</Version>\n    <IsPackable>true</IsPackable>\n    \
             <Nullable>enable</Nullable>\n"
        ),
        "{text}"
    );
}

#[test]
#[cfg(feature = "lua")]
fn lua() {
    use temper_be_lua::{LUA, translate};
    let text = file(
        translate(&program(true), &LUA).unwrap(),
        "app/app-1.2.3-1.rockspec",
    );
    assert!(
        text.contains(
            "description = {\n    summary = 'Apps & more, for $5',\n    \
             detailed = 'Authors: Jo Doe, Inc. <jo@example.com>',\n    \
             homepage = 'https://example.com/app',\n    \
             license = 'Apache-2.0 OR MIT',\n}\n"
        ),
        "{text}"
    );
    let text = file(
        translate(&program(false), &LUA).unwrap(),
        "app/app-1.2.3-1.rockspec",
    );
    assert!(text.contains("description = {\n}\n"), "{text}");
}

#[test]
#[cfg(feature = "cpp")]
fn cpp() {
    use temper_be_cpp::{CPP, translate};
    let text = file(
        translate(&program(true), &CPP).unwrap(),
        "app/CMakeLists.txt",
    );
    assert!(
        text.contains(
            "project(app VERSION 1.2.3 DESCRIPTION \"Apps & more, for \\$5\" LANGUAGES CXX)\n\
             set(app_VERSION_FULL \"1.2.3\")\n"
        ),
        "{text}"
    );
    let text = file(
        translate(&program(false), &CPP).unwrap(),
        "app/CMakeLists.txt",
    );
    assert!(
        text.contains("project(app VERSION 1.2.3 LANGUAGES CXX)\n"),
        "{text}"
    );
}

/// `app`, which uses `util`, each with every backend's name for it, but
/// Lua's and C++'s, which take none.
#[allow(dead_code)] // Unless every backend is left out.
const USES: &str = "tmpl 1 model 1 library app 1.0.0

uses `util`#5 1.2.0 {
  module ``#8 {
    fn u#9(): Void;
  }
}

module app#0 {
  export fn f#1(): Void {
    u#9();
  }

  init {
  }
}
";

#[allow(dead_code)] // Unless every backend is left out.
const UTIL: &str = "tmpl 1 model 1 library util 1.2.0

module util#0 {
  export fn u#1(): Void {
  }

  init {
  }
}
";

/// [`USES`]'s `app`, for a backend's naming rules, with names for it and
/// `util` in their configs.
#[allow(dead_code)] // Unless every backend is left out.
fn named(rules: &temper_tmpl::naming::NamingRules) -> Program {
    use temper_tmpl::naming::{self, Interface};
    let mut program = text::read(USES).unwrap();
    let util = text::read(UTIL).unwrap();
    let names = naming::assign(&util, rules);
    program.interfaces.push(Interface::of(&util, &names));
    let config = |pairs: &[(&str, &str)]| Metadata {
        exports: (pairs.iter())
            .map(|(k, v)| (k.to_string(), v.to_string()))
            .collect(),
    };
    program.metadata = vec![
        (
            "app".into(),
            config(&[
                ("pyName", "App_Py"),
                ("jsName", "@example/app-js"),
                ("rustName", "app-rs"),
                ("javaName", "app-java"),
                ("javaPackage", "com.example.app"),
                ("csharpRootNamespace", "Example.App"),
            ]),
        ),
        (
            "util".into(),
            config(&[
                ("pyName", "util-py"),
                ("jsName", "util-js"),
                ("rustName", "util-rs"),
                ("javaName", "util-java"),
                ("javaGroup", "com.example"),
                ("javaArtifact", "util-lib"),
                ("csharpRootNamespace", "Example.Util"),
            ]),
        ),
    ];
    program
}

/// A file's text, by its path, from what a backend made of [`named`].
#[allow(dead_code)] // Unless every backend is left out.
fn named_file(
    backend: fn(&Program) -> Vec<temper_tmpl::OutFile>,
    rules: &temper_tmpl::naming::NamingRules,
    path: &str,
) -> String {
    file(backend(&named(rules)), path)
}

#[test]
#[cfg(feature = "py")]
fn python_names() {
    use temper_be_py::{PY, translate};
    let made = |p: &Program| translate(p, &PY).unwrap();
    // The project, normalized, and its package, in the library's
    // directory; and the other library by its project and package.
    let text = named_file(made, &PY.rules, "app/pyproject.toml");
    assert!(text.contains("name = \"app-py\"\n"), "{text}");
    assert!(text.contains("    \"util-py==1.2.0\"\n"), "{text}");
    let text = named_file(made, &PY.rules, "app/App_Py/App_Py.py");
    assert!(text.contains("from util_py import u as _u\n"), "{text}");
}

#[test]
#[cfg(feature = "js")]
fn js_names() {
    use temper_be_js::{JS, translate};
    let made = |p: &Program| translate(p, &JS).unwrap();
    let text = named_file(made, &JS.rules, "app/package.json");
    assert!(text.contains("\"name\": \"@example/app-js\",\n"), "{text}");
    assert!(text.contains("        \"util-js\": \"1.2.0\"\n"), "{text}");
    let text = named_file(made, &JS.rules, "app/app.internal.js");
    assert!(text.contains("import { u } from \"util-js\";\n"), "{text}");
}

#[test]
#[cfg(feature = "rust")]
fn rust_names() {
    use temper_be_rust::{RUST, translate};
    let made = |p: &Program| translate(p, &RUST).unwrap();
    let text = named_file(made, &RUST.rules, "app/Cargo.toml");
    assert!(text.contains("name = \"app-rs\"\n"), "{text}");
    assert!(
        text.contains("util-rs = { path = \"../util\", version = \"1.2.0\" }\n"),
        "{text}"
    );
    let text = named_file(made, &RUST.rules, "app/src/mod.rs");
    assert!(text.contains("    util_rs::u();\n"), "{text}");
    let text = named_file(made, &RUST.rules, "app/src/main.rs");
    assert!(text.contains("    app_rs::init(None)"), "{text}");
}

#[test]
#[cfg(feature = "java")]
fn java_names() {
    use temper_be_java::{JAVA, translate};
    let made = |p: &Program| translate(p, &JAVA).unwrap();
    // The group is the package, where no `javaGroup` is given; the
    // artifact the `javaName`; and for `util`, its group and artifact,
    // and its package from its `javaName`.
    let text = named_file(made, &JAVA.rules, "app/pom.xml");
    assert!(
        text.contains(
            "  <groupId>com.example.app</groupId>\n  <artifactId>app-java</artifactId>\n"
        ),
        "{text}"
    );
    assert!(
        text.contains(
            "      <groupId>com.example</groupId>\n      <artifactId>util-lib</artifactId>\n"
        ),
        "{text}"
    );
    let text = named_file(
        made,
        &JAVA.rules,
        "app/src/main/java/com/example/app/AppGlobal.java",
    );
    assert!(
        text.starts_with("package com.example.app;\n\nimport util_java.UtilJavaGlobal;\n"),
        "{text}"
    );
}

#[test]
#[cfg(feature = "csharp")]
fn csharp_names() {
    use temper_be_csharp::{CSHARP, translate};
    let made = |p: &Program| translate(p, &CSHARP).unwrap();
    let text = named_file(made, &CSHARP.rules, "app/src/Example.App.csproj");
    assert!(
        text.contains("<RootNamespace>Example.App</RootNamespace>")
            && text.contains("<ProjectReference Include=\"../../util/src/Example.Util.csproj\"/>"),
        "{text}"
    );
    let text = named_file(made, &CSHARP.rules, "app/src/AppGlobal.cs");
    assert!(
        text.starts_with("using Example.Util;\n\nnamespace Example.App;\n"),
        "{text}"
    );
}

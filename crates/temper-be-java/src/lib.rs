//! Temper's Java backend: tmpl in, Java out.
//!
//! Works from tmpl alone (notes/tmpl/overview.md, "Boundaries"). Its
//! variants share the translation and differ only in data
//! (notes/tmpl/profiles.md, "Variants and the first profiles"); the first,
//! [`JAVA`], is Kotlin-compatible: the same Maven package and layout
//! (`pom.xml`, and a package per library, holding a module's statics in
//! `NAMEGlobal` and its entry point in `NAMEMain`), `temper-core` runtime,
//! public names, and Java 17, as Kotlin Temper's `java` backend, with
//! prettier bodies: no numbered names, no class names before calls and
//! fields of the class's own, `+` converting numbers in concatenations,
//! sorted imports, and labels only where a jump needs one. `javac
//! -Xlint:all -Werror` passes on the output.
//!
//! The pipeline: tmpl's lowerings, the program partitioned by reach
//! ([`temper_tmpl::reach::partition`]: what only tests reach in the test
//! sources, what nothing does left out), the naming pass
//! ([`temper_tmpl::naming::assign`]) with Java's rules
//! ([`names::RULES`]), then [`translate`] into Java's output
//! tree ([`tree`]), with operators spelled by [`ops`] and core's connected
//! declarations by [`connected`], then the printer.
//!
//! It covers libraries of several modules,
//! imports between them and from other libraries, and tests, and classes
//! and interfaces, each a file of its own as Kotlin's
//! (`translate::classes` says how); and nullable types, as Kotlin's,
//! `@Nullable` and boxed (`@Nullable Integer`), with `orelse` as a `try`
//! and `catch (Bubble …)`, `bubble()` as `throw Core.bubble()`, and
//! `panic()` as an `AssertionError`, which no `orelse` catches; unlike
//! Kotlin's, `catch (RuntimeException …)`, a handler catches only the
//! runtime's `temper.core.Bubble` (unchecked, without a stack trace), which
//! `as` and every builtin that bubbles throw, the runtime turning host
//! failures (`NumberFormatException`, `DateTimeException`, a broken
//! promise's `ExecutionException`) into one; so anything else, a host
//! exception from a builtin not declared to bubble or from a callback,
//! passes every `orelse`, and a library's host code (its `.java` files)
//! bubbles only by throwing a `Bubble` (`throw Core.bubble()`); and
//! declared failures (RFC 164) as unchecked exceptions, each library's
//! failure types under its roots, `WherewithException extends
//! RuntimeException implements temper.core.Failure`, `throw` as Java's,
//! and handlers that catch exactly what their bodies may fail with
//! (`translate` says how); and generics, with boxed type arguments (`List<Integer>`) and explicit
//! ones on `new`, core's lists as `java.util.List`s, maps as
//! `LinkedHashMap`s, and pairs as `Map.Entry`s, as Kotlin's, but with two
//! boxes compared by value; lists that may hold null are made by
//! `Core.listOf` and `Core.listCopyOf`, since `List.of` and `List.copyOf`
//! reject null; and a library's own connected functions and classes,
//! with host code in its own `.java` files ([`translate()`] says how);
//! anything else is [`NotYet`]. No source maps yet.

pub mod connected;
pub mod names;
pub mod ops;
pub mod runtime;
// Not in wasm, which runs no tools.
#[cfg(any(unix, windows))]
pub mod tools;
pub mod translate;
pub mod tree;

use std::fmt;
use temper_tmpl::coroutines::CoroutineStrategy;
use temper_tmpl::naming::{NamingRules, assign_checked};
use temper_tmpl::{Features, Profile, Program};

/// A named set of translation choices for Java.
pub struct Variant {
    /// Its name on the command line: `java`.
    pub name: &'static str,
    /// What it handles natively, which lowering and validation read.
    pub profile: Profile,
    pub rules: NamingRules,
    /// The Java release its output targets, as `javac --release` and
    /// Maven's compiler plugin take it.
    pub java_release: &'static str,
}

/// The first variant: Kotlin-compatible API and names, pretty bodies,
/// Java 17. Everything is native: statements inside expressions (a
/// block's value, `orelse`) the translator puts before the statement
/// around them itself, until a hoisting pass does.
pub static JAVA: Variant = Variant {
    name: "java",
    profile: Profile {
        native: Features::STATEMENT_EXPRESSIONS.with(Features::SWITCH),
        coroutines: CoroutineStrategy::TranslateToRegularFunction,
    },
    rules: names::RULES,
    java_release: "17",
};

/// Every variant, by name.
pub const VARIANTS: &[&Variant] = &[&JAVA];

/// Something the translation doesn't cover yet.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct NotYet {
    pub what: String,
}

impl fmt::Display for NotYet {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "not yet in the Java backend: {}", self.what)
    }
}

impl std::error::Error for NotYet {}

pub use temper_tmpl::OutFile;

/// This backend, as tools list it ([`temper_tmpl::Backend`]).
pub const BACKEND: temper_tmpl::Backend = temper_tmpl::Backend {
    name: "java",
    comment: "//",
    runtime: if cfg!(target_family = "wasm") {
        &[]
    } else {
        runtime::FILES
    },
    runtime_version: &runtime::VERSION,
    rules: &JAVA.rules,
    translate: |p, _| translate(p, &JAVA).map_err(|e| e.to_string()),
    default: true,
    #[cfg(any(unix, windows))]
    tools: Some(&tools::TOOLS),
    #[cfg(not(any(unix, windows)))]
    tools: None,
};

/// Translates a library, laid out as Kotlin's Java backend lays it out,
/// a package per module:
///
/// ```text
/// mods/pom.xml                                    Maven's, depending on temper-core
/// mods/src/main/java/mods/ModsGlobal.java         the library's root module
/// mods/src/main/java/mods/ModsMain.java           its entry point
/// mods/src/main/java/mods/Shape.java             each class and interface
/// mods/src/main/java/mods/helpers/HelpersGlobal.java  module `helpers`
/// mods/src/main/java/mods/helpers/HelpersMain.java    its entry point
/// mods/src/test/java/mods/ModsTest.java           the root module's tests
/// mods/src/test/java/mods/TestShape.java          what only they reach
/// mods/src/main/java/mods/ModsConnected.java      host code, copied
/// ```
///
/// A module's test class has what only its tests reach, as statics, and
/// the classes and interfaces only they reach are test sources too, in
/// its package, so they see its package-private statics; a library only
/// the tests use is a `test`-scoped dependency.
///
/// A library's own `.java` files, its host code, go into their modules'
/// packages as they are, as Kotlin copies them: a module's connected
/// functions call static methods of its `NAMEConnected` class (`return
/// ModsConnected.sum(i, j, bonus);`, after their parameters' defaults),
/// and a connected class is its own file, `Counter.java`, in place of the
/// generated one. Which use host code is `temper_tmpl::host::uses_host`'s
/// choice, for [`translate::NAMES`]; a connected class generated from its
/// Temper implementation leaves its file out (Kotlin's
/// `connectedFilesReplacedByFallbacks`). Host code that's needed but
/// missing is [`NotYet`], where Kotlin's fails in `javac`.
///
/// The package takes the library's name, with `_` for `-`
/// ([`names::package`]), and a module's path after it; the classes take
/// the package's last part in Pascal case. Kotlin also copies
/// `temper-core` beside the package; `temper build` does too
/// ([`runtime`]).
/// std's modules with support code, as Kotlin's Java backend writes it
/// into std's output: each module's path, and its support class's file
/// (begun as Kotlin's, in `runtime/std/`), in the module's package, whose static
/// methods its connected members call
/// ([`connected::Connection::StdStatic`]).
pub const STD_SUPPORT: &[(&str, &str, &str)] = &[
    (
        "regex",
        "Core.java",
        include_str!("../runtime/std/regex/Core.java"),
    ),
    (
        "temporal",
        "Core.java",
        include_str!("../runtime/std/temporal/Core.java"),
    ),
];

pub fn translate(p: &Program, variant: &Variant) -> Result<Vec<OutFile>, NotYet> {
    // Type-only errors run as written, or fail where they are
    // (`temper_tmpl::errors`), by the profile.
    let settled = temper_tmpl::errors::settle(p, &variant.profile);
    let p = settled.as_ref().unwrap_or(p);
    // `Never<T>` as `T`, but where the profile keeps it
    // (`temper_tmpl::nevers`).
    let nevered = temper_tmpl::nevers::lower(p, &variant.profile);
    let p = nevered.as_ref().unwrap_or(p);
    // Handlers whose bodies can't fail as their bodies alone
    // (`temper_tmpl::failures`).
    let alive = temper_tmpl::failures::drop_dead_handlers(p);
    let p = alive.as_ref().unwrap_or(p);
    // Generators as state machines (`temper_tmpl::coroutines`).
    let lowered = temper_tmpl::coroutines::lower(p, variant.profile.coroutines)
        .map_err(|what| NotYet { what })?;
    let p = lowered.as_ref().unwrap_or(p);
    // A captured `var` parameter as a local, which may be a cell.
    let copied = temper_tmpl::cells::copy_captured_params(p);
    let p = copied.as_ref().unwrap_or(p);
    // Java's `switch` takes no `long`: those as `if`s.
    let switched = temper_tmpl::switches::to_if_chains_where(p, &|s| {
        let scrutinee = p.nodes.kids(s)[0];
        p.nodes.ty(scrutinee) == temper_tmpl::TypeId::INT64
    });
    let p = switched.as_ref().unwrap_or(p);
    // What only tests reach apart, in the test sources, and what nothing
    // reaches left out (`temper_tmpl::reach`), as the lowerings left it.
    let parted = temper_tmpl::reach::partition(p, connected::connects_std);
    let p = &parted;
    let names = assign_checked(p, &variant.rules).map_err(|what| NotYet { what })?;
    let library = &p.library.name;
    let mut sources = Vec::new();
    let mut tests = Vec::new();
    let mut mains = Vec::new();
    let mut root_main = String::new();
    for module in &p.modules {
        let (package, base) = translate::module_names(p, module.decl);
        let global = format!("{base}Global");
        let main = format!("{base}Main");
        let mut translator = translate::Translator::new(p, &names, &package, &global);
        let class = translator.module(module)?;
        let dir = package.replace('.', "/");
        let main_dir = format!("{library}/src/main/java/{dir}");
        let test_dir = format!("{library}/src/test/java/{dir}");
        sources.push(OutFile {
            path: format!("{main_dir}/{global}.java"),
            text: tree::class(&class),
        });
        for (tests, dir) in [(false, &main_dir), (true, &test_dir)] {
            for t in translator.types(module, tests)? {
                sources.push(OutFile {
                    path: format!("{dir}/{}.java", t.name),
                    text: tree::type_decl(&t),
                });
            }
        }
        sources.push(OutFile {
            path: format!("{main_dir}/{main}.java"),
            text: main_class(&package, &main, &loads(p, module)),
        });
        if let Some(class) = translator.tests(module)? {
            if !class.tests.is_empty() {
                tests.push(format!("{package}.{}", class.statics.name));
            }
            sources.push(OutFile {
                path: format!("{test_dir}/{}.java", class.statics.name),
                text: tree::test_class(&class),
            });
        }
        // The functional interfaces it synthesized, as Kotlin's.
        for (tests, dir) in [(false, &main_dir), (true, &test_dir)] {
            for (path, text) in translator.synthesized(tests) {
                sources.push(OutFile {
                    path: format!("{dir}/{path}"),
                    text,
                });
            }
        }
        // std's support code for the module, in its package, as Kotlin's.
        let support = STD_SUPPORT
            .iter()
            .filter(|(m, ..)| library == "std" && module.path == *m);
        for (_, name, text) in support {
            sources.push(OutFile {
                path: format!("{library}/src/main/java/{dir}/{name}"),
                text: text.to_string(),
            });
        }
        if module.path.is_empty() {
            root_main = format!("{package}.{main}");
        }
        mains.push(format!("{package}.{main}"));
    }
    // The library's own Java files, its host code, into its modules'
    // packages, as Kotlin copies them, but for a connected class's file
    // where the class is generated from its Temper implementation.
    let replaced = temper_tmpl::host::replaced_by_fallbacks(p, translate::NAMES, ".java");
    for file in temper_tmpl::host::files(p, &[".java"]) {
        if replaced.contains(&file.path) {
            continue;
        }
        let (dir, name) = file.path.rsplit_once('/').unwrap_or(("", &file.path));
        let package = match p.modules.iter().find(|m| m.path == dir) {
            Some(m) => translate::module_package(p, m.decl),
            None => (dir.split('/').filter(|s| !s.is_empty()))
                .fold(translate::library_package(p, library), |out, part| {
                    format!("{out}.{}", names::package(part))
                }),
        };
        sources.push(OutFile {
            path: format!(
                "{library}/src/main/java/{}/{name}",
                package.replace('.', "/")
            ),
            text: file.text.clone(),
        });
    }
    let pom = pom(p, library, &root_main, &mains, &tests, variant);
    Ok([OutFile {
        path: format!("{library}/pom.xml"),
        text: pom,
    }]
    .into_iter()
    .chain(sources)
    .collect())
}

/// The classes a module's entry point loads, in order: those of other
/// libraries' modules it imports, then its own library's modules it
/// imports, directly or not, in the order they run, then its own. Each
/// class's static initializer is its module's init, which the JVM runs
/// when the class is first used, so this runs them in Temper's order,
/// imports first, even where nothing reads them.
fn loads(p: &Program, module: &temper_tmpl::Module) -> Vec<String> {
    use temper_tmpl::analysis::module_imports;
    let mut needed = vec![module.decl];
    let mut external = Vec::new();
    let mut i = 0;
    while i < needed.len() {
        let m = p.modules.iter().find(|m| m.decl == needed[i]);
        for import in m.map(|m| module_imports(p, m)).unwrap_or_default() {
            let own = p.modules.iter().any(|m| m.decl == import.from);
            if own && !needed.contains(&import.from) {
                needed.push(import.from);
            } else if !own && !external.contains(&import.from) {
                external.push(import.from);
            }
        }
        i += 1;
    }
    external.sort();
    let order = (p.modules.iter())
        .filter(|m| needed.contains(&m.decl))
        .map(|m| m.decl);
    let class = |m| {
        let (package, class) = translate::global_class(p, m);
        format!("{package}.{class}")
    };
    external.into_iter().chain(order).map(class).collect()
}

/// The entry point, as Kotlin's: logging to stdout, bubbles with stack
/// traces (so one that escapes says where), then the module's
/// class loaded, after those of the modules it imports
/// ([`loads`]), which runs their static initializers, in a top-level turn,
/// so async blocks start after them (as Temper PR #532 has Kotlin's), then
/// any async tasks waited for, the run failing if one failed
/// (`Core.finishProgram`).
fn main_class(package: &str, main: &str, loads: &[String]) -> String {
    let loads = (loads.iter())
        .map(|class| format!("            Class.forName(\"{class}\");\n"))
        .collect::<Vec<_>>()
        .concat();
    format!(
        "package {package};

import temper.core.Bubble;
import temper.core.Core;

public final class {main} {{
    private {main}() {{}}

    public static void main(String[] args) throws ClassNotFoundException {{
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {{
{loads}        }} finally {{
            Core.endTopLevel();
        }}
        Core.finishProgram();
    }}
}}
"
    )
}

/// A library's Maven group and artifact, as Kotlin's configuration has
/// them: its config's `javaGroup`, or else its `javaPackage`, or else its
/// Java name (its `javaName`, or else its own) as Java names a package;
/// and its `javaArtifact`, or else its Java name. std's Java
/// configuration sets them (`dev.temperlang` and `temper-std`).
fn coordinates(p: &Program, library: &str) -> (String, String) {
    let meta = p.metadata_of(library);
    let name = meta.get("javaName").unwrap_or(library);
    match library {
        "std" => ("dev.temperlang".to_string(), "temper-std".to_string()),
        _ => (
            (meta.get("javaGroup").or(meta.get("javaPackage")))
                .map_or_else(|| names::package(name), temper_tmpl::xml_text),
            temper_tmpl::xml_text(meta.get("javaArtifact").unwrap_or(name)),
        ),
    }
}

/// The config's metadata, as Kotlin's `pomXml` writes it after the name:
/// the description, which Maven Central requires, so the library's name
/// where there's none; then the authors as the developer, the homepage
/// (or else the repository) as the URL, the license, and the repository
/// as the source's URL, for those set.
fn pom_metadata(p: &Program, library: &str) -> String {
    let meta = p.metadata();
    let xml = temper_tmpl::xml_text;
    let description = meta.get("description").unwrap_or(library);
    let mut out = format!("  <description>{}</description>\n", xml(description));
    if let Some(authors) = meta.get("authors") {
        out.push_str(&format!(
            "  <developers>\n    <developer>\n      <name>{}</name>\n    </developer>\n  </developers>\n",
            xml(authors)
        ));
    }
    if let Some(url) = meta.get("homepage").or(meta.get("repository")) {
        out.push_str(&format!("  <url>{}</url>\n", xml(url)));
    }
    if let Some(license) = meta.get("license") {
        out.push_str(&format!(
            "  <licenses>\n    <license>\n      <name>{}</name>\n    </license>\n  </licenses>\n",
            xml(license)
        ));
    }
    if let Some(url) = meta.get("repository") {
        out.push_str(&format!("  <scm>\n    <url>{}</url>\n  </scm>\n", xml(url)));
    }
    out
}

/// The package description, as Kotlin's `pomXml` writes it for a library
/// without tests: Maven Central's metadata and plugins, the Java release,
/// and the main class for the jar's manifest and `exec:java`.
fn pom(
    p: &Program,
    library: &str,
    main: &str,
    mains: &[String],
    tests: &[String],
    variant: &Variant,
) -> String {
    let version = p.version_or_default();
    let (group, artifact) = coordinates(p, library);
    // Each library used, by its coordinates; for the tests alone, in
    // their scope.
    let tests_only = temper_tmpl::analysis::test_only_libraries(p);
    let mut dependencies = (p.dependencies().iter())
        .map(|&(name, version)| {
            let (group, artifact) = coordinates(p, name);
            let scope = match tests_only.iter().any(|l| l == name) {
                true => "test",
                false => "compile",
            };
            format!(
                r#"
    <dependency>
      <groupId>{group}</groupId>
      <artifactId>{artifact}</artifactId>
      <version>{}</version>
      <type>jar</type>
      <scope>{scope}</scope>
    </dependency>"#,
                temper_tmpl::version_or_default(version),
            )
        })
        .collect::<Vec<_>>()
        .concat();
    // JUnit for the tests, and for std's `testing` in its code, as std's
    // Java configuration has it.
    let junit = match library {
        "std" => Some("compile"),
        _ if !tests.is_empty() => Some("test"),
        _ => None,
    };
    if let Some(scope) = junit {
        dependencies.push_str(&format!(
            r#"
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.9.2</version>
      <type>jar</type>
      <scope>{scope}</scope>
    </dependency>"#,
        ));
    }
    let executions = (mains.iter())
        .map(|main| {
            format!(
                r#"
          <execution>
            <id>{main}</id>
            <goals>
              <!-- Run in the Maven VM via `mvn compile exec:java@{main}` -->
              <goal>java</goal>
              <!-- Run in a forked JVM via `mvn compile exec:exec@{main}` -->
              <goal>exec</goal>
            </goals>
            <configuration>
              <!-- Used by exec:java -->
              <mainClass>{main}</mainClass>
              <!-- Used by exec:exec -->
              <executable>java</executable>
              <arguments>
                <argument>-classpath</argument>
                <argument>${{project.build.outputDirectory}}</argument>
                <argument>{main}</argument>
              </arguments>
            </configuration>
          </execution>"#
            )
        })
        .collect::<Vec<_>>()
        .concat();
    let surefire = if tests.is_empty() {
        String::new()
    } else {
        let includes = (tests.iter())
            .map(|t| format!("\n            <include>{t}</include>"))
            .collect::<Vec<_>>()
            .concat();
        format!(
            r#"
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.0.0</version>
        <configuration>
          <!-- Configuration is mostly documented in the source: https://github.com/apache/maven-surefire/blob/surefire-3.0.0/maven-surefire-plugin/src/main/java/org/apache/maven/plugin/surefire/SurefirePlugin.java -->
          <reportFormat>xml</reportFormat>
          <includes>{includes}
          </includes>
        </configuration>
      </plugin>"#
        )
    };
    let runtime = temper_tmpl::version::current();
    let release = variant.java_release;
    let metadata = pom_metadata(p, library);
    format!(
        r#"<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>{group}</groupId>
  <artifactId>{artifact}</artifactId>
  <version>{version}</version>
  <name>{library}</name>
{metadata}  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>dev.temperlang</groupId>
      <artifactId>temper-core</artifactId>
      <version>{runtime}</version>
      <type>jar</type>
      <scope>compile</scope>
    </dependency>{dependencies}
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.sonatype.central</groupId>
        <artifactId>central-publishing-maven-plugin</artifactId>
        <version>0.8.0</version>
        <extensions>true</extensions>
        <configuration>
          <publishingServerId>central</publishingServerId>
          <autoPublish>true</autoPublish>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-source-plugin</artifactId>
        <version>3.3.0</version>
        <executions>
          <execution>
            <id>attach-sources</id>
            <goals>
              <goal>jar-no-fork</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-javadoc-plugin</artifactId>
        <version>3.5.0</version>
        <configuration>
          <failOnError>false</failOnError>
          <doclint>-missing</doclint>
          <docencoding>UTF-8</docencoding>
        </configuration>
        <executions>
          <execution>
            <id>attach-javadocs</id>
            <goals>
              <goal>jar</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-gpg-plugin</artifactId>
        <version>3.1.0</version>
        <executions>
          <execution>
            <id>sign-artifacts</id>
            <phase>verify</phase>
            <goals>
              <goal>sign</goal>
            </goals>
            <configuration>
              <id>${{gpg.keyname}}</id>
              <id>${{gpg.keyname}}</id>
            </configuration>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.11.0</version>
        <configuration>
          <release>{release}</release>
        </configuration>
      </plugin>
      <plugin>
        <!-- https://maven.apache.org/plugins/maven-jar-plugin/ -->
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-jar-plugin</artifactId>
        <version>3.3.0</version>
        <configuration>
          <archive>
            <!-- https://maven.apache.org/shared/maven-archiver/index.html details contents -->
            <manifest>
              <mainClass>{main}</mainClass>
            </manifest>
          </archive>
        </configuration>
      </plugin>
      <plugin>
        <!-- https://www.mojohaus.org/exec-maven-plugin/ -->
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.1.0</version>
        <executions>{executions}
        </executions>
      </plugin>{surefire}
    </plugins>
  </build>
</project>
"#
    )
}

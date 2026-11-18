//! Libraries a program uses, besides the runtime (a tmpl `uses`
//! section), in each backend's package files, as Kotlin writes a
//! dependency on another library: by its name, with its version where
//! it has one. Hand-written tmpl, calling a function of each library's
//! root module, as the frontend's imports make it.

use temper_tmpl::naming::{self, Interface, NamingRules};
#[allow(unused_imports)] // Unless every backend is left out.
use temper_tmpl::version::{Spelling, current};
use temper_tmpl::{Profile, Program, text, validate};

/// A library, `app`, that uses `util`, with a version, and `plain`,
/// without one, calling a function of each, and the runtime, logging one
/// line.
const APP: &str = "tmpl 1 model 1 library app 0.0.0

uses `temper-core`#1 0.1.0 {
  interface Console#2 {
    connected method log#3(message: String): Void;
  }
  connected fn getConsole#4(name?: String): Console#2;
}

uses `util`#5 1.2.0 {
  module ``#8 {
    fn u#9(): Void;
  }
}

uses `plain`#6 0.0.0 {
  module ``#10 {
    fn p#11(): Void;
  }
}

module app#0 {
  let ~console#7: Console#2;

  init {
    init ~console#7 = getConsole#4(omitted);
    ~console#7.log#3(\"hi\");
    u#9();
    p#11();
  }
}
";

/// `util` and `plain`, whose own namings name what `app` uses of them.
const UTIL: &str = "tmpl 1 model 1 library util 1.2.0

module util#0 {
  export fn u#1(): Void {
  }

  init {
  }
}
";

const PLAIN: &str = "tmpl 1 model 1 library plain 0.0.0

module plain#0 {
  export fn p#1(): Void {
  }

  init {
  }
}
";

/// `app`, with what `util`'s and `plain`'s namings for a backend named,
/// as a build gives it.
#[allow(dead_code)] // Unless every backend is left out.
fn program_for(rules: &NamingRules) -> Program {
    let mut program = program();
    for dependency in [UTIL, PLAIN] {
        let dependency = text::read(dependency).unwrap();
        let names = naming::assign(&dependency, rules);
        program.interfaces.push(Interface::of(&dependency, &names));
    }
    program
}

fn program() -> Program {
    let program = text::read(APP).unwrap();
    let problems = validate(&program, &Profile::full());
    assert!(problems.is_empty(), "{problems:#?}");
    assert_eq!(
        program.dependencies(),
        [("plain", "0.0.0"), ("util", "1.2.0")]
    );
    program
}

/// A generated file's text, by its path.
#[allow(dead_code)] // Unless every backend is left out.
fn file<'a>(files: &'a [(String, String)], path: &str) -> &'a str {
    &files.iter().find(|(p, _)| p == path).unwrap().1
}

#[test]
#[cfg(feature = "py")]
fn python() {
    let files: Vec<(String, String)> =
        temper_be_py::translate(&program_for(&temper_be_py::PY.rules), &temper_be_py::PY)
            .unwrap()
            .into_iter()
            .map(|f| (f.path, f.text))
            .collect();
    let text = file(&files, "app/pyproject.toml");
    assert!(
        text.contains(&format!(
            "dependencies = [\n    \"plain\",\n    \"temper-core=={}\",\n    \"util==1.2.0\"\n]\n",
            current().spell(Spelling::Python)
        )),
        "{text}"
    );
}

#[test]
#[cfg(feature = "rust")]
fn rust() {
    let files: Vec<(String, String)> = temper_be_rust::translate(
        &program_for(&temper_be_rust::RUST.rules),
        &temper_be_rust::RUST,
    )
    .unwrap()
    .into_iter()
    .map(|f| (f.path, f.text))
    .collect();
    let text = file(&files, "app/Cargo.toml");
    assert!(
        text.ends_with(&format!(
            "[dependencies]\n\
             temper-core = {{ path = \"../temper-core\", version = \"={}\" }}\n\
             plain = {{ path = \"../plain\", version = \"0.0.1\" }}\n\
             util = {{ path = \"../util\", version = \"1.2.0\" }}\n",
            current()
        )),
        "{text}"
    );
}

#[test]
#[cfg(feature = "js")]
fn js() {
    let files: Vec<(String, String)> =
        temper_be_js::translate(&program_for(&temper_be_js::JS.rules), &temper_be_js::JS)
            .unwrap()
            .into_iter()
            .map(|f| (f.path, f.text))
            .collect();
    let text = file(&files, "app/package.json");
    assert!(
        text.contains(&format!(
            "    \"dependencies\": {{\n        \"@temperlang/core\": \"{}\",\n        \
             \"plain\": \"*\",\n        \"util\": \"1.2.0\"\n    }}\n",
            current()
        )),
        "{text}"
    );
}

#[test]
#[cfg(feature = "java")]
fn java() {
    let files: Vec<(String, String)> = temper_be_java::translate(
        &program_for(&temper_be_java::JAVA.rules),
        &temper_be_java::JAVA,
    )
    .unwrap()
    .into_iter()
    .map(|f| (f.path, f.text))
    .collect();
    let text = file(&files, "app/pom.xml");
    assert!(
        text.contains(
            "    <dependency>\n      <groupId>util</groupId>\n      <artifactId>util</artifactId>\n      \
             <version>1.2.0</version>\n      <type>jar</type>\n      <scope>compile</scope>\n    </dependency>\n"
        ) && text.contains("<artifactId>plain</artifactId>\n      <version>0.0.1</version>"),
        "{text}"
    );
}

#[test]
#[cfg(feature = "csharp")]
fn csharp() {
    let files: Vec<(String, String)> = temper_be_csharp::translate(
        &program_for(&temper_be_csharp::CSHARP.rules),
        &temper_be_csharp::CSHARP,
    )
    .unwrap()
    .into_iter()
    .map(|f| (f.path, f.text))
    .collect();
    let text = file(&files, "app/src/App.csproj");
    assert!(
        text.contains(
            "    <ProjectReference Include=\"../../temper-core/TemperLang.Core.csproj\"/>\n    \
             <ProjectReference Include=\"../../plain/src/Plain.csproj\"/>\n    \
             <ProjectReference Include=\"../../util/src/Util.csproj\"/>\n"
        ),
        "{text}"
    );
}

#[test]
#[cfg(feature = "lua")]
fn lua() {
    let files: Vec<(String, String)> =
        temper_be_lua::translate(&program_for(&temper_be_lua::LUA.rules), &temper_be_lua::LUA)
            .unwrap()
            .into_iter()
            .map(|f| (f.path, f.text))
            .collect();
    let text = file(&files, "app/app-dev-1.rockspec");
    assert!(
        text.contains(&format!(
            "    'temper-core == {}',\n    'plain',\n    'util == 1.2.0',\n}}\n",
            current().spell(Spelling::LuaRocks)
        )),
        "{text}"
    );
}

#[test]
#[cfg(feature = "cpp")]
fn cpp() {
    let files: Vec<(String, String)> =
        temper_be_cpp::translate(&program_for(&temper_be_cpp::CPP.rules), &temper_be_cpp::CPP)
            .unwrap()
            .into_iter()
            .map(|f| (f.path, f.text))
            .collect();
    let text = file(&files, "app/CMakeLists.txt");
    assert!(
        text.contains(
            "if(NOT TARGET util)\n  add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../util ${CMAKE_CURRENT_BINARY_DIR}/util)\nendif()\n"
        ) && text.contains("target_link_libraries(app PUBLIC Threads::Threads plain util)\n"),
        "{text}"
    );
}

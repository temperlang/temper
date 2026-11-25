//! Temper syntax: from source text to tokens to trees.
//!
//! - [`literate`] finds code blocks and paragraphs in `.temper.md` files.
//! - [`lex`] splits source into tokens, as Kotlin Temper's lexer does.
//! - [`adapt`] adjusts tokens for the parser: semicolons, strings, call
//!   joins and modifier words.
//! - [`balance`] recovers from brackets that don't balance, for [`adapt`].
//! - [`parse`] builds a [`tree::Tree`] of operators, using the table in
//!   [`operator`] and interning words in [`names`].
//! - [`ast`] turns a parse tree into calls, blocks and declarations, with
//!   the productions in `grammar` and token values from [`unpack`].
//! - [`module`] finishes a module's tree as Kotlin's `ParseStage` does:
//!   metadata as JSON, and call joins rewritten.
//! - [`print`](mod@print) writes trees as text, for tests and comparisons.
//! - [`fmt`](mod@fmt) lays out source canonically, for `temper fmt`.
//! - [`nfkc`] checks that identifiers are in Unicode NFKC.
//! - [`id_parts`] holds Unicode's identifier character classes, which
//!   the lexer and library names (Kotlin's `DashedIdentifier`) use.

pub mod adapt;
pub mod ast;
pub mod balance;
pub mod fmt;
mod grammar;
pub mod id_parts;
pub mod lex;
pub mod literate;
pub mod module;
pub mod names;
pub mod nfkc;
mod nfkc_tables;
pub mod operator;
pub mod parse;
pub mod print;
pub mod tree;
pub mod unpack;

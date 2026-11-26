//! Prints the blocks `temper-literate` finds, in the same format as
//! `compat/oracle/MarkdownBlocks.java`, for comparison with Kotlin Temper.

use std::{env, fs, process::ExitCode};

fn main() -> ExitCode {
    let mut ok = true;
    for path in env::args().skip(1) {
        match fs::read_to_string(&path) {
            Ok(text) => {
                println!("# {path}");
                for block in temper_syntax::literate::find_blocks(&text) {
                    let tags = match &block.kind {
                        temper_syntax::literate::Kind::Paragraph => "-".to_string(),
                        temper_syntax::literate::Kind::Code(tags) => tags.join(" "),
                        _ => unreachable!("find_blocks has only paragraphs and code"),
                    };
                    let (b, e) = (&block.begin, &block.end);
                    println!("{tags}\t{}\t{}\t{}\t{}", b.start, b.end, e.start, e.end);
                }
            }
            Err(err) => {
                eprintln!("{path}: {err}");
                ok = false;
            }
        }
    }
    if ok {
        ExitCode::SUCCESS
    } else {
        ExitCode::FAILURE
    }
}

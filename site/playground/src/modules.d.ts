// Modules the bundler supplies.

/** Example sources, as text (esbuild's `text` loader). */
declare module "*.temper" {
  const source: string;
  export default source;
}

/** The JavaScript backend's runtime, from crates/temper-be-js/runtime. */
declare module "@temperlang/core" {
  const exports: Record<string, unknown>;
  export = exports;
}

/** A hash of the build, for `?v=` on the URLs it loads, from build.py. */
declare const BUILD_VERSION: string;

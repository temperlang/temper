// Tests for finding temper (server.ts), under Node's test runner.

import assert from "node:assert/strict";
import { chmodSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import * as path from "node:path";
import { test } from "node:test";
import { findServer, notFound, search } from "./server";

function scratch(): string {
  return mkdtempSync(path.join(tmpdir(), "temper-vscode-"));
}

function touch(file: string): void {
  mkdirSync(path.dirname(file), { recursive: true });
  writeFileSync(file, "");
  chmodSync(file, 0o755);
}

test("the setting comes first, then the bundled binary, then PATH", () => {
  const dir = scratch();
  try {
    const ext = path.join(dir, "ext");
    const bin = path.join(dir, "path-bin");
    touch(path.join(bin, "temper"));
    const env = { PATH: `/nowhere:${bin}` };
    const where = { setting: "", extensionDir: ext, platform: "linux" as const, env };
    assert.deepEqual(findServer(where), { command: path.join(bin, "temper"), from: "PATH" });
    touch(path.join(ext, "bin", "temper"));
    assert.deepEqual(findServer(where), { command: path.join(ext, "bin", "temper"), from: "bundled" });
    touch(path.join(dir, "my", "temper"));
    const mine = path.join(dir, "my", "temper");
    assert.deepEqual(findServer({ ...where, setting: ` ${mine} ` }), { command: mine, from: "setting" });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("Windows names it temper.exe, and spells PATH as Path", () => {
  const dir = scratch();
  try {
    touch(path.join(dir, "temper.exe"));
    const where = { setting: "", extensionDir: path.join(dir, "none"), platform: "win32" as const };
    const found = findServer({ ...where, env: { Path: `C:\\nowhere;${dir}`, PATHEXT: ".COM;.EXE" } });
    assert.deepEqual(found, { command: path.join(dir, "temper.exe"), from: "PATH" });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("nothing found says what was tried and what to do", () => {
  const where = { setting: "", extensionDir: "/nowhere", platform: "linux" as const, env: { PATH: "" } };
  const { found, tried } = search(where);
  assert.equal(found, undefined);
  assert.deepEqual(tried, [`the bundled ${path.join("/nowhere", "bin", "temper")}`, "temper on PATH"]);
  const message = notFound(tried, "linux", "x64");
  assert.match(message, /bin\/temper; temper on PATH/);
  assert.match(message, /linux-x64/);
  assert.match(message, /Temper: Server Path \(temper\.server\.path\)/);
});

test("a setting that isn't there isn't used, nor anything else", () => {
  const dir = scratch();
  try {
    const ext = path.join(dir, "ext");
    touch(path.join(ext, "bin", "temper"));
    const where = { setting: "/no/temper", extensionDir: ext, platform: "linux" as const, env: { PATH: "" } };
    assert.deepEqual(search(where), { tried: ["temper.server.path, /no/temper"] });
    // A bare name is looked for on PATH.
    touch(path.join(dir, "bin", "my-temper"));
    const named = { ...where, setting: "my-temper" };
    assert.deepEqual(search({ ...named, env: { PATH: "" } }), { tried: ["temper.server.path, my-temper, on PATH"] });
    const env = { PATH: path.join(dir, "bin") };
    assert.deepEqual(findServer({ ...named, env }), { command: path.join(dir, "bin", "my-temper"), from: "setting" });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

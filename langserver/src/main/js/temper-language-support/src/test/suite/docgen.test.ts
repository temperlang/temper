import * as assert from "assert";
import { Uri } from "vscode";
import {
  DocGenStorage,
  docGenKeyToUri,
  docGenUriToKey,
  stringifySortedShallow,
} from "../../client/docgen";

suite("Doc Gen Independent Suite", () => {
  const key = { backend: "js", uri: "whatever:/a/b/c" };
  const uri =
    "temper:/a/b/js%20c?%7B%22backend%22%3A%22js%22%2C%22sub%22%3A%22doc%22%2C%22uri%22%3A%22whatever%3A%2Fa%2Fb%2Fc%22%7D";
  test("DocGenStorage", () => {
    const storage = new DocGenStorage();
    assert.strictEqual("", storage.contentForEncoded(Uri.parse(uri)));
    const content = "Hi!";
    storage.track({ key, content });
    assert.strictEqual(content, storage.contentForEncoded(Uri.parse(uri)));
  });
  test("docGenKeyToUri matches docGenUriToKey", () => {
    assert.strictEqual(uri, docGenKeyToUri(key).toString());
    assert.deepStrictEqual(key, docGenUriToKey(Uri.parse(uri)));
  });
  test("stringifySortedShallow", () => {
    const text = stringifySortedShallow({ b: "d", a: "c" });
    assert(text.indexOf("a") < text.indexOf("b"));
  });
});

// TODO(tjp, tooling): suite("Doc Gen VSCode Suite", () => { ...

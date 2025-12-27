import * as assert from "assert";
import {
  escapeHtml,
  loadGrammar,
  tagCode,
  tokenizeCode,
} from "../../client/highlight";

suite("Highlight Independent Suite", () => {
  test("escapeHtml", () => {
    const source = 'a < b && b < c # but c > d and "so on"';
    const expected = 'a &lt; b &amp;&amp; b &lt; c # but c > d and "so on"';
    assert.strictEqual(expected, escapeHtml(source));
  });
  test("tagCode", async () => {
    const grammar = await loadGrammar();
    const code = `
      if (a < 5) {
        print("Hi!");
      }
    `;
    const taggedCode = tagCode({ code, grammar });
    // TODO(tjp, tooling): Coalesce sequential matching classes spans like the string parts here.
    const expected = `
      <span class="hljs-keyword">if</span> (a &lt; <span class="hljs-number">5</span>) {
        <span class="hljs-title">print</span>(<span class="hljs-string">"</span><span class="hljs-string">Hi!</span><span class="hljs-string">"</span>);
      }
    `;
    assert.strictEqual(taggedCode, expected);
  });
  test("tokenizeInterpolatedLambda", async () => {
    const grammar = await loadGrammar();
    // Verify that the outer string ends after fixing a handling of colons
    // inside lambda parameter lists.
    const code = `"\${messages.join("\n") { (it: String);; it }}"`;
    const lines = Array.from(tokenizeCode({ code, grammar }));
    const endScopes = lines.slice(-1)[0].tokens.slice(-1)[0].scopes;
    const expected = [
      "source.temper",
      "string.quoted.double.temper",
      "punctuation.definition.string.end.temper",
    ];
    assert.deepStrictEqual(endScopes, expected);
  });
});

// TODO(tjp, tooling): suite("Highlight VSCode Suite", () => { ...

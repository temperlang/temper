## Regular Expression Support

Temper supports a cross-backend regex dialect designed to run efficiently.
See the "data.temper.md" for supported features.

### Thoughts

Our match macros could provide typed group access such as follows:

```temper
class Pair {
  public first: String
  public second: String
}
let text = "hi-there";
let result = /(?<first>[^-]+)-(?<second>[^-]+)/.match<Pair>(text);
if (result != null) {
  let named = result.named;
  print("First is ${named.first} and second is ${named.second}");
}
```

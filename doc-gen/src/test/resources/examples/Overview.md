# temper-diff-utils

## Intro

Diff Utils library is an OpenSource library for performing the comparison operations between texts: computing diffs, applying patches, generating unified diffs or parsing them, generating diff output for easy future displaying (like side-by-side view) and so on.

**This is originally a port of java-diff-utils.**

## API
<!-- Phrasing this without the word "JavaDocs" would definitely be weird 

[API docs for the release version](#temper-api-docs)

 reads fine. The destination would need to be translated. 
-->
[API docs for the release version](#temper-api-docs)

## Examples

Look [here](Examples.md) to find more helpful informations and examples.

These two outputs are generated using this temper-diff-utils. The source code can also be found at the *Examples* page:

**Producing a one liner including all difference information.**

This is a test ~senctence~**for diffutils**.

**Producing a side by side view of computed differences.**

|original|new|
|--------|---|
|This is a test ~senctence~.|This is a test **for diffutils**.|
|This is the second line.|This is the second line.|
|~And here is the finish.~||

## Main Features

* computing the difference between two texts.
* capable to hand more than plain ascii. Arrays or List of any type that implements hashCode() and equals() correctly can be subject to differencing using this library
* patch and unpatch the text with the given patch
* parsing the unified diff format
* producing human-readable differences
* inline difference construction
* Algorithms:
  * Meyers Standard Algorithm
  * Meyers with linear space improvement
  
### Algorithms

* Meyer's diff
* HistogramDiff

But it can easily replaced by any other which is better for handing your texts. I have plan to add implementation of some in future.

## Source Code convention
 <!-- this block exposes a nuance that I hadn't considered. 
 Docs for contributors must be explicitly different from docs for consumers -->

Recently a linting process was integrated into the build process. temper-diff-utils follows the Mike format convention. 

<!-- I know this is still java but not going to rewrite it as part of this -->
```temper 
public static <T> Patch<T> diff(List<T> original, List<T> revised,
    BiPredicate<T, T> equalizer) throws DiffException {
    if (equalizer != null) {
        return DiffUtils.diff(original, revised,
        new MyersDiff<>(equalizer));
    }
    return DiffUtils.diff(original, revised, new MyersDiff<>());
}
```

This is a valid piece of source code:

* blocks without braces are not allowed
* after control statements (if, while, for) a whitespace is expected
* the opening brace should be in the same line as the control statement

### To Install
<!-- This seems like content that the CCC could handle but it is interesting since this would be a prose block and a code block but would become two of each, which implies a need to create new fragments.
Maybe complete replacement would make more sense-->
> temper-install-instructions
<!-- This represents all the stuff I had been envisioning when thinking of documentation, so fits well into the existing model -->

## Compute the difference between two files and print its deltas ##
```temper
//build simple lists of the lines of the two test files
let original : List<String> = Files.readAllLines(new File(ORIGINAL).toPath());
let revised : List<String> = Files.readAllLines(new File(REVISED).toPath());

//compute the patch: this is the diffutils part
let patch : Patch<String> = DiffUtils.diff(original, revised);

//simple output the computed patch to console
for (AbstractDelta<String> delta : patch.getDeltas()) {
    System.out.println(delta);
}
```

## Get the file in unified format and apply it as the patch to given text ##

```temper
let original : List<String> = Files.readAllLines(new File(ORIGINAL).toPath());
let patched : List<String> = Files.readAllLines(new File(PATCH).toPath());

// At first, parse the unified diff file and get the patch
let patch : List<String> = UnifiedDiffUtils.parseUnifiedDiff(patched);

// Then apply the computed patch to the given text
let result : List<String> = DiffUtils.patch(original, patch);

//simple output to console
System.out.println(result);
```

## Generate a file in unified diff format import it and apply the patch

```temper
let text1 : List<String> =Arrays.asList("this is a test","a test");
let text2 : List<String> =Arrays.asList("this is a testfile","a test");

//generating diff information.
let diff : Patch<String> = DiffUtils.diff(text1, text2);

//generating unified diff format
let unifiedDiff : List<String> = UnifiedDiffUtils.generateUnifiedDiff("original-file.txt", "new-file.txt", text1, diff, 0);

unifiedDiff.forEach(System.out::println);

//importing unified diff format from file or here from memory to a Patch
let importedPatch : Patch<String> = UnifiedDiffUtils.parseUnifiedDiff(unifiedDiff);

//apply patch to original list
let patchedText : List<String>= DiffUtils.patch(text1, importedPatch);

System.out.println(patchedText);
```

## Compute the difference between two texts and print it in human-readable markup style ##

### one liner
The DiffRowGenerator does the main part in producing readable texts. Its instantiated using a builder pattern.
<!-- Unsure if the pattern reference will translate TBD -->

```temper
//create a configured DiffRowGenerator
let generator : DiffRowGenerator = DiffRowGenerator.create()
                .showInlineDiffs(true)
                .mergeOriginalRevised(true)
                .inlineDiffByWord(true)
                .oldTag(f -> "~")      //introduce markdown style for strikethrough
                .newTag(f -> "**")     //introduce markdown style for bold
                .build();

//compute the differences for two test texts.
let rows : List<DiffRow> = generator.generateDiffRows(
                Arrays.asList("This is a test sentence."),
                Arrays.asList("This is a test for diffutils."));

System.out.println(rows.get(0).getOldLine());
```

output is:

This is a test ~sentence~**for diffutils**.


### multi liner

The DiffRowGenerator does the main part in producing readable texts. Its instantiated using a builder pattern.

```temper
let generator : DiffRowGenerator = DiffRowGenerator.create()
                .showInlineDiffs(true)
                .inlineDiffByWord(true)
                .oldTag(f -> "~")
                .newTag(f -> "**")
                .build();
let rows : List<DiffRow> = generator.generateDiffRows(
                Arrays.asList("This is a test sentence.", "This is the second line.", "And here is the finish."),
                Arrays.asList("This is a test for diffutils.", "This is the second line."));

System.out.println("|original|new|");
System.out.println("|--------|---|");
for (DiffRow row : rows) {
    System.out.println("|" + row.getOldLine() + "|" + row.getNewLine() + "|");
}
```

output is:

|original|new|
|--------|---|
|This is a test ~sentence~.|This is a test **for diffutils**.|
|This is the second line.|This is the second line.|
|~And here is the finish.~||


## Compute the difference between two non textual inputs ##
The difference computing part is generified. Therefore a List of objects could be used. Here is an example,
that demonstrates a patch for an Integer List.

```temper
let original : List<Integer> = Arrays.asList(1, 2, 3, 4, 5);
let revised : List<Integer>= Arrays.asList(2, 3, 4, 6);

let patch : Patch<Integer> = DiffUtils.diff(original, revised);

for (Delta delta : patch.getDeltas()) {
    System.out.println(delta);
}
```

output is:

```
[DeleteDelta, position: 0, lines: [1]]
[ChangeDelta, position: 4, lines: [5] to [6]]
```

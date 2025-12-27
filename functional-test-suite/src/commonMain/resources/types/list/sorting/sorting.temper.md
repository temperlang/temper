# Sorting Functional Test

TODO Move all other sort testing from the list operations test to here?

## Stable sorting

Sorting is stable in Temper, which means we don't change the order of items
considered equal by the compare function. So let's make a type to test that.

    class Entry(
      public id: Int,
      public group: Int,
    ) {
      public toString(): String {
        "{ id: ${id}, group: ${group} }"
      }
    }

Now make a list with a bunch of equal groups for sorting. Keep the `id`
incrementing, so we can easily see later if they stayed in order.

For now, this is a manual list, but consider making a pseudorandom list in the
future. But we want enough cases to make accidental stability very unlikely.

    let entries = [
      { id: 0, group: 0 },
      { id: 1, group: 1 },
      { id: 2, group: 2 },
      { id: 3, group: 0 },
      { id: 4, group: 1 },
      { id: 5, group: 2 },
      { id: 6, group: 0 },
      { id: 7, group: 1 },
      { id: 8, group: 2 },
    ].toListBuilder();

Now get them sorted by `group`.

    let sortedEntries = entries.sorted { a, b => a.group - b.group };
    console.log(sortedEntries.join("\n") { a => a.toString() })

```log
{ id: 0, group: 0 }
{ id: 3, group: 0 }
{ id: 6, group: 0 }
{ id: 1, group: 1 }
{ id: 4, group: 1 }
{ id: 7, group: 1 }
{ id: 2, group: 2 }
{ id: 5, group: 2 }
{ id: 8, group: 2 }
```

## Sorted vs sort

Also verify that the above didn't change the original. Just be sloppy for now
and check one entry.

    console.log(entries[1].toString());

```log
{ id: 1, group: 1 }
```

But then also sort the existing `ListBuilder` instance to verify that works too.

    entries.sort { a, b => a.group - b.group };
    console.log(entries.join("\n") { a => a.toString() })

```log
{ id: 0, group: 0 }
{ id: 3, group: 0 }
{ id: 6, group: 0 }
{ id: 1, group: 1 }
{ id: 4, group: 1 }
{ id: 7, group: 1 }
{ id: 2, group: 2 }
{ id: 5, group: 2 }
{ id: 8, group: 2 }
```

## Sorting primitives

Java was having trouble sorting ints, because it makes the callback work on
primitive ints, so test those. In particular,

    let mungeInts(ints: List<Int>): Void {
      let builder = ints.toListBuilder();
      builder.sort { a, b => a - b };
      console.log(ints.sorted { a, b => a - b }.join(", ") { a => "${a}" });
      console.log(builder.join(", ") { a => "${a}" });
    }
    mungeInts([2, 1]);

```log
1, 2
1, 2
```

And might as well try floats also.

    let mungeFloats(floats: List<Float64>): Void {
      let builder = floats.toListBuilder();
      let compareFloats(a: Float64, b: Float64): Int {
        (a - b).sign().toInt32Unsafe()
      }
      builder.sort(compareFloats);
      console.log(floats.sorted(compareFloats).join(", ") { a => "${a}" });
      console.log(builder.join(", ") { a => "${a}" });
    }
    mungeFloats([3.4, 1.2]);

```log
1.2, 3.4
1.2, 3.4
```

Maybe should deal with Boolean someday, too.

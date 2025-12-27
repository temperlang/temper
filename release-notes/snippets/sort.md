### Sorting

Both in-place and non-destructive sorting are now built in. Sorting is also
stable. That is, order for values considered equal by the comparer remain
unchanged. Here is an example:

```temper
class Entry(
  public id: Int,
  public group: Int,
) {
  public toString(): String {
    "{ id: ${id.toString()}, group: ${group.toString()} }"
  }
}

let entries = [
  { id: 0, group: 0 },
  { id: 1, group: 1 },
  { id: 2, group: 2 },
  { id: 3, group: 0 },
  { id: 4, group: 1 },
  { id: 5, group: 2 },
].toListBuilder();

let sortedEntries = entries.sorted { (a, b);; a.group - b.group };
console.log(sortedEntries.join("\n") { (a);; a.toString() })
```

This is guaranteed to log the following message on all backends:

```
{ id: 0, group: 0 }
{ id: 3, group: 0 }
{ id: 1, group: 1 }
{ id: 4, group: 1 }
{ id: 2, group: 2 }
{ id: 5, group: 2 }
```

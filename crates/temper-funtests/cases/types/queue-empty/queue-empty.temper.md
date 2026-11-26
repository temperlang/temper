# Queue Removal When Empty

Removing from an empty *Queue* panics on every backend, as reading a
list out of bounds does, ending the run.

```text
@meta:arg expectRunFailure = true,
```

    let { Queue } = import("std/collections");

    let queue = new Queue<String>();
    queue.add("a");
    console.log(queue.removeFirst());
    console.log("before");
    console.log(queue.removeFirst());
    console.log("not reached");

```log
a
before
```

The run ends in each backend's panic, with this message, not in a host
exception:

```panic
index 0 out of bounds for length 0
```

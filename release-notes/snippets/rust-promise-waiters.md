### Rust resumes every block awaiting a promise

On the Rust backend, a promise used to remember only the last `async` block
that awaited it, so when two blocks awaited the same promise, only one of them
resumed. Now every waiting block resumes, in the order it started waiting.
A `breakPromise` after `complete` no longer replaces the first result.

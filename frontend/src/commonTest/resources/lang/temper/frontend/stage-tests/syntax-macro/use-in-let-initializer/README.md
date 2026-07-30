
In Java and Rust, consider this:

    {
      int i = 0;
      {
        int i = i;
      }
    }

That's legal since, the `i` used in the initializer binds in a scope that excludes the name being
initialized.

    T n = e;
    // following statements in the same block

Java treats every initialization like that the same as:

    T temporary = e;
    {
      T n = temporary;
      // following statements in the same block
    }

JavaScript has a temporal dead zone though.

    {
      let i = 0;
      {
        let i = i;
      }
    }

That is illegal since the `i` in the initializer binds to the uninitialized inner `let`.

The Rust and Kotlin communities' experiences with shadowing starting lexically after
initialization show that this feature is widely appreciated.

## Java config for Temper std

We've chosen to use `temper` as a package prefix for official Temper packages,
but to conform to Maven standards, we use a reverse owned domain name for the
group ID on [Maven][std-on-maven].

    export let java = {
      class: JavaConfig,
      group: "dev.temperlang",
      artifact: "temper-std",
      package: "temper.std",

And as long as the `testing` module is part of `std`, we need junit as a core
dependency of `std`, so its internals can use it. For now, usage is done via
specialized connected methods.

      dependencies: [
        "org.junit.jupiter:junit-jupiter:5.9.2",
      ],
    };

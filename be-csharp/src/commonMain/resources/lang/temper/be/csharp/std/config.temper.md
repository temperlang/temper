## C#

For [NuGet][std-on-nuget], we use TemperLang as a prefix for Temper internal
libraries.

    export let csharp = {
      class: CSharpConfig,
      rootNamespace: "TemperLang.Std",
      dependencies: [
        // We also list these in be-csharp kotlin source for Temper-built tests.
        // Edits here may need done there also!
        "Microsoft.NET.Test.Sdk:17.8.0",
        "MSTest.TestFramework:3.1.1",
      ],
    };

[std-on-nuget]: https://www.nuget.org/packages/TemperLang.Std/

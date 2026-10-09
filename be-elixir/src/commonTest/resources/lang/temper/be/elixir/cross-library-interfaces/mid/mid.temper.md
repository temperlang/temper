# Mid

    let { Named } = import("base");

    export interface Titled extends Named {
      public get title(): String { "Dr. ${name}" }
      public tag(): String { "mid" }
    }

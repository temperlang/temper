# Net Response Functional Test

    let { NetRequest, NetResponse } = import("std/net");

We do a simple request to localhost for reliability, but it would be nice to
regularly test against https and public certs. Meanwhile, `testServerPort` is
injected automatically into functional tests.

    // let url = 'https://httpbin.org/post';
    let url = "http://127.0.0.1:${testServerPort}";

    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      do {
        let req = new NetRequest(url);
        req.post("[]", "application/json")
        let resp: NetResponse = await (req.send());

        if (resp.status == 200) {
          let body: String = (await resp.bodyContent) ?? "missing";
          // Either option above echoes at least some request info.
          let bodyChar0 = String.fromCodePoint(body[String.begin]);
          console.log("Thanks, server.  I got '${bodyChar0}'.");
        } else {
          console.log("HTTP status was not 200: ${resp.status.toString()}!");
        }
      } orelse console.log("failed");
    }

The server should respond with some json object.

```log
Thanks, server.  I got '{'.
```

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
          // The local server echoes the method and whether the request's
          // Content-Type was the one given to `post`.
          console.log("Thanks, server.  I got ${body}.");
        } else {
          console.log("HTTP status was not 200: ${resp.status.toString()}!");
        }
      } orelse console.log("failed");
    }

The server should respond with a json object saying it got a POST
with a JSON body.

```log
Thanks, server.  I got {"method": "POST", "json": true}.
```

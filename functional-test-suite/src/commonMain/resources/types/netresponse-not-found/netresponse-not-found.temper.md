# Net Response Not Found Functional Test

An HTTP error status is still a response. `send` resolves with it, and
`status` says what it was. The test server answers 404 at `/missing`.

    let { NetRequest, NetResponse } = import("std/net");

    let url = "http://127.0.0.1:${testServerPort}/missing";

    async { (): GeneratorResult<Empty> extends GeneratorFn =>
      do {
        let resp: NetResponse = await new NetRequest(url).send();
        console.log("status ${resp.status.toString()}");
        let body: String = (await resp.bodyContent) ?? "missing";
        console.log("body ${body}");
      } orelse console.log("broken promise");

When there is no response at all, because nothing listens on the port,
the promise is broken.

      do {
        await new NetRequest("http://127.0.0.1:1/").send();
        console.log("port 1 answered");
      } orelse console.log("no response from port 1");
    }

```log
status 404
body not here
no response from port 1
```

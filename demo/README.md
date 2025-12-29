# Demo

Some tooling related to ["Hand waving to Awesomeness"][handwaving].

## Getting started with the docgen testbed

There's a Python HTTP server that allows demoing the documentation
generator side-by-side.

```sh
temper $ gradle cli:build
ELIDED
temper $ cd demo/
temper/demo/ $ python3 demo_server.py
Server started http://localhost:8080
```

And then browse to `http://localhost:8080/docgen-testbed.html`.



[handwaving]: https://docs.google.com/presentation/d/1n5hcJqa0EAF16KVmLyOreYJucSES4PdMkPKOLVCWg2A/edit#slide=id.g10d044cc5b4_0_148

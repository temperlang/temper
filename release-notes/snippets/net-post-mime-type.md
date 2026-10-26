### *NetRequest.post* sends its MIME type

`NetRequest.post(content, mimeType)` ignored `mimeType`, so the request body was
sent without the given content type. It's now sent.

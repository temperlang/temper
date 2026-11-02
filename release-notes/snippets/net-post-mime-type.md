### *NetRequest.post* sends its body and MIME type

`NetRequest.post(content, mimeType)` ignored `mimeType`, so the request body was
sent without the given content type. It's now sent, including parameters like
`charset`. On C#, the body wasn't sent at all, and reading
*NetResponse.contentType* always failed. Both now work.

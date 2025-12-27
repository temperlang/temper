# Network Access

*NetRequest* is a builder class for an HTTP send.
None of the methods except *send* actually initiate anything.

    export class NetRequest(

The URL to request.

      private url: String,
    ) {
      private var method: String = "GET";
      private var bodyContent: String? = null;
      private var bodyMimeType: String? = null;

*Post* switches the HTTP method to "POST" and makes sure that
a body with the given textual content and mime-type will be sent
along.

      public post(content: String, mimeType: String): Void {
        this.method = "POST";
        this.bodyContent = content;
        this.bodyMimeType = bodyMimeType;
      }

*Send* makes a best effort to actual send an HTTP method.
Backends may or may not support all request features in which
case, send should return a broken promise.

      public send(): Promise<NetResponse> {
        sendRequest(
          this.url,
          this.method,
          this.bodyContent,
          this.bodyMimeType,
        )
      }
    }

Response bundles together parts of an HTTP response.

    @connected("NetResponse")
    export interface NetResponse {

*status* is an [HTTP Status Code](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status).

      @connected("NetResponse::getStatus")
      get status(): Int;

*contentType* describes the content type of the body if any.

      @connected("NetResponse::getContentType")
      get contentType(): String?;

*bodyContent* is the textual content of the body if it is textual.

      @connected("NetResponse::getBodyContent")
      get bodyContent(): Promise<String?>;
    }

This connected method does the work but is not directly
accessible to Temper code.

    @connected("stdNetSend")
    let sendRequest(
      url: String,
      method: String,
      bodyContent: String?,
      bodyMimeType: String?,
    ): Promise<NetResponse> {
      panic()
    }

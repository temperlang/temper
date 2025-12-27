using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Threading.Tasks;

namespace TemperLang.Std.Net
{
    public interface INetResponse
    {
        public int Status { get; }
        public string? ContentType { get; }
        public Task<string?> BodyContent { get; }
    }

    public static class NetSupport
    {
        private class NetResponseImpl : INetResponse
        {
            public readonly HttpStatusCode StatusCode;
            public readonly IDictionary<string, IEnumerable<string>> Headers;
            public int Status {
                get {
                    return (int) StatusCode;
                }
            }
            public string? ContentType {
                get {
                    return Headers["content-type"].FirstOrDefault();
                }
            }
            public Task<string?> BodyContent { get; }

            public NetResponseImpl(
                HttpStatusCode statusCode,
                IDictionary<string, IEnumerable<string>> headers,
                Task<string?> bodyContent
            )
            {
                this.StatusCode = statusCode;
                this.Headers = headers;
                this.BodyContent = bodyContent;
            }
        }

        private static readonly HttpClient _httpClient = new HttpClient();
        public static async Task<INetResponse> StdNetSend(
            string url, string method,
            string? mimeType, string? requestBody
        )
        {
            HttpMethod httpMethod;
            if (method == "GET")
            {
                httpMethod = HttpMethod.Get;
            }
            else if (method == "POST")
            {
                httpMethod = HttpMethod.Post;
            }
            else
            {
                throw new ArgumentException(method);
            }

            using (var request = new HttpRequestMessage())
            {
                request.RequestUri = new Uri(url);
                request.Method = httpMethod;

                if (requestBody != null)
                {
                    request.Content = new StringContent(requestBody, Encoding.UTF8);
                    if (mimeType != null)
                    {
                        request.Content.Headers.ContentType =
                            new System.Net.Http.Headers.MediaTypeHeaderValue(mimeType);
                    }
                }

                var response = await _httpClient.SendAsync(
                    request,
                    HttpCompletionOption.ResponseHeadersRead
                );

                // Extract status and headers
                var statusCode = response.StatusCode;
                var headers = new Dictionary<string, IEnumerable<string>>();
                foreach (var header in response.Headers)
                {
                    headers[header.Key] = header.Value;
                }

                var bodyTask = LoadBodyAsync(response);
                return new NetResponseImpl(statusCode, headers, bodyTask);
            }
        }

        private static async Task<string?> LoadBodyAsync(HttpResponseMessage response)
        {
            try
            {
                var content = response.Content;
                if (content == null)
                {
                    return null;
                }
                return await content.ReadAsStringAsync();
            }
            finally
            {
                response.Dispose();
            }
        }
    }
}

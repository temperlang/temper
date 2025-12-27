/**
 * @param {string} url
 * @param {"GET" | "POST"} method
 * @param {string | null} bodyContent
 * @param {string | null} bodyMimeType
 * @return {Promise<Response>}
 */
export function stdNetSend(url, method, bodyContent, bodyMimeType) {
  let details = { method };
  if (typeof bodyContent === 'string') {
    details.body = bodyContent;
    if (typeof bodyMimeType === 'string') {
      details.headers = { 'Content-Type': bodyMimeType };
    }
  }
  return fetch(url, details);
}

/**
 * @param {Response} r
 * @return {number} an HTTP status code
 */
export function netResponseGetStatus(r) {
  return r.status;
}

/**
 * @param {Response} r
 * @return {string | null} the mime-type of the body.
 */
export function netResponseGetContentType(r) {
  return r.headers.get('Content-Type') || null;
}

/**
 * @param {Response} r
 * @return {Promise<string | null>} the body content.
 */
export function netResponseGetBodyContent(r) {
  return r.text();
}

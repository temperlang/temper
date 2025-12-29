use super::*;
use std::error::Error;
use std::fmt;
use std::sync::Arc;
use temper_core::{Promise, PromiseBuilder, SafeGenerator, ToArcString};
#[cfg(feature = "net")]
use ureq::http;

#[cfg(not(feature = "net"))]
pub(crate) fn send_request(
    url: impl ToArcString,
    method: impl ToArcString,
    body_content: Option<impl ToArcString>,
    body_mime_type: Option<impl ToArcString>,
) -> Promise<NetResponse> {
    panic!()
}

#[cfg(feature = "net")]
pub(crate) fn send_request(
    url: impl ToArcString,
    method: impl ToArcString,
    body_content: Option<impl ToArcString>,
    body_mime_type: Option<impl ToArcString>,
) -> Promise<NetResponse> {
    let url = url.to_arc_string();
    let method = method.to_arc_string();
    let body_content = body_content.map(|x| x.to_arc_string());
    let body_mime_type = body_mime_type.map(|x| x.to_arc_string());
    let response_future = PromiseBuilder::new();
    let response_promise = response_future.promise();
    crate::run_async(Arc::new(move || {
        let response_future = response_future.clone();
        let url = url.clone();
        let method = method.clone();
        let body_content = body_content.clone();
        let body_mime_type = body_mime_type.clone();
        SafeGenerator::from_fn(Arc::new(move |generator: SafeGenerator<()>| {
            let body_future = PromiseBuilder::<Option<Arc<String>>>::new();
            let response = make_request(
                &url,
                &method,
                body_content.as_ref().map(|x| x.as_str()),
                body_mime_type.as_ref().map(|x| x.as_str()),
            );
            let response = match response {
                Ok(response) => {
                    body_future.complete(response.body_content);
                    response_future.complete(NetResponse::new(SimpleNetResponse(Arc::new(
                        SimpleNetResponseStruct {
                            status: response.status,
                            content_type: response.content_type,
                            body_content: body_future.promise(),
                        },
                    ))));
                }
                Err(error) => {
                    response_future.break_promise();
                }
            };
            None
        }))
    }));
    return response_promise;
}

#[cfg(feature = "net")]
fn make_request(
    url: &str,
    method: &str,
    body_content: Option<&str>,
    body_mime_type: Option<&str>,
) -> Result<SimplerNetResponse, ureq::Error> {
    let request = http::Request::builder().uri(url).method(method);
    let request = match body_mime_type {
        Some(body_mime_type) => request.header("Content-Type", body_mime_type),
        _ => request,
    };
    let mut response = match body_content {
        Some(body_content) => ureq::run(request.body(body_content)?),
        None => ureq::run(request.body(())?),
    }?;
    let mut body = response.body_mut();
    let content_type = match response.headers().get(http::header::CONTENT_TYPE) {
        Some(value) => Some(
            value
                .to_str()
                .map_err(|e| ureq::Error::Other(Box::new(e)))?
                .to_arc_string(),
        ),
        None => None,
    };
    Ok(SimplerNetResponse {
        status: response.status().as_u16() as i32,
        content_type,
        body_content: response
            .body_mut()
            .read_to_string()
            .ok()
            .map(|x| x.to_arc_string()),
    })
}

struct SimplerNetResponse {
    status: i32,
    content_type: Option<Arc<String>>,
    body_content: Option<Arc<String>>,
}

struct SimpleNetResponseStruct {
    status: i32,
    content_type: Option<Arc<String>>,
    body_content: Promise<Option<Arc<String>>>,
}

#[derive(Clone)]
struct SimpleNetResponse(Arc<SimpleNetResponseStruct>);

impl NetResponseTrait for SimpleNetResponse {
    fn clone_boxed(&self) -> NetResponse {
        NetResponse::new(self.clone())
    }

    fn status(&self) -> i32 {
        self.0.status
    }

    fn content_type(&self) -> Option<Arc<String>> {
        self.0.content_type.clone()
    }

    fn body_content(&self) -> Promise<Option<Arc<String>>> {
        self.0.body_content.clone()
    }
}

temper_core::impl_any_value_trait!(SimpleNetResponse, [NetResponse]);

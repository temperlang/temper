#pragma once
// std/net's requests go through a client, temper::core::net::Client. The
// default is libcurl's, where the build defines TEMPER_NET_CURL (the
// generated CMake does where it finds libcurl), the same in every file;
// without it, requests give broken promises and say how to plug in a
// client. Host code plugs in its own once, at the edge, before sending:
// temper::core::net::set_client(std::make_shared<MyClient>()).
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <functional>
#include <memory>
#include <string>
#include <utility>
#include "nullable_param.hpp"
#include "promise.hpp"
#include "str.hpp"
#ifdef TEMPER_NET_CURL
#ifndef CURL_DISABLE_TYPECHECK
#define CURL_DISABLE_TYPECHECK
#endif
#include <curl/curl.h>
#include <deque>
#include <map>
#include <thread>
#endif

namespace temper {
    namespace core {
        namespace net {

            // A client's requests and responses hold std::strings, not
            // Temper's shared Str, as host code and other threads use them.
            struct Request {
                std::string url;
                std::string method;
                NullableParam<std::string> bodyContent;
                NullableParam<std::string> bodyMimeType;
            };

            struct Response {
                int32_t status = 0;
                NullableParam<std::string> contentType;
                NullableParam<std::string> bodyContent;
            };

            // Called once, from any thread: with the response, or null if
            // no response came (an HTTP error status is still a response).
            using Respond = std::function<void(std::shared_ptr<Response const>)>;

            // Sends a request without blocking the caller, which runs a
            // Temper turn, and later responds.
            struct Client {
                virtual ~Client() = default;
                virtual void send(Request request, Respond respond) = 0;
            };

            inline std::shared_ptr<Client>& client_slot() {
                static std::shared_ptr<Client> slot;
                return slot;
            }

            // The client std/net sends through from now on; null restores
            // the default. Call it on the thread that runs Temper code.
            inline void set_client(std::shared_ptr<Client> client) {
                client_slot() = std::move(client);
            }

#ifdef TEMPER_NET_CURL
            // libcurl's multi interface on one thread of its own, the
            // temper-io thread, started by the first request, which runs
            // every transfer and never a Temper turn. Needs libcurl 7.68.
            class CurlClient final : public Client {
              public:
                void send(Request request, Respond respond) override {
                    std::unique_ptr<Transfer> transfer(new Transfer());
                    transfer->request = std::move(request);
                    transfer->respond = std::move(respond);
                    std::lock_guard<std::mutex> lock(mutex_);
                    if (!started_) {
                        started_ = true;
                        if (curl_global_init(CURL_GLOBAL_DEFAULT) == CURLE_OK) {
                            multi_ = curl_multi_init();
                        }
                        if (multi_ != nullptr) {
                            std::thread(&CurlClient::loop, this).detach();
                        }
                    }
                    if (multi_ == nullptr) {
                        transfer->respond(nullptr);
                        return;
                    }
                    incoming_.push_back(std::move(transfer));
                    curl_multi_wakeup(multi_);
                }

              private:
                struct Transfer {
                    Request request;
                    Respond respond;
                    std::string body;
                    curl_slist* headers = nullptr;
                };

                static size_t write_body(char* data, size_t size, size_t count, void* user) {
                    static_cast<Transfer*>(user)->body.append(data, size * count);
                    return size * count;
                }

                void start(std::unique_ptr<Transfer> transfer) {
                    CURL* easy = curl_easy_init();
                    if (easy == nullptr) {
                        transfer->respond(nullptr);
                        return;
                    }
                    Request const& request = transfer->request;
                    curl_easy_setopt(easy, CURLOPT_URL, request.url.c_str());
                    curl_easy_setopt(easy, CURLOPT_NOSIGNAL, 1L);
                    curl_easy_setopt(easy, CURLOPT_WRITEFUNCTION, &CurlClient::write_body);
                    curl_easy_setopt(easy, CURLOPT_WRITEDATA, static_cast<void*>(transfer.get()));
                    if (request.method == "GET") {
                        curl_easy_setopt(easy, CURLOPT_HTTPGET, 1L);
                    } else if (request.method == "POST") {
                        curl_easy_setopt(easy, CURLOPT_POST, 1L);
                    } else {
                        curl_easy_setopt(easy, CURLOPT_CUSTOMREQUEST, request.method.c_str());
                    }
                    if (request.bodyContent.has_value || request.method == "POST") {
                        std::string const& body = request.bodyContent.value;
                        curl_easy_setopt(easy, CURLOPT_POSTFIELDSIZE_LARGE, static_cast<curl_off_t>(body.size()));
                        curl_easy_setopt(easy, CURLOPT_COPYPOSTFIELDS, body.c_str());
                    }
                    if (request.bodyMimeType.has_value) {
                        std::string header = "Content-Type: " + request.bodyMimeType.value;
                        transfer->headers = curl_slist_append(nullptr, header.c_str());
                        curl_easy_setopt(easy, CURLOPT_HTTPHEADER, transfer->headers);
                    }
                    if (curl_multi_add_handle(multi_, easy) != CURLM_OK) {
                        finish(easy, std::move(transfer), CURLE_FAILED_INIT);
                        return;
                    }
                    active_[easy] = std::move(transfer);
                }

                void finish(CURL* easy, std::unique_ptr<Transfer> transfer, CURLcode result) {
                    std::shared_ptr<Response> response;
                    if (result == CURLE_OK) {
                        long status = 0;
                        char* type = nullptr;
                        curl_easy_getinfo(easy, CURLINFO_RESPONSE_CODE, &status);
                        curl_easy_getinfo(easy, CURLINFO_CONTENT_TYPE, &type);
                        response = std::make_shared<Response>();
                        response->status = static_cast<int32_t>(status);
                        if (type != nullptr) {
                            response->contentType = std::string(type);
                        }
                        response->bodyContent = std::move(transfer->body);
                    }
                    curl_slist_free_all(transfer->headers);
                    curl_easy_cleanup(easy);
                    transfer->respond(response);
                }

                void loop() {
                    for (;;) {
                        std::deque<std::unique_ptr<Transfer>> fresh;
                        {
                            std::lock_guard<std::mutex> lock(mutex_);
                            fresh.swap(incoming_);
                        }
                        for (std::unique_ptr<Transfer>& transfer : fresh) {
                            start(std::move(transfer));
                        }
                        int running = 0;
                        curl_multi_perform(multi_, &running);
                        int left = 0;
                        while (CURLMsg* message = curl_multi_info_read(multi_, &left)) {
                            if (message->msg != CURLMSG_DONE) {
                                continue;
                            }
                            CURL* easy = message->easy_handle;
                            CURLcode result = message->data.result;
                            curl_multi_remove_handle(multi_, easy);
                            auto found = active_.find(easy);
                            if (found != active_.end()) {
                                std::unique_ptr<Transfer> transfer = std::move(found->second);
                                active_.erase(found);
                                finish(easy, std::move(transfer), result);
                            }
                        }
                        curl_multi_poll(multi_, nullptr, 0, 1000, nullptr);
                    }
                }

                std::mutex mutex_;
                bool started_ = false;
                CURLM* multi_ = nullptr;
                std::deque<std::unique_ptr<Transfer>> incoming_;
                std::map<CURL*, std::unique_ptr<Transfer>> active_;
            };
#endif

            // The client set, or else the default, or null if there's none.
            inline std::shared_ptr<Client> current_client() {
                std::shared_ptr<Client> client = client_slot();
                if (client) {
                    return client;
                }
#ifdef TEMPER_NET_CURL
                // Never destroyed, as its thread runs until the program
                // exits.
                static std::shared_ptr<Client>* fallback = new std::shared_ptr<Client>(std::make_shared<CurlClient>());
                return *fallback;
#else
                return nullptr;
#endif
            }

            // A Temper String? as the host's std::string, or back.
            inline NullableParam<std::string> text_of(NullableParam<Str> const& text) {
                return text.has_value ? NullableParam<std::string>(text.value.str()) : NullableParam<std::string>();
            }
            inline NullableParam<Str> text_of(NullableParam<std::string> const& text) {
                return text.has_value ? NullableParam<Str>(Str(text.value)) : NullableParam<Str>();
            }

            // std/net's NetResponse (R) over a response.
            template<class R>
            struct ResponseOf final : public R {
                std::shared_ptr<Response const> response;

                explicit ResponseOf(std::shared_ptr<Response const> response) : response(std::move(response)) {}

                int32_t get_status() const override {
                    return response->status;
                }

                NullableParam<Str> get_contentType() const override {
                    return text_of(response->contentType);
                }

                std::shared_ptr<Promise<NullableParam<Str>>> get_bodyContent() override {
                    auto builder = PromiseBuilderNs::make<NullableParam<Str>>();
                    complete(builder, text_of(response->bodyContent));
                    return getpromise(builder);
                }
            };

            // std/net's sendRequest, its promise of an R: the client's
            // response, settled on the thread that runs Temper code, which
            // waits for it when nothing else is ready (async_hold).
            template<class R>
            std::shared_ptr<Promise<std::shared_ptr<R>>> sendRequest(
                Str const& url,
                Str const& method,
                NullableParam<Str> const& bodyContent,
                NullableParam<Str> const& bodyMimeType
            ) {
                auto builder = PromiseBuilderNs::make<std::shared_ptr<R>>();
                std::shared_ptr<Client> client = current_client();
                if (!client) {
                    static std::atomic<bool> told(false);
                    if (!told.exchange(true)) {
                        std::fputs(
                            "std/net: no client: build with libcurl (TEMPER_NET_CURL), "
                            "or call temper::core::net::set_client\n",
                            stderr
                        );
                    }
                    breakpromise(builder);
                    return getpromise(builder);
                }
                async_hold();
                auto once = std::make_shared<std::atomic<bool>>(false);
                Request request{url.str(), method.str(), text_of(bodyContent), text_of(bodyMimeType)};
                client->send(std::move(request), [builder, once](std::shared_ptr<Response const> response) {
                    if (once->exchange(true)) {
                        return;
                    }
                    async_post([builder, response]() {
                        if (response) {
                            complete(builder, std::shared_ptr<R>(std::make_shared<ResponseOf<R>>(response)));
                        } else {
                            breakpromise(builder);
                        }
                    });
                });
                return getpromise(builder);
            }

        }
    }
}

window.addEventListener(
    'load',
    () => {
        let $ = (selector) => document.querySelector(selector);
        let textarea = $('textarea#docs-temper');
        let jsDiv = $('#docs-js');

        showdown.extension(
            'highlight',
            {
                type: "output",
                filter: function (text, converter, options) {
                    var left = "<pre><code\\b[^>]*>",
                        right = "</code></pre>",
                        flags = "g";
                    var replacement = function (wholeMatch, match, left, right) {
      	                var lang = (left.match(/class=\"([^ \"]+)/) || [])[1];
                        left = left.slice(0, 18) + 'hljs ' + left.slice(18);
                        if (lang && hljs.getLanguage(lang)) {
                            return left + hljs.highlight(lang, match).value + right;
                        } else {
                            return left + hljs.highlightAuto(match).value + right;
                        }
                    };
                    return showdown.helper
                        .replaceRecursiveRegExp(text, replacement, left, right, flags);
                }
            }
        );

        let converter = new showdown.Converter({ extensions: ['highlight'] });
        converter.setOption('smoothLivePreview', 'true');
        converter.setFlavor('github');

        let changeCount = 0;
        let changeEmitted = -1;
        let pendingTimeout = null;
        function onTextChanged(nodelay) {
            if (pendingTimeout === null || nodelay) {
                // Rate limit how many requests we send to server
                pendingTimeout = setTimeout(
                    () => {
                        pendingTimeout = null;
                        handleTextChanged()
                    },
                    250 /* ms */
                );
            }
        }

        function handleTextChanged() {
            let text = textarea.value;
            let changeIndex = changeCount++;
            let promise = fetch(
                '/docgen',
                {
                    method: 'POST',
                    headers: {
                        'Content-type': 'text/markdown',
                    },
                    body: text
                }
            );
            (async () => {
                let response = { ok: false };
                try {
                    response = await promise;
                } catch (_) {
                    console.group('Failed to handle text');
                    console.log(text);
                    console.groupEnd();
                }
                if (response.ok && changeIndex > changeEmitted) {
                    let bodyText = await response.text();
                    if (
                        changeIndex > changeEmitted && // Is latest
                            !/\bthrow \"/.test(bodyText) // Does not have an error node
                    ) {

                        jsDiv.innerHTML = converter.makeHtml(bodyText);
                        changeEmitted = changeIndex;
                    }
                }
            })();
        }

        textarea.addEventListener('input', onTextChanged);

        setTimeout(() => { onTextChanged(true) });
        setTimeout(() => { textarea.select(); textarea.focus() });
    }
);

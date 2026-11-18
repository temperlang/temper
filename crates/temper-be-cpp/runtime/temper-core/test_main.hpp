#pragma once
#include <fstream>
#include <functional>
#include <sstream>
#include <string>
#include <vector>

// Test harness for the generated `main`. The generated entry point stays thin: it
// initializes modules and hands a list of named test closures to `run_tests`, which
// owns the (fiddly, easy-to-get-wrong) JUnit-XML serialization and file writing. This
// mirrors the other backends, whose generated `main` delegates the harness to their
// runtime rather than emitting an XML writer inline.

namespace temper {
    namespace core {

        // Outcome of running one test: whether it passed and any accumulated messages.
        struct TestOutcome {
            bool passed;
            std::string messages;
        };

        // A named test. `run` executes it and returns its outcome; any uncaught
        // exception it throws is turned into a failure carrying the exception text.
        struct TestEntry {
            std::string name;
            std::function<TestOutcome()> run;
        };

        inline std::string xml_escape(const std::string& s) {
            std::string out;
            for (char c : s) {
                switch (c) {
                    case '&': out += "&amp;"; break;
                    case '<': out += "&lt;"; break;
                    case '>': out += "&gt;"; break;
                    case '"': out += "&quot;"; break;
                    default: out += c;
                }
            }
            return out;
        }

        // Runs each test, capturing uncaught exceptions, writes a JUnit XML report to
        // `test-results.xml`, and returns 0.
        inline int run_tests(const std::vector<TestEntry>& tests) {
            struct Result {
                std::string name;
                bool passed;
                std::string messages;
            };
            std::vector<Result> results;
            results.reserve(tests.size());
            for (const TestEntry& test : tests) {
                bool threw = false;
                std::string thrown;
                TestOutcome outcome{false, std::string()};
                // An uncaught exception means the generated code is broken: record it as a
                // failure with its message rather than letting a crashing test masquerade
                // as whatever the outcome happened to be.
                try {
                    outcome = test.run();
                } catch (const std::exception& e) {
                    threw = true;
                    thrown = e.what();
                } catch (...) {
                    threw = true;
                    thrown = "unknown C++ exception";
                }
                bool passed = !threw && outcome.passed;
                std::string messages = outcome.messages;
                if (threw) {
                    if (!messages.empty()) {
                        messages += "\n";
                    }
                    messages += "uncaught exception: " + thrown;
                }
                results.push_back({test.name, passed, messages});
            }

            int failures = 0;
            for (const Result& r : results) {
                if (!r.passed) {
                    failures++;
                }
            }

            std::ostringstream xml;
            xml << "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                << "<testsuites>\n"
                << "  <testsuite name=\"suite\" tests=\"" << results.size()
                << "\" failures=\"" << failures << "\" time=\"0\">\n";
            for (const Result& r : results) {
                xml << "    <testcase name=\"" << xml_escape(r.name)
                    << "\" classname=\"" << xml_escape(r.name) << "\" time=\"0\">\n";
                if (!r.passed) {
                    xml << "      <failure message=\"" << xml_escape(r.messages)
                        << "\"><![CDATA[" << r.messages << "]]></failure>\n";
                }
                xml << "    </testcase>\n";
            }
            xml << "  </testsuite>\n"
                << "</testsuites>\n";

            std::ofstream out("test-results.xml");
            if (out.is_open()) {
                out << xml.str();
                out.close();
            }
            return 0;
        }

    }
}

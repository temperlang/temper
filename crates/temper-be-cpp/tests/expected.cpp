// The runtime's `Expected` (`expected.hpp`) and `Failure` (`failure.hpp`),
// for `functional.rs`'s `expected_shim`: built as C++14, where it's the
// shim, and as C++23, where it's `std::expected`, and the shim again
// (`TEMPER_EXPECTED_SHIM`), each printing the same lines, so the shim
// behaves as std's does for what it has. Under the sanitizers, a count of
// live values checks that every one made is destroyed once.

#include <temper-core/core.hpp>

#include <iostream>
#include <memory>
#include <string>
#include <utility>

namespace core = temper::core;

namespace {

    // Counts its live copies, so a value held, moved, or assigned is
    // destroyed exactly once.
    struct Counted {
        static int live;
        int n;

        explicit Counted(int value) : n(value) {
            live++;
        }

        Counted(const Counted& other) : n(other.n) {
            live++;
        }

        Counted(Counted&& other) noexcept : n(other.n) {
            live++;
        }

        Counted& operator=(const Counted&) = default;
        Counted& operator=(Counted&&) = default;

        ~Counted() {
            live--;
        }
    };

    int Counted::live = 0;

    struct Base : public virtual core::Failure {
        core::Str get_message() const override {
            return core::Str("base");
        }
    };

    struct Missing : public virtual Base {
        std::string key;

        explicit Missing(std::string k) : key(std::move(k)) {}

        core::Str get_message() const override {
            return core::Str("missing (key: " + key + ")");
        }
    };

    core::Expected<int, std::shared_ptr<Missing>> find(const std::string& key) {
        if (key == "a") {
            return 1;
        }
        return core::unexpected(std::make_shared<Missing>(key));
    }

    // A subtype's failure into a supertype's result, as a `throw` does.
    core::Expected<long, std::shared_ptr<Base>> wider(const std::string& key) {
        core::Expected<int, std::shared_ptr<Missing>> found = find(key);
        if (!found.has_value()) {
            return core::unexpected(found.error());
        }
        return *found;
    }

    core::Expected<void, std::shared_ptr<Missing>> check(bool fine) {
        if (fine) {
            return {};
        }
        return core::unexpected(std::make_shared<Missing>("check"));
    }

    core::Expected<Counted, std::shared_ptr<Missing>> counted(int n) {
        if (n < 0) {
            return core::unexpected(std::make_shared<Missing>("negative"));
        }
        return Counted(n);
    }

    void values() {
        core::Expected<int, std::shared_ptr<Missing>> one = find("a");
        core::Expected<int, std::shared_ptr<Missing>> none = find("z");
        std::cout << "value " << one.has_value() << " " << static_cast<bool>(one) << " " << *one
                  << " " << one.value() << " " << one.value_or(7) << "\n";
        std::cout << "error " << none.has_value() << " " << none.error()->key << " "
                  << none.value_or(7) << "\n";
        try {
            static_cast<void>(none.value());
            std::cout << "value() gave a value\n";
        } catch (const core::BadExpectedAccess<std::shared_ptr<Missing>>& e) {
            std::cout << "value() threw for " << e.error()->key << "\n";
        }
        core::Expected<long, std::shared_ptr<Base>> w = wider("q");
        std::cout << "wider " << w.has_value() << " " << w.error()->get_message().str() << " "
                  << *wider("a") << "\n";
        core::Expected<std::string, int> text = std::string("hi");
        std::cout << "arrow " << text->size() << " " << std::move(text).value() << "\n";
    }

    void voids() {
        core::Expected<void, std::shared_ptr<Missing>> fine = check(true);
        core::Expected<void, std::shared_ptr<Missing>> failed = check(false);
        std::cout << "void " << fine.has_value() << " " << failed.has_value() << " "
                  << failed.error()->key << "\n";
        fine.value();
        *fine;
        fine = failed;
        std::cout << "void assigned " << fine.has_value() << "\n";
        try {
            fine.value();
        } catch (const core::BadExpectedAccess<std::shared_ptr<Missing>>& e) {
            std::cout << "void value() threw for " << e.error()->key << "\n";
        }
    }

    void lifetimes() {
        {
            core::Expected<Counted, std::shared_ptr<Missing>> a = counted(1);
            core::Expected<Counted, std::shared_ptr<Missing>> b = counted(-1);
            core::Expected<Counted, std::shared_ptr<Missing>> c = a;
            std::cout << "live " << Counted::live << "\n";
            c = b;
            std::cout << "assigned an error " << Counted::live << " " << c.has_value() << "\n";
            c = a;
            std::cout << "assigned a value " << Counted::live << " " << c->n << "\n";
            core::Expected<Counted, std::shared_ptr<Missing>> d = std::move(c);
            b = std::move(d);
            std::cout << "moved " << b->n << " " << (*b).n << "\n";
            core::Expected<Counted, std::shared_ptr<Missing>>& same = b;
            b = same;
            std::cout << "self " << b->n << "\n";
        }
        std::cout << "all gone " << Counted::live << "\n";
    }

    void failures() {
        std::shared_ptr<Missing> first = std::make_shared<Missing>("first");
        std::shared_ptr<Missing> second = std::make_shared<Missing>("second");
        std::shared_ptr<Missing> third = std::make_shared<Missing>("third");
        const std::exception& as_std = *first;
        std::cout << "what " << as_std.what() << "\n";
        std::shared_ptr<Missing> made = core::with_cause(second, first);
        std::cout << "cause " << (made == second) << " " << second->get_cause()->what() << "\n";
        // Set once: a failure thrown again keeps its first cause.
        core::with_cause(second, third);
        std::cout << "kept " << second->get_cause()->what() << "\n";
        // Never itself, nor a loop.
        core::with_cause(first, first);
        core::with_cause(first, second);
        std::cout << "no loop " << (first->get_cause() == nullptr) << "\n";
        core::with_cause(third, second);
        std::cout << "report " << core::failure_report(*third) << "\n";
        try {
            core::uncaught_failure(third);
        } catch (const core::UncaughtFailure& e) {
            std::cout << "uncaught " << e.what() << " " << (e.failure == third) << "\n";
        }
        std::shared_ptr<core::Failure> root = third;
        std::shared_ptr<core::AnyValueBase> any = root;
        std::cout << "cast " << (std::dynamic_pointer_cast<Missing>(any) == third) << "\n";
    }

}

int main() {
    values();
    voids();
    lifetimes();
    failures();
}

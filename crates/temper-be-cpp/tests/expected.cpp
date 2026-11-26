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


    // Core's members' `try` forms, with callbacks that return an
    // `Expected` (RFC 164's failure parameters): what the plain forms
    // give where nothing fails, else the first failure, with no call
    // after it; and `Never`, "can't fail".
    void try_forms() {
        using Ints = std::shared_ptr<std::vector<int>>;
        using Result = core::Expected<int, std::shared_ptr<Missing>>;
        Ints xs = std::make_shared<std::vector<int>>(std::vector<int>{3, 1, 2});
        int calls = 0;
        // `n`, unless it's 0, counting each call.
        auto check = [&calls](int n) -> Result {
            calls++;
            if (n == 0) {
                return core::unexpected(std::make_shared<Missing>("0"));
            }
            return n;
        };
        auto twice = core::List::tryMap(xs, [&](int n) { return check(n * 2); });
        auto kept = core::List::tryFilter(xs, [&](int n) -> core::Expected<bool, std::shared_ptr<Missing>> {
            return n > 1;
        });
        auto joined = core::List::tryJoin(xs, core::Str("+"), [&](int n) -> core::Expected<core::Str, std::shared_ptr<Missing>> {
            return core::Str(std::to_string(n));
        });
        auto sum = core::List::tryReduce(xs, [&](int a, int b) { return check(a + b); });
        auto from = core::List::tryReduceFrom(xs, 10, [&](int a, int b) { return check(a + b); });
        auto sorted = core::List::trySorted(xs, [&](int a, int b) { return check(a - b == 0 ? 1 : a - b); });
        std::cout << "try " << (*twice)->at(0) << (*kept)->size() << " " << joined->str() << " " << *sum
                  << " " << *from << " " << (*sorted)->at(0) << (*sorted)->at(2) << "\n";
        Ints zeros = std::make_shared<std::vector<int>>(std::vector<int>{3, 0, 2, 0});
        calls = 0;
        auto failed = core::List::tryMap(zeros, check);
        std::cout << "try failed " << failed.error()->key << " " << calls << "\n";
        auto unsorted = core::List::trySorted(zeros, [&](int a, int b) { return check(a * b); });
        Ints builder = std::make_shared<std::vector<int>>(*zeros);
        auto left = core::ListBuilder::trySort(builder, [&](int a, int b) { return check(a * b); });
        std::cout << "try sort " << unsorted.has_value() << left.has_value() << " " << builder->at(1) << "\n";
        auto map = std::make_shared<core::Mapped::Ordered<int, int>>();
        map->data = {{1, 10}, {2, 20}};
        map->order = {1, 2};
        int total = 0;
        auto each = core::Mapped::tryForEach(map, [&](int k, int v) -> core::Expected<void, std::shared_ptr<Missing>> {
            total += k * v;
            return {};
        });
        auto pairs = core::Mapped::tryToListWith(map, [&](int k, int v) { return check(k + v); });
        std::cout << "try map " << each.has_value() << " " << total << " " << (*pairs)->at(1) << "\n";
        auto never = core::List::tryMap(xs, [](int n) -> core::Expected<int, core::Never> { return n + 1; });
        std::cout << "never " << (*never)->at(0) << "\n";
    }
}

int main() {
    values();
    voids();
    lifetimes();
    failures();
    try_forms();
}

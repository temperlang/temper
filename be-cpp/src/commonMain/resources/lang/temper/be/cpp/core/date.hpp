#pragma once
#include <cstdint>
#include <ctime>
#include <memory>
#include <sstream>
#include <string>
#include <type_traits>
#include "temper_bubble.hpp"
#include "base_types.hpp"

namespace temper {
    namespace core {

        namespace Date {

            template<class D>
            int32_t getYear(const std::shared_ptr<D>& d) {
                return d->_year;
            }

            template<class D>
            int32_t getMonth(const std::shared_ptr<D>& d) {
                return d->_month;
            }

            template<class D>
            int32_t getDay(const std::shared_ptr<D>& d) {
                return d->_day;
            }

            template<class D>
            int32_t getDayOfWeek(const std::shared_ptr<D>& d) {
                static int t[] = {0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4};
                int64_t y = d->_year;
                int64_t m = d->_month;
                if (m < 1 || m > 12) {
                    bubble<int32_t>("month out of range");
                }
                if (m < 3) {
                    y = y - 1;
                }
                int64_t dow = (y + y/4 - y/100 + y/400 + t[m-1] + d->_day) % 7;
                if (dow < 0) {
                    dow = dow + 7;
                }
                return static_cast<int32_t>(dow);
            }

            template<class D>
            std::string toString(
            const std::shared_ptr<D>& d,
            typename std::enable_if<
            std::is_same<decltype(std::declval<D>()._year), int32_t>::value
            && std::is_same<decltype(std::declval<D>()._month), int32_t>::value
            && std::is_same<decltype(std::declval<D>()._day), int32_t>::value
            >::type* = nullptr
            ) {
                std::ostringstream oss;
                int32_t y = d->_year;
                if (y < 0) {
                    oss << '-'; y = -y;
                }
                if (y < 10) {
                    oss << "000";
                } else if (y < 100) {
                    oss << "00";
                } else if (y < 1000) {
                    oss << "0";
                }
                oss << y << '-';
                if (d->_month < 10) {
                    oss << '0';
                }
                oss << d->_month << '-';
                if (d->_day < 10) {
                    oss << '0';
                }
                oss << d->_day;
                return oss.str();
            }

            template<class D>
            int32_t yearsBetween(const std::shared_ptr<D>& from, const std::shared_ptr<D>& to) {
                int32_t years = to->_year - from->_year;
                if (to->_month < from->_month
                || (to->_month == from->_month && to->_day < from->_day)) {
                    years = years - 1;
                }
                return years;
            }

            template<class D>
            std::shared_ptr<D> makeDate(int32_t year, int32_t month, int32_t day) {
                std::shared_ptr<D> d = std::make_shared<D>();
                d->_year = year;
                d->_month = month;
                d->_day = day;
                return d;
            }

            template<class D>
            std::shared_ptr<D> fromIso(std::string isoString) {
                size_t pos = 0;
                bool is_negative = false;
                if (!isoString.empty() && isoString[0] == '-') {
                    is_negative = true;
                    pos = 1;
                }
                size_t dash1 = isoString.find('-', pos);
                if (dash1 == std::string::npos) {
                    bubble<std::shared_ptr<D>>("invalid ISO date string");
                }
                size_t dash2 = isoString.find('-', dash1 + 1);
                if (dash2 == std::string::npos) {
                    bubble<std::shared_ptr<D>>("invalid ISO date string");
                }
                int32_t y = 0, m = 0, d = 0;
                try {
                    y = std::stoi(isoString.substr(pos, dash1 - pos));
                    if (is_negative) {
                        y = -y;
                    }
                    m = std::stoi(isoString.substr(dash1 + 1, dash2 - dash1 - 1));
                    d = std::stoi(isoString.substr(dash2 + 1));
                } catch (const std::exception&) {
                    bubble<std::shared_ptr<D>>("invalid ISO date string");
                }
                return makeDate<D>(y, m, d);
            }

            template<class D>
            std::shared_ptr<D> today() {
                std::time_t now = std::time(nullptr);
                std::tm tm;
#if defined(_WIN32) || defined(_WIN64)
                localtime_s(&tm, &now);
#else
                localtime_r(&now, &tm);
#endif
                return makeDate<D>(tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday);
            }

        }

    }
}

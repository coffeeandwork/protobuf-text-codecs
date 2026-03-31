#pragma once

#include <cmath>
#include <cstdint>
#include <jsonarray/codec.hpp>
#include <nlohmann/json.hpp>
#include <string>

namespace example {

class Address {
    private:
        std::string street_ = "";
        std::string city_ = "";
        std::string state_ = "";
        int32_t zip_ = 0;

    public:
        Address() = default;

        Address(const Address&) = default;
        Address(Address&&) = default;
        Address& operator=(const Address&) = default;
        Address& operator=(Address&&) = default;

        const std::string& street() const { return street_; }
        void set_street(const std::string& value) { street_ = value; }
        void set_street(std::string&& value) { street_ = std::move(value); }

        const std::string& city() const { return city_; }
        void set_city(const std::string& value) { city_ = value; }
        void set_city(std::string&& value) { city_ = std::move(value); }

        const std::string& state() const { return state_; }
        void set_state(const std::string& value) { state_ = value; }
        void set_state(std::string&& value) { state_ = std::move(value); }

        int32_t zip() const { return zip_; }
        void set_zip(int32_t value) { zip_ = value; }

        nlohmann::json serialize() const;
        static Address deserialize(const nlohmann::json& arr);

        std::string to_json_string() const;
        static Address from_json_string(const std::string& json);
};

inline nlohmann::json Address::serialize() const {
    nlohmann::json arr = nlohmann::json::array();
    arr.push_back(street_);
    arr.push_back(city_);
    arr.push_back(state_);
    arr.push_back(zip_);
    return arr;
}

inline std::string Address::to_json_string() const {
    return serialize().dump();
}

inline Address Address::deserialize(const nlohmann::json& arr) {
    Address obj;
    const auto size = arr.size();
    if (size > 0 && !arr[0].is_null()) {
        obj.set_street(arr[0].get<std::string>());
    }
    if (size > 1 && !arr[1].is_null()) {
        obj.set_city(arr[1].get<std::string>());
    }
    if (size > 2 && !arr[2].is_null()) {
        obj.set_state(arr[2].get<std::string>());
    }
    if (size > 3 && !arr[3].is_null()) {
        obj.set_zip(arr[3].get<int32_t>());
    }
    return obj;
}

inline Address Address::from_json_string(const std::string& json) {
    return deserialize(nlohmann::json::parse(json));
}

} // namespace example

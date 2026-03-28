#include "example/address.h"


cJSON* example_address_serialize(const example_Address* msg) {
    if (!msg) return cJSON_CreateNull();
    cJSON* array = cJSON_CreateArray();
    cJSON_AddItemToArray(array, msg->street ? cJSON_CreateString(msg->street) : cJSON_CreateString(""));
    cJSON_AddItemToArray(array, msg->city ? cJSON_CreateString(msg->city) : cJSON_CreateString(""));
    cJSON_AddItemToArray(array, msg->state ? cJSON_CreateString(msg->state) : cJSON_CreateString(""));
    cJSON_AddItemToArray(array, cJSON_CreateNumber((double)msg->zip));
    return array;
}

char* example_address_to_json_string(const example_Address* msg) {
    cJSON* json = example_address_serialize(msg);
    char* str = cJSON_PrintUnformatted(json);
    cJSON_Delete(json);
    return str;
}

example_Address* example_address_deserialize(const cJSON* array) {
    if (!array || !cJSON_IsArray(array)) return NULL;
    example_Address* msg = (example_Address*)calloc(1, sizeof(example_Address));
    if (!msg) return NULL;
    int size = cJSON_GetArraySize(array);
    if (size > 0) {
        cJSON* item = cJSON_GetArrayItem(array, 0);
        if (item && !cJSON_IsNull(item)) {
            if (cJSON_IsString(item) && item->valuestring) {
                msg->street = strdup(item->valuestring);
            }
        }
    }
    if (size > 1) {
        cJSON* item = cJSON_GetArrayItem(array, 1);
        if (item && !cJSON_IsNull(item)) {
            if (cJSON_IsString(item) && item->valuestring) {
                msg->city = strdup(item->valuestring);
            }
        }
    }
    if (size > 2) {
        cJSON* item = cJSON_GetArrayItem(array, 2);
        if (item && !cJSON_IsNull(item)) {
            if (cJSON_IsString(item) && item->valuestring) {
                msg->state = strdup(item->valuestring);
            }
        }
    }
    if (size > 3) {
        cJSON* item = cJSON_GetArrayItem(array, 3);
        if (item && !cJSON_IsNull(item)) {
            msg->zip = (int32_t)cJSON_GetNumberValue(item);
        }
    }
    return msg;
}

example_Address* example_address_from_json_string(const char* json) {
    if (!json) return NULL;
    cJSON* parsed = cJSON_Parse(json);
    if (!parsed) return NULL;
    example_Address* msg = example_address_deserialize(parsed);
    cJSON_Delete(parsed);
    return msg;
}

void example_address_free(example_Address* msg) {
    if (!msg) return;
    free(msg->street);
    free(msg->city);
    free(msg->state);
    free(msg);
}

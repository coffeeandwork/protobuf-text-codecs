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

char* example_address_pack(const example_Address* msg) {
  cJSON* json = example_address_serialize(msg);
  char* str = cJSON_PrintUnformatted(json);
  cJSON_Delete(json);
  return str;
}

example_Address* example_address_deserialize(const cJSON* array) {
  if (!array || !cJSON_IsArray(array)) return NULL;
  example_Address* msg = (example_Address*)calloc(1, sizeof(example_Address));
  if (!msg) return NULL;
  cJSON* item = array->child;
  if (item) {
    if (!cJSON_IsNull(item)) {
      if (cJSON_IsString(item) && item->valuestring) {
        msg->street = jsonarray_strdup(item->valuestring);
      }
    }
    item = item->next;
  }
  if (item) {
    if (!cJSON_IsNull(item)) {
      if (cJSON_IsString(item) && item->valuestring) {
        msg->city = jsonarray_strdup(item->valuestring);
      }
    }
    item = item->next;
  }
  if (item) {
    if (!cJSON_IsNull(item)) {
      if (cJSON_IsString(item) && item->valuestring) {
        msg->state = jsonarray_strdup(item->valuestring);
      }
    }
    item = item->next;
  }
  if (item) {
    if (!cJSON_IsNull(item)) {
      msg->zip = (int32_t)cJSON_GetNumberValue(item);
    }
    item = item->next;
  }
  return msg;
}

example_Address* example_address_unpack(const char* json) {
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

#ifndef EXAMPLE_ADDRESS_H
#define EXAMPLE_ADDRESS_H

#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <math.h>
#include "cjson/cJSON.h"
#include "jsonarray/codec.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  char* street;
  char* city;
  char* state;
  int32_t zip;
} example_Address;

/* Serialization */
cJSON* example_address_serialize(const example_Address* msg);
char* example_address_pack(const example_Address* msg);

/* Deserialization */
example_Address* example_address_deserialize(const cJSON* array);
example_Address* example_address_unpack(const char* json);

/* Memory management */
void example_address_free(example_Address* msg);

#ifdef __cplusplus
}
#endif

#endif /* EXAMPLE_ADDRESS_H */

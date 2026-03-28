#ifndef JSONARRAY_CODEC_H
#define JSONARRAY_CODEC_H

#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include "cjson/cJSON.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Encode binary data to a base64 string.
 *
 * @param data  Pointer to the binary data to encode.
 * @param len   Length of the binary data in bytes.
 * @return      A newly allocated null-terminated base64 string, or NULL on failure.
 *              The caller must free() the returned string.
 */
char* jsonarray_base64_encode(const uint8_t* data, size_t len);

/**
 * Decode a base64 string to binary data.
 *
 * @param b64       Null-terminated base64 string to decode.
 * @param out_len   Pointer to a size_t that receives the length of the decoded data.
 * @return          A newly allocated buffer containing the decoded data, or NULL on failure.
 *                  The caller must free() the returned buffer.
 */
uint8_t* jsonarray_base64_decode(const char* b64, size_t* out_len);

/**
 * Safely duplicate a string. Returns NULL if the input is NULL.
 *
 * @param s  The string to duplicate.
 * @return   A newly allocated copy of s, or NULL. The caller must free() the result.
 */
char* jsonarray_strdup(const char* s);

/**
 * Get an array item from a cJSON array, with bounds checking.
 * Returns cJSON null node representation if out of bounds.
 *
 * @param array  The cJSON array.
 * @param index  The zero-based index.
 * @return       The cJSON item at the given index, or NULL if out of bounds.
 */
cJSON* jsonarray_array_get(const cJSON* array, int index);

/**
 * Check if a cJSON item is present and not null.
 *
 * @param item  The cJSON item to check.
 * @return      true if the item is non-NULL and not a cJSON null value.
 */
bool jsonarray_item_present(const cJSON* item);

/**
 * Read a number from a cJSON item, returning a default if the item is not a number.
 *
 * @param item          The cJSON item.
 * @param default_val   The default value to return if item is not a number.
 * @return              The numeric value, or default_val.
 */
double jsonarray_get_number(const cJSON* item, double default_val);

/**
 * Read a boolean from a cJSON item, returning a default if the item is not a boolean.
 *
 * @param item          The cJSON item.
 * @param default_val   The default value to return if item is not a boolean.
 * @return              The boolean value, or default_val.
 */
bool jsonarray_get_bool(const cJSON* item, bool default_val);

/**
 * Read a string from a cJSON item, returning NULL if the item is not a string.
 * The returned string is a strdup copy that the caller must free.
 *
 * @param item  The cJSON item.
 * @return      A newly allocated copy of the string, or NULL.
 */
char* jsonarray_get_string(const cJSON* item);

#ifdef __cplusplus
}
#endif

#endif /* JSONARRAY_CODEC_H */

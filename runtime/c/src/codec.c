#include "jsonarray/codec.h"

/* ========================================================================
 * Base64 encoding table and helpers
 * ======================================================================== */

static const char b64_encode_table[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static const unsigned char b64_decode_table[256] = {
    /* 0x00-0x0F */ 255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
    /* 0x10-0x1F */ 255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
    /* 0x20-0x2F */ 255,255,255,255,255,255,255,255,255,255,255, 62,255,255,255, 63,
    /* 0x30-0x3F */  52, 53, 54, 55, 56, 57, 58, 59, 60, 61,255,255,255,  0,255,255,
    /* 0x40-0x4F */ 255,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,
    /* 0x50-0x5F */  15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25,255,255,255,255,255,
    /* 0x60-0x6F */ 255, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
    /* 0x70-0x7F */  41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51,255,255,255,255,255,
    /* 0x80-0xFF - all invalid */
    255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
    255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
    255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
    255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
    255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
    255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
    255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
    255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
};

/* ========================================================================
 * Base64 encode
 * ======================================================================== */

char* jsonarray_base64_encode(const uint8_t* data, size_t len) {
    if (!data && len > 0) return NULL;

    /* Calculate output length: 4 chars per 3 bytes, rounded up, plus null terminator */
    size_t out_len = 4 * ((len + 2) / 3);
    char* out = (char*)malloc(out_len + 1);
    if (!out) return NULL;

    size_t i = 0;
    size_t j = 0;

    while (i + 2 < len) {
        uint32_t triple = ((uint32_t)data[i] << 16)
                        | ((uint32_t)data[i + 1] << 8)
                        | (uint32_t)data[i + 2];
        out[j++] = b64_encode_table[(triple >> 18) & 0x3F];
        out[j++] = b64_encode_table[(triple >> 12) & 0x3F];
        out[j++] = b64_encode_table[(triple >> 6) & 0x3F];
        out[j++] = b64_encode_table[triple & 0x3F];
        i += 3;
    }

    /* Handle remaining 1 or 2 bytes */
    if (i < len) {
        uint32_t triple = (uint32_t)data[i] << 16;
        if (i + 1 < len) {
            triple |= (uint32_t)data[i + 1] << 8;
        }
        out[j++] = b64_encode_table[(triple >> 18) & 0x3F];
        out[j++] = b64_encode_table[(triple >> 12) & 0x3F];
        if (i + 1 < len) {
            out[j++] = b64_encode_table[(triple >> 6) & 0x3F];
        } else {
            out[j++] = '=';
        }
        out[j++] = '=';
    }

    out[j] = '\0';
    return out;
}

/* ========================================================================
 * Base64 decode
 * ======================================================================== */

uint8_t* jsonarray_base64_decode(const char* b64, size_t* out_len) {
    if (!b64 || !out_len) return NULL;

    size_t input_len = strlen(b64);
    if (input_len == 0) {
        *out_len = 0;
        uint8_t* empty = (uint8_t*)malloc(1);
        if (empty) empty[0] = 0;
        return empty;
    }

    /* Validate input length is a multiple of 4 (VULN-008) */
    if (input_len % 4 != 0) {
        *out_len = 0;
        return NULL;
    }

    /* Strip trailing padding to determine actual data length */
    size_t padding = 0;
    if (input_len > 0 && b64[input_len - 1] == '=') padding++;
    if (input_len > 1 && b64[input_len - 2] == '=') padding++;

    size_t decoded_len = (input_len / 4) * 3 - padding;
    uint8_t* out = (uint8_t*)malloc(decoded_len + 1);
    if (!out) return NULL;

    size_t i = 0;
    size_t j = 0;

    while (i + 3 < input_len) {
        unsigned char a = b64_decode_table[(unsigned char)b64[i]];
        unsigned char b = b64_decode_table[(unsigned char)b64[i + 1]];
        unsigned char c = b64_decode_table[(unsigned char)b64[i + 2]];
        unsigned char d = b64_decode_table[(unsigned char)b64[i + 3]];

        if (a == 255 || b == 255) {
            /* Invalid character - stop decoding */
            break;
        }

        uint32_t triple = ((uint32_t)a << 18) | ((uint32_t)b << 12);
        if (c != 255) triple |= ((uint32_t)c << 6);
        if (d != 255) triple |= (uint32_t)d;

        if (j < decoded_len) out[j++] = (uint8_t)((triple >> 16) & 0xFF);
        if (j < decoded_len) out[j++] = (uint8_t)((triple >> 8) & 0xFF);
        if (j < decoded_len) out[j++] = (uint8_t)(triple & 0xFF);

        i += 4;
    }

    *out_len = j;
    out[j] = 0; /* null terminate for safety */
    return out;
}

/* ========================================================================
 * String utilities
 * ======================================================================== */

char* jsonarray_strdup(const char* s) {
    if (!s) return NULL;
    size_t len = strlen(s);
    char* copy = (char*)malloc(len + 1);
    if (!copy) return NULL;
    memcpy(copy, s, len + 1);
    return copy;
}

/* ========================================================================
 * cJSON utilities
 * ======================================================================== */

cJSON* jsonarray_array_get(const cJSON* array, int index) {
    if (!array || !cJSON_IsArray(array)) return NULL;
    int size = cJSON_GetArraySize(array);
    if (index < 0 || index >= size) return NULL;
    return cJSON_GetArrayItem(array, index);
}

bool jsonarray_item_present(const cJSON* item) {
    return item != NULL && !cJSON_IsNull(item);
}

double jsonarray_get_number(const cJSON* item, double default_val) {
    if (!item || !cJSON_IsNumber(item)) return default_val;
    return cJSON_GetNumberValue(item);
}

bool jsonarray_get_bool(const cJSON* item, bool default_val) {
    if (!item) return default_val;
    if (cJSON_IsTrue(item)) return true;
    if (cJSON_IsFalse(item)) return false;
    return default_val;
}

char* jsonarray_get_string(const cJSON* item) {
    if (!item || !cJSON_IsString(item) || !item->valuestring) return NULL;
    return jsonarray_strdup(item->valuestring);
}

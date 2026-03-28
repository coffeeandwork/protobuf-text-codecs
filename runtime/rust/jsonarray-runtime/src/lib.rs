//! Runtime support library for protoc-gen-jsonarray generated Rust code.
//!
//! Provides base64 encoding/decoding helpers and common traits used by
//! the generated serialization/deserialization code.

use base64::{Engine as _, engine::general_purpose};
use serde_json::Value;

/// Trait for types that can be serialized to a positional JSON array.
pub trait JsonArraySerialize {
    /// Serialize this message to a serde_json::Value (an array).
    fn serialize(&self) -> Value;

    /// Serialize this message to a JSON string.
    fn to_json_string(&self) -> String {
        serde_json::to_string(&self.serialize()).unwrap()
    }

    /// Serialize this message to JSON bytes.
    fn to_json_bytes(&self) -> Vec<u8> {
        self.to_json_string().into_bytes()
    }
}

/// Trait for types that can be deserialized from a positional JSON array.
pub trait JsonArrayDeserialize: Sized {
    /// Deserialize from a serde_json::Value (expected to be an array).
    fn deserialize(arr: &Value) -> Result<Self, String>;

    /// Deserialize from a JSON string.
    fn from_json_string(json: &str) -> Result<Self, String> {
        let value: Value = serde_json::from_str(json).map_err(|e| e.to_string())?;
        Self::deserialize(&value)
    }

    /// Deserialize from JSON bytes.
    fn from_json_bytes(bytes: &[u8]) -> Result<Self, String> {
        let json = std::str::from_utf8(bytes).map_err(|e| e.to_string())?;
        Self::from_json_string(json)
    }
}

/// Encode bytes to a base64 string using the standard alphabet.
pub fn encode_bytes(data: &[u8]) -> String {
    general_purpose::STANDARD.encode(data)
}

/// Decode a base64 string to bytes using the standard alphabet.
/// Returns an empty Vec on decode failure.
pub fn decode_bytes(encoded: &str) -> Vec<u8> {
    general_purpose::STANDARD.decode(encoded).unwrap_or_default()
}

/// Safely extract an i32 from a JSON Value, returning 0 if not a number.
pub fn value_as_i32(v: &Value) -> i32 {
    v.as_i64().unwrap_or(0) as i32
}

/// Safely extract a u32 from a JSON Value, returning 0 if not a number.
pub fn value_as_u32(v: &Value) -> u32 {
    v.as_u64().unwrap_or(0) as u32
}

/// Safely extract an i64 from a JSON Value, returning 0 if not a number.
pub fn value_as_i64(v: &Value) -> i64 {
    v.as_i64().unwrap_or(0)
}

/// Safely extract a u64 from a JSON Value, returning 0 if not a number.
pub fn value_as_u64(v: &Value) -> u64 {
    v.as_u64().unwrap_or(0)
}

/// Safely extract an f32 from a JSON Value, returning 0.0 if not a number.
pub fn value_as_f32(v: &Value) -> f32 {
    v.as_f64().unwrap_or(0.0) as f32
}

/// Safely extract an f64 from a JSON Value, returning 0.0 if not a number.
pub fn value_as_f64(v: &Value) -> f64 {
    v.as_f64().unwrap_or(0.0)
}

/// Safely extract a bool from a JSON Value, returning false if not a boolean.
pub fn value_as_bool(v: &Value) -> bool {
    v.as_bool().unwrap_or(false)
}

/// Safely extract a String from a JSON Value, returning empty string if not a string.
pub fn value_as_string(v: &Value) -> String {
    v.as_str().unwrap_or("").to_string()
}

/// Safely extract bytes (base64-decoded) from a JSON Value.
pub fn value_as_bytes(v: &Value) -> Vec<u8> {
    decode_bytes(v.as_str().unwrap_or(""))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn test_encode_decode_bytes() {
        let original = b"hello world";
        let encoded = encode_bytes(original);
        let decoded = decode_bytes(&encoded);
        assert_eq!(original.to_vec(), decoded);
    }

    #[test]
    fn test_decode_invalid_base64() {
        let result = decode_bytes("not valid base64!!!");
        assert!(result.is_empty());
    }

    #[test]
    fn test_value_as_i32() {
        assert_eq!(value_as_i32(&json!(42)), 42);
        assert_eq!(value_as_i32(&json!("not a number")), 0);
        assert_eq!(value_as_i32(&Value::Null), 0);
    }

    #[test]
    fn test_value_as_u64() {
        assert_eq!(value_as_u64(&json!(100)), 100);
        assert_eq!(value_as_u64(&Value::Null), 0);
    }

    #[test]
    fn test_value_as_f64() {
        assert_eq!(value_as_f64(&json!(3.14)), 3.14);
        assert_eq!(value_as_f64(&Value::Null), 0.0);
    }

    #[test]
    fn test_value_as_bool() {
        assert_eq!(value_as_bool(&json!(true)), true);
        assert_eq!(value_as_bool(&json!(false)), false);
        assert_eq!(value_as_bool(&Value::Null), false);
    }

    #[test]
    fn test_value_as_string() {
        assert_eq!(value_as_string(&json!("hello")), "hello");
        assert_eq!(value_as_string(&Value::Null), "");
    }

    #[test]
    fn test_value_as_bytes() {
        let encoded = encode_bytes(b"test data");
        let result = value_as_bytes(&json!(encoded));
        assert_eq!(result, b"test data");
    }
}

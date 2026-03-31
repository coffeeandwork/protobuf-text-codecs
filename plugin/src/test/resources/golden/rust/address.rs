use serde_json::{Value, json};

#[derive(Debug, Clone, Default)]
pub struct Address {
    pub street: String,
    pub city: String,
    pub state: String,
    pub zip: i32,
}

impl Address {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn street(&self) -> &str {
        &self.street
    }

    pub fn set_street(&mut self, value: String) {
        self.street = value;
    }

    pub fn city(&self) -> &str {
        &self.city
    }

    pub fn set_city(&mut self, value: String) {
        self.city = value;
    }

    pub fn state(&self) -> &str {
        &self.state
    }

    pub fn set_state(&mut self, value: String) {
        self.state = value;
    }

    pub fn zip(&self) -> i32 {
        self.zip
    }

    pub fn set_zip(&mut self, value: i32) {
        self.zip = value;
    }

    pub fn serialize(&self) -> Value {
        let mut arr: Vec<Value> = Vec::new();
        arr.push(json!(self.street));
        arr.push(json!(self.city));
        arr.push(json!(self.state));
        arr.push(json!(self.zip));
        Value::Array(arr)
    }

    pub fn to_json_string(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string(&self.serialize())
    }

    pub fn to_json_bytes(&self) -> Result<Vec<u8>, serde_json::Error> {
        Ok(self.to_json_string()?.into_bytes())
    }

    pub fn deserialize(arr: &Value) -> Result<Self, String> {
        let arr = arr.as_array().ok_or_else(|| "expected JSON array".to_string())?;
        let size = arr.len();
        let mut obj = Address::default();
        if size > 0 && !arr[0].is_null() {
            obj.street = arr[0].as_str().unwrap_or("").to_string();
        }
        if size > 1 && !arr[1].is_null() {
            obj.city = arr[1].as_str().unwrap_or("").to_string();
        }
        if size > 2 && !arr[2].is_null() {
            obj.state = arr[2].as_str().unwrap_or("").to_string();
        }
        if size > 3 && !arr[3].is_null() {
            obj.zip = arr[3].as_i64().unwrap_or(0) as i32;
        }
        Ok(obj)
    }

    pub fn from_json_string(json: &str) -> Result<Self, String> {
        let value: Value = serde_json::from_str(json).map_err(|e| e.to_string())?;
        Self::deserialize(&value)
    }

    pub fn from_json_bytes(bytes: &[u8]) -> Result<Self, String> {
        let json = std::str::from_utf8(bytes).map_err(|e| e.to_string())?;
        Self::from_json_string(json)
    }
}

impl std::fmt::Display for Address {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "Address{{street: {:?}, city: {:?}, state: {:?}, zip: {:?}}}", self.street, self.city, self.state, self.zip)
    }
}

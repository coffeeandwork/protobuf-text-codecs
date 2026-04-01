package example

import (
	"encoding/json"
	"fmt"
)

type Address struct {
	Street string
	City string
	State string
	Zip int32
}

func (m *Address) Serialize() []any {
	arr := make([]any, 4)
	arr[0] = m.Street
	arr[1] = m.City
	arr[2] = m.State
	arr[3] = m.Zip
	return arr
}

func (m *Address) Marshal() ([]byte, error) {
	arr := m.Serialize()
	data, err := json.Marshal(arr)
	if err != nil {
		return nil, err
	}
	return data, nil
}

func DeserializeAddress(arr []any) (*Address, error) {
	obj := &Address{}
	size := len(arr)
	if size > 0 && arr[0] != nil {
		if v, ok := arr[0].(string); ok {
			obj.Street = v
		}
	}
	if size > 1 && arr[1] != nil {
		if v, ok := arr[1].(string); ok {
			obj.City = v
		}
	}
	if size > 2 && arr[2] != nil {
		if v, ok := arr[2].(string); ok {
			obj.State = v
		}
	}
	if size > 3 && arr[3] != nil {
		if v, ok := arr[3].(float64); ok {
			obj.Zip = int32(v)
		}
	}
	return obj, nil
}

func (m *Address) Unmarshal(data []byte) error {
	var arr []any
	if err := json.Unmarshal(data, &arr); err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %w", err)
	}
	parsed, err := DeserializeAddress(arr)
	if err != nil {
		return err
	}
	*m = *parsed
	return nil
}

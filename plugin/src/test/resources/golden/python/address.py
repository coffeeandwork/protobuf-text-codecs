import json
import base64
import math


class Address:
    """Generated from proto message .example.Address."""

    def __init__(self):
        self._street = ""
        self._city = ""
        self._state = ""
        self._zip = 0

    @property
    def street(self):
        return self._street

    @street.setter
    def street(self, value):
        self._street = value

    @property
    def city(self):
        return self._city

    @city.setter
    def city(self, value):
        self._city = value

    @property
    def state(self):
        return self._state

    @state.setter
    def state(self, value):
        self._state = value

    @property
    def zip(self):
        return self._zip

    @zip.setter
    def zip(self, value):
        self._zip = value

    def serialize(self):
        """Serialize this message to a positional list."""
        result = []
        result.append(self._street)
        result.append(self._city)
        result.append(self._state)
        result.append(self._zip)
        return result

    def to_json_string(self):
        """Serialize this message to a JSON string."""
        return json.dumps(self.serialize())

    def to_json_bytes(self):
        """Serialize this message to JSON-encoded bytes."""
        return self.to_json_string().encode("utf-8")

    @classmethod
    def deserialize(cls, data):
        """Deserialize a positional list into a Address instance."""
        obj = cls()
        size = len(data)
        if size > 0 and data[0] is not None:
            obj._street = str(data[0])
        if size > 1 and data[1] is not None:
            obj._city = str(data[1])
        if size > 2 and data[2] is not None:
            obj._state = str(data[2])
        if size > 3 and data[3] is not None:
            obj._zip = int(data[3])
        return obj

    @classmethod
    def from_json_string(cls, json_str):
        """Deserialize from a JSON string."""
        return cls.deserialize(json.loads(json_str))

    @classmethod
    def from_json_bytes(cls, data):
        """Deserialize from JSON-encoded bytes."""
        return cls.from_json_string(data.decode("utf-8"))

    def __repr__(self):
        return f"Address(street={self._street!r}, city={self._city!r}, state={self._state!r}, zip={self._zip!r})"

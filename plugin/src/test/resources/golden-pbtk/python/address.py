import base64
import urllib.parse


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

    def _append_pbtk_fields(self, parts):
        parts.append("!1s" + urllib.parse.quote(self._street, safe=""))
        parts.append("!2s" + urllib.parse.quote(self._city, safe=""))
        parts.append("!3s" + urllib.parse.quote(self._state, safe=""))
        parts.append("!4i" + str(self._zip))

    def _count_pbtk_fields(self):
        count = 0
        count += 1
        count += 1
        count += 1
        count += 1
        return count

    def SerializeToString(self):
        """Serialize this message to a pbtk URL-encoded string."""
        parts = []
        self._append_pbtk_fields(parts)
        return "".join(parts)

    @classmethod
    def _parse_pbtk_tokens(cls, tokens, field_count, offset):
        obj = cls()
        consumed = 0
        while consumed < field_count and offset[0] < len(tokens):
            token = tokens[offset[0]]
            num_end = 0
            while num_end < len(token) and token[num_end].isdigit():
                num_end += 1
            if num_end == 0 or num_end >= len(token):
                offset[0] += 1
                consumed += 1
                continue
            field_num = int(token[:num_end])
            type_char = token[num_end]
            value = token[num_end + 1:]
            if field_num == 1:
                obj._street = urllib.parse.unquote(value)
                offset[0] += 1
                consumed += 1
            elif field_num == 2:
                obj._city = urllib.parse.unquote(value)
                offset[0] += 1
                consumed += 1
            elif field_num == 3:
                obj._state = urllib.parse.unquote(value)
                offset[0] += 1
                consumed += 1
            elif field_num == 4:
                obj._zip = int(value)
                offset[0] += 1
                consumed += 1
            else:
                offset[0] += 1
                consumed += 1
        return obj

    @classmethod
    def ParseFromString(cls, input_str):
        """Deserialize a pbtk URL-encoded string into a Address instance."""
        if not input_str:
            return cls()
        tokens = cls._tokenize_pbtk(input_str)
        offset = [0]
        return cls._parse_pbtk_tokens(tokens, len(tokens), offset)

    @staticmethod
    def _tokenize_pbtk(input_str):
        tokens = []
        i = 1 if input_str and input_str[0] == "!" else 0
        while i < len(input_str):
            nxt = input_str.find("!", i)
            if nxt < 0:
                tokens.append(input_str[i:])
                break
            tokens.append(input_str[i:nxt])
            i = nxt + 1
        return tokens

    def __repr__(self):
        return f"Address(street={self._street!r}, city={self._city!r}, state={self._state!r}, zip={self._zip!r})"

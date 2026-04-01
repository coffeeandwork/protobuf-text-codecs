package example

import (
	"strconv"
	"strings"
	"net/url"
)

type Address struct {
	Street string
	City string
	State string
	Zip int32
}

func (m *Address) countPbtkFields() int {
	count := 0
	count++
	count++
	count++
	count++
	return count
}

func (m *Address) appendPbtkFields(sb *strings.Builder) {
	sb.WriteString("!1s")
	sb.WriteString(url.QueryEscape(m.Street))
	sb.WriteString("!2s")
	sb.WriteString(url.QueryEscape(m.City))
	sb.WriteString("!3s")
	sb.WriteString(url.QueryEscape(m.State))
	sb.WriteString("!4i")
	sb.WriteString(strconv.FormatInt(int64(m.Zip), 10))
}

func (m *Address) Marshal() ([]byte, error) {
	var sb strings.Builder
	m.appendPbtkFields(&sb)
	return []byte(sb.String()), nil
}

func parseAddressPbtkTokens(tokens []string, fieldCount int, offset *int) *Address {
	obj := &Address{}
	consumed := 0
	for consumed < fieldCount && *offset < len(tokens) {
		token := tokens[*offset]
		numEnd := 0
		for numEnd < len(token) && token[numEnd] >= '0' && token[numEnd] <= '9' {
			numEnd++
		}
		if numEnd == 0 || numEnd >= len(token) {
			*offset++
			consumed++
			continue
		}
		fieldNum, _ := strconv.Atoi(token[:numEnd])
		value := token[numEnd+1:]
		switch fieldNum {
			case 1:
				obj.Street, _ = url.QueryUnescape(value)
				*offset++
				consumed++
			case 2:
				obj.City, _ = url.QueryUnescape(value)
				*offset++
				consumed++
			case 3:
				obj.State, _ = url.QueryUnescape(value)
				*offset++
				consumed++
			case 4:
				tmpI64, _ := strconv.ParseInt(value, 10, 32)
				obj.Zip = int32(tmpI64)
				*offset++
				consumed++
			default:
				*offset++
				consumed++
		}
	}
	return obj
}

func (m *Address) Unmarshal(data []byte) error {
	input := string(data)
	if input == "" {
		return nil
	}
	tokens := pbtkTokenize(input)
	offset := 0
	parsed := parseAddressPbtkTokens(tokens, len(tokens), &offset)
	*m = *parsed
	return nil
}

func pbtkTokenize(input string) []string {
	var tokens []string
	i := 0
	if len(input) > 0 && input[0] == '!' {
		i = 1
	}
	for i < len(input) {
		next := strings.IndexByte(input[i:], '!')
		if next < 0 {
			tokens = append(tokens, input[i:])
			break
		}
		tokens = append(tokens, input[i:i+next])
		i = i + next + 1
	}
	return tokens
}

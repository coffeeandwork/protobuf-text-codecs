<?php

declare(strict_types=1);

namespace Example;

/**
 * Generated from proto message .example.Address.
 */
class Address
{
    public string $street = '';
    public string $city = '';
    public string $state = '';
    public int $zip = 0;

    public function __construct()
    {
    }

    public function getStreet(): string
    {
        return $this->street;
    }

    public function setStreet(string $value): self
    {
        $this->street = $value;
        return $this;
    }

    public function getCity(): string
    {
        return $this->city;
    }

    public function setCity(string $value): self
    {
        $this->city = $value;
        return $this;
    }

    public function getState(): string
    {
        return $this->state;
    }

    public function setState(string $value): self
    {
        $this->state = $value;
        return $this;
    }

    public function getZip(): int
    {
        return $this->zip;
    }

    public function setZip(int $value): self
    {
        $this->zip = $value;
        return $this;
    }

    /**
     * @param array<int, string> $parts
     */
    public function appendPbtkFields(array &$parts): void
    {
        $parts[] = '!1s' . rawurlencode($this->street);
        $parts[] = '!2s' . rawurlencode($this->city);
        $parts[] = '!3s' . rawurlencode($this->state);
        $parts[] = '!4i' . $this->zip;
    }

    public function countPbtkFields(): int
    {
        $count = 0;
        $count++;
        $count++;
        $count++;
        $count++;
        return $count;
    }

    /**
     * Serialize this message to a string.
     */
    public function serializeToString(): string
    {
        $parts = [];
        $this->appendPbtkFields($parts);
        return implode('', $parts);
    }

    /**
     * @param array<int, string> $tokens
     * @param int[] $offset
     */
    private static function parsePbtkTokens(array $tokens, int $fieldCount, array &$offset): self
    {
        $obj = new self();
        $consumed = 0;
        while ($consumed < $fieldCount && $offset[0] < count($tokens)) {
            $token = $tokens[$offset[0]];
            $numEnd = 0;
            $tokenLen = strlen($token);
            while ($numEnd < $tokenLen && ctype_digit($token[$numEnd])) {
                $numEnd++;
            }
            if ($numEnd === 0 || $numEnd >= $tokenLen) {
                $offset[0]++;
                $consumed++;
                continue;
            }
            $fieldNum = (int) substr($token, 0, $numEnd);
            $typeChar = $token[$numEnd];
            $value = substr($token, $numEnd + 1);
            if ($fieldNum === 1) {
                $obj->street = rawurldecode($value);
                $offset[0]++;
                $consumed++;
            } elseif ($fieldNum === 2) {
                $obj->city = rawurldecode($value);
                $offset[0]++;
                $consumed++;
            } elseif ($fieldNum === 3) {
                $obj->state = rawurldecode($value);
                $offset[0]++;
                $consumed++;
            } elseif ($fieldNum === 4) {
                $obj->zip = (int) $value;
                $offset[0]++;
                $consumed++;
            } else {
                $offset[0]++;
                $consumed++;
            }
        }
        return $obj;
    }

    /**
     * Deserialize from a string into a Address instance.
     */
    public static function mergeFromString(string $inputStr): self
    {
        if ($inputStr === '') {
            return new self();
        }
        $tokens = self::tokenizePbtk($inputStr);
        $offset = [0];
        return self::parsePbtkTokens($tokens, count($tokens), $offset);
    }

    /**
     * @return array<int, string>
     */
    private static function tokenizePbtk(string $inputStr): array
    {
        $tokens = [];
        $i = ($inputStr !== '' && $inputStr[0] === '!') ? 1 : 0;
        $len = strlen($inputStr);
        while ($i < $len) {
            $nxt = strpos($inputStr, '!', $i);
            if ($nxt === false) {
                $tokens[] = substr($inputStr, $i);
                break;
            }
            $tokens[] = substr($inputStr, $i, $nxt - $i);
            $i = $nxt + 1;
        }
        return $tokens;
    }

    public function __toString(): string
    {
        return 'Address(' . 'street=' . var_export($this->street, true) . ', ' . 'city=' . var_export($this->city, true) . ', ' . 'state=' . var_export($this->state, true) . ', ' . 'zip=' . var_export($this->zip, true) . ')';
    }
}

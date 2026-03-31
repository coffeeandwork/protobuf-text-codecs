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
     * Serialize this message to a positional array.
     *
     * @return array<int, mixed>
     */
    public function serialize(): array
    {
        $result = [];
        $result[] = $this->street;
        $result[] = $this->city;
        $result[] = $this->state;
        $result[] = $this->zip;
        return $result;
    }

    /**
     * Serialize this message to a JSON string.
     */
    public function toJsonString(): string
    {
        return json_encode($this->serialize(), JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
    }

    /**
     * Serialize this message to JSON-encoded bytes.
     */
    public function toJsonBytes(): string
    {
        return $this->toJsonString();
    }

    /**
     * Deserialize a positional array into a Address instance.
     *
     * @param array<int, mixed> $data
     * @return self
     */
    public static function deserialize(array $data): self
    {
        $obj = new self();
        $size = count($data);
        if ($size > 0 && $data[0] !== null) {
            $obj->street = (string) $data[0];
        }
        if ($size > 1 && $data[1] !== null) {
            $obj->city = (string) $data[1];
        }
        if ($size > 2 && $data[2] !== null) {
            $obj->state = (string) $data[2];
        }
        if ($size > 3 && $data[3] !== null) {
            $obj->zip = (int) $data[3];
        }
        return $obj;
    }

    /**
     * Deserialize from a JSON string.
     */
    public static function fromJsonString(string $jsonStr): self
    {
        $data = json_decode($jsonStr, true, 512, JSON_THROW_ON_ERROR);
        return self::deserialize($data);
    }

    /**
     * Deserialize from JSON-encoded bytes.
     */
    public static function fromJsonBytes(string $data): self
    {
        return self::fromJsonString($data);
    }

    public function __toString(): string
    {
        return 'Address(' . 'street=' . var_export($this->street, true) . ', ' . 'city=' . var_export($this->city, true) . ', ' . 'state=' . var_export($this->state, true) . ', ' . 'zip=' . var_export($this->zip, true) . ')';
    }
}

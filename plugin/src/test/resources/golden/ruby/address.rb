# frozen_string_literal: true

require 'json'
require 'base64'

module Example

  class Address
    # Generated from proto message .example.Address.

    attr_accessor :street
    attr_accessor :city
    attr_accessor :state
    attr_accessor :zip

    def initialize
      @street = ""
      @city = ""
      @state = ""
      @zip = 0
    end

    def serialize
      result = []
      result << @street
      result << @city
      result << @state
      result << @zip
      result
    end

    def self.encode(instance)
      JSON.generate(instance.serialize)
    end

    def self.deserialize(data)
      obj = new
      size = data.length
      if size > 0 && !data[0].nil?
        obj.street = data[0].to_s
      end
      if size > 1 && !data[1].nil?
        obj.city = data[1].to_s
      end
      if size > 2 && !data[2].nil?
        obj.state = data[2].to_s
      end
      if size > 3 && !data[3].nil?
        obj.zip = data[3].to_i
      end
      obj
    end

    def self.decode(data)
      deserialize(JSON.parse(data))
    end

    def inspect
      "Address(street=#{@street.inspect}, city=#{@city.inspect}, state=#{@state.inspect}, zip=#{@zip.inspect})"
    end
  end
end

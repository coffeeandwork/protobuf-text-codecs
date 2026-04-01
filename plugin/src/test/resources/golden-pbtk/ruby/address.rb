# frozen_string_literal: true

require 'base64'
require 'cgi'

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

    def _append_pbtk_fields(parts)
      parts << "!1s" + CGI.escape(@street)
      parts << "!2s" + CGI.escape(@city)
      parts << "!3s" + CGI.escape(@state)
      parts << "!4i" + @zip.to_s
    end

    def _count_pbtk_fields
      count = 0
      count += 1
      count += 1
      count += 1
      count += 1
      count
    end

    def self.encode(instance)
      parts = []
      instance._append_pbtk_fields(parts)
      parts.join
    end

    def self._parse_pbtk_tokens(tokens, field_count, offset)
      obj = new
      consumed = 0
      while consumed < field_count && offset[0] < tokens.length
        token = tokens[offset[0]]
        num_end = 0
        num_end += 1 while num_end < token.length && token[num_end] =~ /\d/
        if num_end == 0 || num_end >= token.length
          offset[0] += 1
          consumed += 1
          next
        end
        field_num = token[0...num_end].to_i
        type_char = token[num_end]
        value = token[(num_end + 1)..-1]
        if field_num == 1
          obj.instance_variable_set(:@street, CGI.unescape(value))
          offset[0] += 1
          consumed += 1
        elsif field_num == 2
          obj.instance_variable_set(:@city, CGI.unescape(value))
          offset[0] += 1
          consumed += 1
        elsif field_num == 3
          obj.instance_variable_set(:@state, CGI.unescape(value))
          offset[0] += 1
          consumed += 1
        elsif field_num == 4
          obj.instance_variable_set(:@zip, value.to_i)
          offset[0] += 1
          consumed += 1
        else
          offset[0] += 1
          consumed += 1
        end
      end
      obj
    end

    def self.decode(data)
      return new if data.nil? || data.empty?
      tokens = _tokenize_pbtk(data)
      offset = [0]
      _parse_pbtk_tokens(tokens, tokens.length, offset)
    end

    def self._tokenize_pbtk(input_str)
      tokens = []
      i = input_str[0] == '!' ? 1 : 0
      while i < input_str.length
        nxt = input_str.index('!', i)
        if nxt.nil?
          tokens << input_str[i..-1]
          break
        end
        tokens << input_str[i...nxt]
        i = nxt + 1
      end
      tokens
    end

    def inspect
      "Address(street=#{@street.inspect}, city=#{@city.inspect}, state=#{@state.inspect}, zip=#{@zip.inspect})"
    end
  end
end

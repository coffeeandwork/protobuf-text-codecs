import Foundation

public struct Address {
    public var street: String = ""
    public var city: String = ""
    public var state: String = ""
    public var zip: Int32 = 0

    public init() {
    }

    func countPbtkFields() -> Int {
        var count = 0
        count += 1
        count += 1
        count += 1
        count += 1
        return count
    }

    func appendPbtkFields(_ sb: inout String) {
        sb += "!1s"
        sb += street.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? street
        sb += "!2s"
        sb += city.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? city
        sb += "!3s"
        sb += state.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? state
        sb += "!4i"
        sb += String(zip)
    }

    public func serializedData() throws -> Data {
        var sb = ""
        appendPbtkFields(&sb)
        return Data(sb.utf8)
    }

    static func parsePbtkTokens(_ tokens: [String], fieldCount: Int, offset: inout Int) -> Address {
        var obj = Address()
        var consumed = 0
        while consumed < fieldCount && offset < tokens.count {
            let token = tokens[offset]
            var numEnd = token.startIndex
            while numEnd < token.endIndex && token[numEnd].isNumber {
                numEnd = token.index(after: numEnd)
            }
            if numEnd == token.startIndex || numEnd >= token.endIndex {
                offset += 1
                consumed += 1
                continue
            }
            guard let fieldNum = Int(token[token.startIndex..<numEnd]) else {
                offset += 1
                consumed += 1
                continue
            }
            let valueStart = token.index(after: numEnd)
            let value = String(token[valueStart..<token.endIndex])
            switch fieldNum {
            case 1:
                obj.street = value.removingPercentEncoding ?? value
                offset += 1
                consumed += 1
            case 2:
                obj.city = value.removingPercentEncoding ?? value
                offset += 1
                consumed += 1
            case 3:
                obj.state = value.removingPercentEncoding ?? value
                offset += 1
                consumed += 1
            case 4:
                obj.zip = Int32(value) ?? 0
                offset += 1
                consumed += 1
            default:
                offset += 1
                consumed += 1
            }
        }
        return obj
    }

    public init(serializedData data: Data) throws {
        guard let input = String(data: data, encoding: .utf8) else {
            throw NSError(domain: "Address", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid UTF-8 data"])
        }
        if input.isEmpty {
            self = Address()
            return
        }
        let tokens = Address.pbtkTokenize(input)
        var offset = 0
        self = Address.parsePbtkTokens(tokens, fieldCount: tokens.count, offset: &offset)
    }
}

private func pbtkTokenize(_ input: String) -> [String] {
    var tokens: [String] = []
    var s = input
    if s.hasPrefix("!") {
        s = String(s.dropFirst())
    }
    while !s.isEmpty {
        if let range = s.range(of: "!") {
            tokens.append(String(s[s.startIndex..<range.lowerBound]))
            s = String(s[range.upperBound...])
        }
        else {
            tokens.append(s)
            break
        }
    }
    return tokens
}

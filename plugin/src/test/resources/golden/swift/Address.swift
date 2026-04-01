import Foundation

public struct Address {
    public var street: String = ""
    public var city: String = ""
    public var state: String = ""
    public var zip: Int32 = 0

    public init() {
    }

    public func serialize() -> [Any] {
        var arr: [Any] = Array(repeating: NSNull(), count: 4)
        arr[0] = street
        arr[1] = city
        arr[2] = state
        arr[3] = zip
        return arr
    }

    public func serializedData() throws -> Data {
        let arr = serialize()
        return try JSONSerialization.data(withJSONObject: arr, options: [])
    }

    public static func deserialize(_ arr: [Any?]) -> Address {
        var obj = Address()
        let size = arr.count
        if size > 0, let elem = arr[0] {
            if let v = elem as? String {
                obj.street = v
            }
        }
        if size > 1, let elem = arr[1] {
            if let v = elem as? String {
                obj.city = v
            }
        }
        if size > 2, let elem = arr[2] {
            if let v = elem as? String {
                obj.state = v
            }
        }
        if size > 3, let elem = arr[3] {
            if let v = elem as? Double {
                obj.zip = Int32(v)
            }
        }
        return obj
    }

    public init(serializedData data: Data) throws {
        guard let arr = try JSONSerialization.jsonObject(with: data, options: []) as? [Any] else {
            throw NSError(domain: "Address", code: -1, userInfo: [NSLocalizedDescriptionKey: "Expected JSON array"])
        }
        let parsed = Address.deserialize(arr.map { $0 is NSNull ? nil : $0 })
        self = parsed
    }
}

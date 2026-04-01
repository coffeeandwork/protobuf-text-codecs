const std = @import("std");
const json = std.json;

pub const Address = struct {
    street: []const u8 = "",
    city: []const u8 = "",
    state: []const u8 = "",
    zip: i32 = 0,

    pub fn serializeToValue(self: *const Address, allocator: std.mem.Allocator) !json.Value {
        var arr = try allocator.alloc(json.Value, 4);
        arr[0] = json.Value{ .string = self.street };
        arr[1] = json.Value{ .string = self.city };
        arr[2] = json.Value{ .string = self.state };
        arr[3] = json.Value{ .integer = @as(i64, @intCast(self.zip)) };
        return json.Value{ .array = json.Array.fromOwnedSlice(allocator, arr) };
    }

    pub fn serialize(self: *const Address, allocator: std.mem.Allocator) ![]u8 {
        const value = try self.serializeToValue(allocator);
        var string = std.ArrayList(u8).init(allocator);
        defer string.deinit();
        try value.jsonStringify(.{}, string.writer());
        return try string.toOwnedSlice();
    }

    pub fn deserializeFromValue(value: json.Value, allocator: std.mem.Allocator) !Address {
        const arr = value.array.items;
        const size = arr.len;
        var obj: Address = undefined;
        obj.street = "";
        obj.city = "";
        obj.state = "";
        obj.zip = 0;

        if (size > 0 and arr[0] != .null) {
            obj.street = try allocator.dupe(u8, arr[0].string);
        }
        if (size > 1 and arr[1] != .null) {
            obj.city = try allocator.dupe(u8, arr[1].string);
        }
        if (size > 2 and arr[2] != .null) {
            obj.state = try allocator.dupe(u8, arr[2].string);
        }
        if (size > 3 and arr[3] != .null) {
            obj.zip = @as(i32, @intCast(arr[3].integer));
        }
        return obj;
    }

    pub fn deserialize(data: []const u8, allocator: std.mem.Allocator) !Address {
        const parsed = try std.json.parseFromSlice(json.Value, allocator, data, .{});
        defer parsed.deinit();
        return try deserializeFromValue(parsed.value, allocator);
    }

    pub fn deinit(self: *Address, allocator: std.mem.Allocator) void {
        if (self.street.len > 0) {
            allocator.free(self.street);
        }
        if (self.city.len > 0) {
            allocator.free(self.city);
        }
        if (self.state.len > 0) {
            allocator.free(self.state);
        }
    }
};
